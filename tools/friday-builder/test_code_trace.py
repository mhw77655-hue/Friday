import importlib.util
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[2] / ".ralph" / "code_trace.py"
SPEC = importlib.util.spec_from_file_location("friday_code_trace", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

class CodeTraceTests(unittest.TestCase):
    def test_python_ast_tracks_nested_class_method(self):
        source = "class Worker:\n    def run(self):\n        return 1\n"
        symbols = MODULE.py_symbols("worker.py", source)
        self.assertEqual([(x["name"],x["start"],x["end"]) for x in symbols],
                         [("Worker",1,3),("Worker.run",2,3)])

    def test_kotlin_lexical_index_tracks_function_body(self):
        source = "class Engine {\n    fun runTask() {\n        helper()\n    }\n}\n"
        symbols = MODULE.lexical_symbols("Engine.kt", source)
        self.assertEqual([(x["name"],x["kind"]) for x in symbols],
                         [("Engine","type"),("runTask","function")])
        self.assertEqual((symbols[1]["start"],symbols[1]["end"]),(2,4))

    def test_stack_resolution_ignores_android_and_jdk_frames(self):
        by_path = {"mobile/app/src/main/java/ai/friday/VoiceService.kt": []}
        self.assertIsNone(MODULE.resolve_stack_location(
            "ThreadPoolExecutor.java", 1145,
            "at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1145)",
            by_path
        ))

    def test_stack_resolution_preserves_unresolved_friday_frames(self):
        result = MODULE.resolve_stack_location(
            "MissingFridayService.kt", 12,
            "at ai.friday.assistant.MissingFridayService.run(MissingFridayService.kt:12)",
            {}
        )
        self.assertEqual(result["resolution"], "friday_source_file_not_found")
        self.assertEqual(result["line"], 12)

    def test_stack_trace_resolves_kotlin_file_and_line(self):
        match = MODULE.STACK.search("at ai.friday.Engine.run(Engine.kt:42)")
        self.assertIsNotNone(match)
        self.assertEqual((match.group(1),match.group(2)),("Engine.kt","42"))

if __name__=="__main__": unittest.main()
