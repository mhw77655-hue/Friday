import importlib.util
import json
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("apply_device_state_capsule.py")
SPEC = importlib.util.spec_from_file_location("device_state_capsule", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(MODULE)

FIXTURE = Path(__file__).parents[2] / ".github" / "android-emulator" / "default-state-capsule.json"


class DeviceStateCapsuleTests(unittest.TestCase):
    def setUp(self):
        self.capsule = json.loads(FIXTURE.read_text(encoding="utf-8"))

    def test_default_synthetic_capsule_is_valid(self):
        self.assertEqual(MODULE.validate_capsule(self.capsule)["api_level"], 35)

    def test_rejects_unexpected_personal_data_fields(self):
        self.capsule["contacts"] = ["not allowed"]
        with self.assertRaises(ValueError):
            MODULE.validate_capsule(self.capsule)

    def test_rejects_invalid_permission_state(self):
        self.capsule["permissions"]["camera"] = "maybe"
        with self.assertRaises(ValueError):
            MODULE.validate_capsule(self.capsule)

    def test_rejects_unbounded_display_dimensions(self):
        self.capsule["display"]["width_px"] = 999999
        with self.assertRaises(ValueError):
            MODULE.validate_capsule(self.capsule)

    def test_rejects_capsules_without_explicit_privacy_scope(self):
        self.capsule["privacy_scope"] = "full_phone_clone"
        with self.assertRaises(ValueError):
            MODULE.validate_capsule(self.capsule)

    def test_offline_state_overrides_last_known_wifi_transport(self):
        self.assertEqual(
            MODULE.network_commands({"transport": "wifi", "connected": False}),
            (("svc", "wifi", "disable"), ("svc", "data", "disable")),
        )

    def test_wifi_state_enables_wifi_and_disables_cellular(self):
        self.assertEqual(
            MODULE.network_commands({"transport": "wifi", "connected": True}),
            (("svc", "wifi", "enable"), ("svc", "data", "disable")),
        )

    def test_cellular_state_disables_wifi_and_enables_mobile_data(self):
        self.assertEqual(
            MODULE.network_commands({"transport": "cellular", "connected": True}),
            (("svc", "wifi", "disable"), ("svc", "data", "enable")),
        )


if __name__ == "__main__":
    unittest.main()
