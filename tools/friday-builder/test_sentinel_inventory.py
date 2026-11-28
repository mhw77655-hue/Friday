from __future__ import annotations
import importlib.util
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("sentinel_inventory.py")
SPEC = importlib.util.spec_from_file_location("sentinel_inventory", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
sentinel = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(sentinel)

class SentinelInventoryTests(unittest.TestCase):
    def test_discovers_manifest_components_permissions_and_persistent_resources(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            main = root / "mobile/app/src/main"
            java = main / "java/ai/friday"
            java.mkdir(parents=True)
            (main / "AndroidManifest.xml").write_text(
                """<manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-permission android:name="android.permission.INTERNET"/>
                <application>
                  <service android:name=".RuntimeService" android:exported="false"/>
                  <receiver android:name=".BootReceiver" android:exported="true"
                    android:permission="example.permission.BOOT"/>
                </application></manifest>""", encoding="utf-8")
            (java / "Stores.kt").write_text(
                '''
                val prefs = context.getSharedPreferences("friday_identity", Context.MODE_PRIVATE)
                val store = Room.databaseBuilder(context, FridayDb::class.java, "friday-memory.db").build()
                class MemorySyncWorker : CoroutineWorker()
                const val DATABASE_NAME = "legacy-cache.db"
                val host = "https://api.example.test/v1"
                ''', encoding="utf-8")
            data = sentinel.build_inventory(root)
            self.assertEqual(["android.permission.INTERNET"], data["permissions"])
            self.assertEqual(2, data["counts"]["manifest_components"])
            self.assertEqual("friday_identity", data["resources"]["shared_preferences"][0]["name"])
            self.assertEqual("friday-memory.db", data["resources"]["room_database"][0]["name"])
            self.assertEqual("legacy-cache.db", data["resources"]["sqlite_database"][0]["name"])
            self.assertEqual("MemorySyncWorker", data["resources"]["work_worker"][0]["name"])
            self.assertEqual("api.example.test", data["resources"]["network_host"][0]["name"])
            self.assertFalse(data["resources"]["room_database"][0]["runtime_verified"])

    def test_refuses_to_claim_inventory_when_manifest_or_source_is_missing(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                sentinel.build_inventory(Path(directory))

    def test_reports_dynamic_preference_names_as_unresolved(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            main = root / "mobile/app/src/main"
            java = main / "java/ai/friday"
            java.mkdir(parents=True)
            (main / "AndroidManifest.xml").write_text(
                "<manifest><application/></manifest>", encoding="utf-8")
            (java / "Dynamic.kt").write_text(
                "fun store(name: String) = context.getSharedPreferences(name, 0)\n",
                encoding="utf-8")
            data = sentinel.build_inventory(root)
            self.assertEqual(1, data["counts"]["dynamic_preference_names"])
            self.assertEqual("name", data["unresolved"]["dynamic_preference_names"][0]["expression"])

if __name__ == "__main__":
    unittest.main()
