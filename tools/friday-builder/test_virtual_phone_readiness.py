import unittest

from wait_for_android_system import is_system_ready


class AndroidSystemReadinessTests(unittest.TestCase):
    def state(self, **overrides):
        value = {
            "boot_completed": "1",
            "packages": "package:android\npackage:com.android.settings\n",
            "settings_device_provisioned": "0",
            "storage_volumes": "private mounted null\nemulated mounted null",
        }
        value.update(overrides)
        return value

    def test_ready_only_when_settings_provider_answers(self):
        self.assertTrue(is_system_ready(self.state()))

    def test_package_enumeration_alone_does_not_prove_settings_provider_ready(self):
        self.assertFalse(
            is_system_ready(self.state(settings_device_provisioned="Cannot access system provider"))
        )

    def test_boot_completed_is_required(self):
        self.assertFalse(is_system_ready(self.state(boot_completed="0")))

    def test_required_system_packages_are_required(self):
        self.assertFalse(
            is_system_ready(self.state(packages="package:android\n"))
        )

    def test_settings_and_package_manager_can_be_ready_before_storage_mounts(self):
        self.assertFalse(
            is_system_ready(self.state(storage_volumes="private unmounted null\nemulated unmounted null"))
        )

    def test_accepts_user_scoped_volume_identifiers(self):
        self.assertTrue(
            is_system_ready(self.state(
                storage_volumes="private:179,64 mounted null\nemulated;0 mounted null"
            ))
        )

    def test_private_storage_must_be_mounted(self):
        self.assertFalse(
            is_system_ready(self.state(storage_volumes="private unmounted null\nemulated mounted null"))
        )

    def test_emulated_storage_must_be_mounted_for_package_installer(self):
        self.assertFalse(
            is_system_ready(self.state(storage_volumes="private mounted null\nemulated unmounted null"))
        )

    def test_null_or_unknown_settings_value_is_not_ready(self):
        for value in ("", "null", "unknown", "Exception occurred"):
            with self.subTest(value=value):
                self.assertFalse(
                    is_system_ready(self.state(settings_device_provisioned=value))
                )


if __name__ == "__main__":
    unittest.main()
