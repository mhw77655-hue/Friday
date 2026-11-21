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

    def test_disabled_accessibility_is_deleted_not_written_as_empty(self):
        self.assertEqual(
            MODULE.secure_component_setting(
                "enabled_accessibility_services",
                "ai.friday.assistant/ai.friday.assistant.voice.FridayAccessibilityService",
                False,
            ),
            ("settings", "delete", "secure", "enabled_accessibility_services"),
        )

    def test_enabled_accessibility_writes_only_the_allowlisted_component(self):
        component = "ai.friday.assistant/ai.friday.assistant.voice.FridayAccessibilityService"
        self.assertEqual(
            MODULE.secure_component_setting("enabled_accessibility_services", component, True),
            ("settings", "put", "secure", "enabled_accessibility_services", component),
        )

    def test_disabled_notification_listener_is_deleted_not_written_as_empty(self):
        self.assertEqual(
            MODULE.secure_component_setting(
                "enabled_notification_listeners",
                "ai.friday.assistant/ai.friday.assistant.FridayNotificationListener",
                False,
            ),
            ("settings", "delete", "secure", "enabled_notification_listeners"),
        )

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

    def test_wifi_capsule_skips_cellular_commands_when_telephony_is_absent(self):
        self.assertEqual(
            MODULE.network_commands(
                {"transport": "wifi", "connected": True},
                supports_telephony=False,
            ),
            (("svc", "wifi", "enable"),),
        )

    def test_offline_capsule_skips_unsupported_cellular_data_service(self):
        self.assertEqual(
            MODULE.network_commands(
                {"transport": "wifi", "connected": False},
                supports_telephony=False,
            ),
            (("svc", "wifi", "disable"),),
        )

    def test_cellular_capsule_is_rejected_when_device_has_no_telephony(self):
        with self.assertRaisesRegex(ValueError, "no telephony feature"):
            MODULE.network_commands(
                {"transport": "cellular", "connected": True},
                supports_telephony=False,
            )

    def test_runtime_locale_and_timezone_must_match_the_capsule(self):
        properties = {
            "persist.sys.locale": "en-US",
            "ro.product.locale": "en-US",
            "persist.sys.timezone": "UTC",
        }
        MODULE.verify_runtime_defaults(self.capsule, properties.get)

    def test_runtime_locale_mismatch_is_rejected_instead_of_falsely_written(self):
        properties = {
            "persist.sys.locale": "fr-FR",
            "ro.product.locale": "en-US",
            "persist.sys.timezone": "UTC",
        }
        with self.assertRaisesRegex(ValueError, "does not match emulator locale"):
            MODULE.verify_runtime_defaults(self.capsule, properties.get)

    def test_runtime_timezone_mismatch_is_rejected(self):
        properties = {
            "persist.sys.locale": "en-US",
            "ro.product.locale": "en-US",
            "persist.sys.timezone": "Europe/Paris",
        }
        with self.assertRaisesRegex(ValueError, "does not match emulator timezone"):
            MODULE.verify_runtime_defaults(self.capsule, properties.get)

    def test_runtime_locale_falls_back_to_product_default_only_when_unset(self):
        properties = {
            "persist.sys.locale": "",
            "ro.product.locale": "en-US",
            "persist.sys.timezone": "UTC",
        }
        MODULE.verify_runtime_defaults(self.capsule, properties.get)


if __name__ == "__main__":
    unittest.main()
