from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from run_jvm_test_shard import (
    TestSource,
    assign_shards,
    class_patterns_for_sources,
    collect_test_sources,
)


class JvmTestShardTests(unittest.TestCase):
    def test_source_discovery_uses_exact_class_filters(self):
        with TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "AlphaTest.kt").write_text(
                "package ai.friday.sample\n\n"
                "class AlphaTest {\n"
                "    @Test\n"
                "    fun alpha() = Unit\n"
                "}\n"
                "private class Fixture\n",
                encoding="utf-8",
            )
            nested = root / "nested"
            nested.mkdir()
            (nested / "BetaTest.kt").write_text(
                "package ai.friday.sample.nested\n\n"
                "class BetaTest {\n"
                "    @Test\n"
                "    fun beta() = Unit\n"
                "}\n",
                encoding="utf-8",
            )

            sources = collect_test_sources(root)
            self.assertEqual(
                [
                    "ai.friday.sample.AlphaTest",
                    "ai.friday.sample.nested.BetaTest",
                ],
                class_patterns_for_sources(sources),
            )

    def test_assignment_is_deterministic_disjoint_and_weight_balanced(self):
        sources = [
            TestSource(
                path=Path(f"package/Test{index}Test.kt"),
                package="ai.friday.sample",
                class_names=(f"Test{index}Test",),
                weight=weight,
            )
            for index, weight in enumerate((12, 9, 8, 7, 6, 5, 4, 3, 2, 1))
        ]

        first, first_weights = assign_shards(sources, shard_count=6)
        second, second_weights = assign_shards(sources, shard_count=6)

        first_paths = [[source.path for source in shard] for shard in first]
        second_paths = [[source.path for source in shard] for shard in second]
        self.assertEqual(first_paths, second_paths)
        self.assertEqual(first_weights, second_weights)

        flattened = [source.path for shard in first for source in shard]
        self.assertEqual(sorted(source.path for source in sources), sorted(flattened))
        self.assertEqual(len(flattened), len(set(flattened)))

        all_sources = [source for shard in first for source in shard]
        patterns = class_patterns_for_sources(all_sources)
        self.assertEqual(len(patterns), len(set(patterns)))
        self.assertLessEqual(
            max(first_weights) - min(first_weights),
            max(source.weight for source in sources),
        )


if __name__ == "__main__":
    unittest.main()
