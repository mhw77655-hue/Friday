from __future__ import annotations

import contextlib
import importlib.util
import io
import sys
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("source_test_inventory.py")
SPEC = importlib.util.spec_from_file_location("source_test_inventory", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
inventory = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(inventory)


class SourceTestInventoryTests(unittest.TestCase):
    def _run_with_roots(self, root: Path, production: Path, test_roots: tuple[Path, Path], output: Path) -> str:
        old_root = inventory.ROOT
        old_production = inventory.PRODUCTION
        old_test_roots = inventory.TEST_ROOTS
        old_argv = sys.argv
        try:
            inventory.ROOT = root
            inventory.PRODUCTION = production
            inventory.TEST_ROOTS = test_roots
            sys.argv = [str(MODULE_PATH), "--output", str(output)]
            stdout = io.StringIO()
            with contextlib.redirect_stdout(stdout):
                inventory.main()
            return stdout.getvalue()
        finally:
            inventory.ROOT = old_root
            inventory.PRODUCTION = old_production
            inventory.TEST_ROOTS = old_test_roots
            sys.argv = old_argv

    def test_maps_filename_symbol_reference_and_unmapped_sources(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            production = root / "mobile/app/src/main/java"
            unit_tests = root / "mobile/app/src/test/java"
            android_tests = root / "mobile/app/src/androidTest/java"
            package = Path("ai/friday/assistant")
            for name in ("FilenameMatched", "SymbolReferenced", "Unmapped"):
                source = production / package / f"{name}.kt"
                source.parent.mkdir(parents=True, exist_ok=True)
                source.write_text(f"class {name}\n", encoding="utf-8")

            filename_test = unit_tests / package / "FilenameMatchedTest.kt"
            filename_test.parent.mkdir(parents=True, exist_ok=True)
            filename_test.write_text("class FilenameMatchedTest\n", encoding="utf-8")

            symbol_test = android_tests / package / "SharedIntegrationTest.kt"
            symbol_test.parent.mkdir(parents=True, exist_ok=True)
            symbol_test.write_text("fun exercise() = SymbolReferenced()\n", encoding="utf-8")

            output = root / "reports" / "source-test-map.tsv"
            summary = self._run_with_roots(root, production, (unit_tests, android_tests), output)

            rows = {}
            for line in output.read_text(encoding="utf-8").splitlines()[1:]:
                source, kind, candidates = line.split("\t")
                rows[Path(source).stem] = (kind, candidates)

            self.assertEqual("filename", rows["FilenameMatched"][0])
            self.assertEqual("symbol-reference", rows["SymbolReferenced"][0])
            self.assertEqual("unmapped", rows["Unmapped"][0])
            self.assertIn("Production Kotlin sources: 3", summary)
            self.assertTrue(output.is_file())

    def test_refuses_to_publish_an_empty_source_inventory(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            production = root / "missing-production"
            output = root / "reports" / "empty.tsv"
            with self.assertRaises(SystemExit):
                self._run_with_roots(root, production, (root / "unit", root / "android"), output)
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
