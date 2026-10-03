"""Release identity checks with byte fixtures; no signing keys or GitHub writes."""

import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import release_artifacts as artifacts


class ReleaseArtifactsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.properties = self.root / "gradle.properties"
        self.properties.write_text(
            "SLEEPMANAGER_VERSION_NAME=0.6.2\nSLEEPMANAGER_VERSION_CODE=546\n"
            "SLEEPMANAGER_HELPER_VERSION_NAME=1.1.1\n"
            "SLEEPMANAGER_HELPER_VERSION_CODE=1112\n", encoding="utf-8")
        self.main = self.root / "main.apk"
        self.helper = self.root / "helper.apk"
        self.main.write_bytes(b"main APK byte fixture")
        self.helper.write_bytes(b"helper APK byte fixture")
        self.bundle = self.root / "dist"
        self.commit = "a" * 40
        self.repo = "Baggio94/SleepManager"
        artifacts.prepare(self.bundle, self.properties, self.commit, self.repo,
                          "release", self.main, self.helper)

    def verify(self, **overrides):
        args = dict(directory=self.bundle, properties=self.properties, commit=self.commit,
                    repository=self.repo, variant="release", stable=True)
        args.update(overrides)
        return artifacts.verify(**args)

    def test_downloaded_bundle_preserves_bytes_and_updater_hashes(self):
        downloaded = self.root / "downloaded"
        shutil.copytree(self.bundle, downloaded)
        versions = self.verify(directory=downloaded)
        self.assertEqual(versions["versionCode"], 546)
        self.assertEqual((downloaded / "SleepManager-0.6.2.apk").read_bytes(), self.main.read_bytes())
        self.assertEqual((downloaded / "SleepManager-Helper-1.1.1.apk").read_bytes(), self.helper.read_bytes())
        update = json.loads((downloaded / "update.json").read_text())
        self.assertEqual(update["sha256"], artifacts.sha256(self.main))
        self.assertEqual(update["helperSha256"], artifacts.sha256(self.helper))

    def test_wrong_commit_or_repository_is_rejected(self):
        for overrides in ({"commit": "b" * 40}, {"repository": "Baggio94/SleepManager-Dev"}):
            with self.subTest(overrides=overrides), self.assertRaises(ValueError):
                self.verify(**overrides)

    def test_fork_subversions_preserve_paired_fork_update_urls(self):
        self.properties.write_text(self.properties.read_text().replace("0.6.2", "0.7.0.1")
                                   .replace("1.1.1", "1.1.2.1"))
        bundle = self.root / "fork"
        artifacts.prepare(bundle, self.properties, self.commit, "Darkaxt/SleepManager",
                          "release", self.main, self.helper)
        versions = self.verify(directory=bundle, repository="Darkaxt/SleepManager")
        self.assertEqual(versions["versionName"], "0.7.0.1")
        self.assertEqual(versions["helperVersionName"], "1.1.2.1")
        update = json.loads((bundle / "update.json").read_text())
        self.assertEqual(update["helperApkUrl"],
                         "https://github.com/Darkaxt/SleepManager/releases/download/"
                         "v0.7.0.1/SleepManager-Helper-1.1.2.1.apk")

    def test_tampered_main_helper_or_update_is_rejected(self):
        for name in ("SleepManager-0.6.2.apk", "SleepManager-Helper-1.1.1.apk", "update.json"):
            path = self.bundle / name
            original = path.read_bytes()
            path.write_bytes(original + b"changed")
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
                self.verify()
            path.write_bytes(original)

    def test_missing_apk_is_rejected(self):
        (self.bundle / "SleepManager-Helper-1.1.1.apk").unlink()
        with self.assertRaises(ValueError):
            self.verify()

    def test_extra_file_is_rejected(self):
        (self.bundle / "unverified.apk").write_bytes(b"extra")
        with self.assertRaises(ValueError):
            self.verify()

    def test_version_code_change_is_rejected(self):
        self.properties.write_text(self.properties.read_text().replace("=546", "=547"))
        with self.assertRaisesRegex(ValueError, "versions"):
            self.verify()

    def test_prerelease_main_or_helper_is_rejected_for_stable(self):
        original = self.properties.read_text()
        for name in ("0.6.2", "1.1.1"):
            self.properties.write_text(original.replace(name, name + "-dev1"))
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, "refuses prerelease"):
                self.verify()

    def test_debug_dry_run_cannot_be_promoted_to_release(self):
        debug = self.root / "debug"
        artifacts.prepare(debug, self.properties, self.commit, self.repo,
                          "debug", self.main, self.helper)
        self.verify(directory=debug, variant="debug", stable=False)
        with self.assertRaisesRegex(ValueError, "variant"):
            self.verify(directory=debug)
        with self.assertRaisesRegex(ValueError, "requires release"):
            self.verify(directory=debug, variant="debug")

    def test_update_url_mismatch_is_rejected_even_with_recomputed_checksum(self):
        path = self.bundle / "update.json"
        update = json.loads(path.read_text())
        update["apkUrl"] = "https://example.invalid/wrong.apk"
        artifacts.write_json(path, update)
        manifest_path = self.bundle / "build-manifest.json"
        manifest = json.loads(manifest_path.read_text())
        manifest["files"]["update.json"] = artifacts.sha256(path)
        artifacts.write_json(manifest_path, manifest)
        with self.assertRaisesRegex(ValueError, "does not describe"):
            self.verify()

    def test_invalid_version_or_code_cannot_enter_workflow_environment(self):
        original = self.properties.read_text()
        for value in (original.replace("0.6.2", "../../escape"),
                      original.replace("=546", "=0"), original.replace("=546", "=abc")):
            self.properties.write_text(value)
            with self.subTest(value=value), self.assertRaises(ValueError):
                artifacts.read_versions(self.properties)

    def test_cli_exports_metadata_only_after_successful_verification(self):
        output = self.root / "github-env"
        command = [sys.executable, str(Path(artifacts.__file__)), "verify",
                   "--directory", str(self.bundle), "--properties", str(self.properties),
                   "--commit", self.commit, "--repository", self.repo,
                   "--variant", "release", "--stable", "--github-env", str(output)]
        result = subprocess.run(command, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        environment = output.read_text()
        self.assertIn("RELEASE_TAG=v0.6.2\n", environment)
        (self.bundle / "SleepManager-0.6.2.apk").write_bytes(b"tampered")
        result = subprocess.run(command, capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(output.read_text(), environment)


if __name__ == "__main__":
    unittest.main()
