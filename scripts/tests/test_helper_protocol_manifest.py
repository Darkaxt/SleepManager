"""Guard AndroidManifest declarations against HelperProtocol drift."""

import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PROTOCOL = (
    ROOT
    / "shared/helper-protocol/src/main/java/com/med/sleepmanager/protocol/HelperProtocol.java"
)
APP_MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
HELPER_MANIFEST = ROOT / "helper/src/main/AndroidManifest.xml"
ANDROID_NAME = "{http://schemas.android.com/apk/res/android}name"
ANDROID_PERMISSION = "{http://schemas.android.com/apk/res/android}permission"


def protocol_constants():
    source = PROTOCOL.read_text(encoding="utf-8")
    matches = re.findall(
        r'public\s+static\s+final\s+String\s+(\w+)\s*=\s*"([^"]+)"\s*;',
        source,
        flags=re.MULTILINE,
    )
    return dict(matches)


class HelperProtocolManifestTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.contract = protocol_constants()
        cls.app = ET.parse(APP_MANIFEST).getroot()
        cls.helper = ET.parse(HELPER_MANIFEST).getroot()

    def test_protocol_parser_finds_required_contract_values(self):
        required = {
            "HELPER_PACKAGE",
            "PERMISSION",
            "PERMISSION_V2",
            "HELPER_COMMAND_SERVICE_V2_CLASS",
            "ACTION_SLEEP",
            "ACTION_WAKE",
            "ACTION_RESTORE",
            "ACTION_QUERY",
            "ACTION_FORGET_STATE",
            "ACTION_SET_TEMP_WIFI",
        }
        self.assertTrue(required <= self.contract.keys())

    def test_main_manifest_permission_and_helper_query_match_protocol(self):
        declared_permissions = {
            node.get(ANDROID_NAME)
            for node in self.app.findall("permission")
        }
        used_permissions = {
            node.get(ANDROID_NAME)
            for node in self.app.findall("uses-permission")
        }
        queried_packages = {
            node.get(ANDROID_NAME)
            for node in self.app.findall("./queries/package")
        }

        self.assertIn(self.contract["PERMISSION"], declared_permissions)
        self.assertIn(self.contract["PERMISSION"], used_permissions)
        self.assertIn(self.contract["PERMISSION_V2"], used_permissions)
        self.assertIn(self.contract["HELPER_PACKAGE"], queried_packages)


    def test_helper_v2_command_service_is_signature_protected(self):
        services = {
            node.get(ANDROID_NAME): node
            for node in self.helper.findall("./application/service")
        }
        service = services[".HelperCommandServiceV2"]
        self.assertEqual(
            service.get(ANDROID_PERMISSION),
            self.contract["PERMISSION_V2"],
        )

    def test_helper_receiver_permission_and_actions_match_protocol(self):
        declared_permissions = {
            node.get(ANDROID_NAME)
            for node in self.helper.findall("permission")
        }
        used_permissions = {
            node.get(ANDROID_NAME)
            for node in self.helper.findall("uses-permission")
        }
        self.assertIn(self.contract["PERMISSION_V2"], declared_permissions)
        self.assertIn(self.contract["PERMISSION"], used_permissions)
        self.assertIn(self.contract["PERMISSION_V2"], used_permissions)

        receivers = {
            node.get(ANDROID_NAME): node
            for node in self.helper.findall("./application/receiver")
        }
        self.assertEqual(
            receivers[".SleepManagerHelperReceiver"].get(ANDROID_PERMISSION),
            self.contract["PERMISSION"],
        )
        self.assertEqual(
            receivers[".SleepManagerHelperReceiverV2"].get(ANDROID_PERMISSION),
            self.contract["PERMISSION_V2"],
        )

        expected_actions = {
            self.contract["ACTION_SLEEP"],
            self.contract["ACTION_WAKE"],
            self.contract["ACTION_RESTORE"],
            self.contract["ACTION_QUERY"],
            self.contract["ACTION_FORGET_STATE"],
            self.contract["ACTION_SET_TEMP_WIFI"],
        }
        for receiver in receivers.values():
            declared_actions = {
                node.get(ANDROID_NAME)
                for node in receiver.findall("./intent-filter/action")
            }
            self.assertEqual(declared_actions, expected_actions)



if __name__ == "__main__":
    unittest.main()
