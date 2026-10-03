#!/usr/bin/env python3
"""Package checked APKs and verify their identity before publication. No build or upload."""

import argparse
import hashlib
import json
import re
import shutil
from pathlib import Path


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_versions(path, stable=False):
    properties = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            properties[key.strip()] = value.strip()
    versions = {}
    for prefix, name_key, code_key in (
        ("SLEEPMANAGER", "versionName", "versionCode"),
        ("SLEEPMANAGER_HELPER", "helperVersionName", "helperVersionCode"),
    ):
        name = properties[prefix + "_VERSION_NAME"]
        code = properties[prefix + "_VERSION_CODE"]
        require(re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:\.[0-9]+)?(?:-[A-Za-z0-9][A-Za-z0-9.-]*)?", name),
                f"Invalid version name: {name!r}")
        require(re.fullmatch(r"[0-9]+", code) and int(code) > 0,
                f"Invalid version code: {code!r}")
        require(not stable or "-" not in name, f"Stable publication refuses prerelease: {name}")
        versions[name_key] = name
        versions[code_key] = int(code)
    return versions


def apk_names(versions):
    return (f"SleepManager-{versions['versionName']}.apk",
            f"SleepManager-Helper-{versions['helperVersionName']}.apk")


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path, payload):
    path.write_text(json.dumps(payload, sort_keys=True, separators=(",", ":")) + "\n",
                    encoding="utf-8")


def update_payload(directory, versions, repository):
    main, helper = apk_names(versions)
    base = f"https://github.com/{repository}/releases"
    tag = f"v{versions['versionName']}"
    return {
        **versions,
        "releaseUrl": f"{base}/tag/{tag}",
        "apkUrl": f"{base}/download/{tag}/{main}",
        "sha256": sha256(directory / main),
        "helperApkUrl": f"{base}/download/{tag}/{helper}",
        "helperSha256": sha256(directory / helper),
    }


def identity(versions, commit, repository, variant):
    require(re.fullmatch(r"[0-9a-f]{40}", commit), "Expected a full source commit SHA")
    require(re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository), "Invalid repository")
    require(variant in ("release", "debug"), "Invalid build variant")
    return {"schemaVersion": 1, "sourceCommit": commit, "repository": repository,
            "variant": variant, "versions": versions}


def prepare(directory, properties, commit, repository, variant, main_apk, helper_apk):
    versions = read_versions(properties)
    manifest = identity(versions, commit, repository, variant)
    require(main_apk.is_file() and helper_apk.is_file(), "Both built APKs are required")
    require(not directory.exists() or not any(directory.iterdir()), "Bundle directory must be empty")
    directory.mkdir(parents=True, exist_ok=True)
    for source, name in zip((main_apk, helper_apk), apk_names(versions)):
        require(source.stat().st_size > 0, f"Empty APK: {source}")
        shutil.copyfile(source, directory / name)
    write_json(directory / "update.json", update_payload(directory, versions, repository))
    manifest["files"] = {name: sha256(directory / name)
                         for name in (*apk_names(versions), "update.json")}
    write_json(directory / "build-manifest.json", manifest)


def verify(directory, properties, commit, repository, variant, stable=False):
    require(not stable or variant == "release", "Stable publication requires release APKs")
    versions = read_versions(properties, stable=stable)
    expected = identity(versions, commit, repository, variant)
    manifest = json.loads((directory / "build-manifest.json").read_text(encoding="utf-8"))
    require(isinstance(manifest, dict), "Invalid build manifest")
    require(set(manifest) == set(expected) | {"files"}, "Unexpected build manifest fields")
    for key, value in expected.items():
        require(manifest[key] == value, f"Artifact {key} does not match this build")
    names = {*apk_names(versions), "update.json"}
    require(isinstance(manifest["files"], dict) and set(manifest["files"]) == names,
            "Artifact file list does not match expected APKs and update manifest")
    require({path.name for path in directory.iterdir()} == names | {"build-manifest.json"},
            "Artifact contains missing or unexpected files")
    for name in names:
        path = directory / name
        require(path.is_file() and not path.is_symlink() and path.stat().st_size > 0,
                f"Missing, empty or unsafe artifact file: {name}")
        require(sha256(path) == manifest["files"][name], f"SHA-256 mismatch: {name}")
    update = json.loads((directory / "update.json").read_text(encoding="utf-8"))
    require(update == update_payload(directory, versions, repository),
            "Update manifest does not describe the verified APKs")
    return versions


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("prepare", "verify"))
    parser.add_argument("--directory", type=Path, default=Path("dist"))
    parser.add_argument("--properties", type=Path, default=Path("gradle.properties"))
    parser.add_argument("--commit", required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--variant", choices=("release", "debug"), required=True)
    parser.add_argument("--main-apk", type=Path)
    parser.add_argument("--helper-apk", type=Path)
    parser.add_argument("--stable", action="store_true")
    parser.add_argument("--github-env", type=Path)
    args = parser.parse_args()
    try:
        if args.command == "prepare":
            require(args.main_apk is not None and args.helper_apk is not None,
                    "prepare requires --main-apk and --helper-apk")
            require(not args.stable and args.github_env is None,
                    "--stable and --github-env are verification-only options")
            prepare(args.directory, args.properties, args.commit, args.repository,
                    args.variant, args.main_apk, args.helper_apk)
        versions = verify(args.directory, args.properties, args.commit, args.repository,
                          args.variant, stable=args.stable)
        if args.github_env:
            values = {"VERSION_NAME": versions["versionName"],
                      "HELPER_VERSION_NAME": versions["helperVersionName"],
                      "RELEASE_TAG": f"v{versions['versionName']}",
                      "SOURCE_COMMIT": args.commit}
            with args.github_env.open("a", encoding="utf-8") as handle:
                for key, value in values.items():
                    handle.write(f"{key}={value}\n")
    except (ValueError, KeyError, OSError, TypeError) as error:
        parser.exit(1, f"Artifact verification failed: {error}\n")
    print(f"Verified {args.variant} APK bundle for {args.commit}")


if __name__ == "__main__":
    main()
