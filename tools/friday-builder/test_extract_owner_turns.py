import importlib.util
import tempfile
import unittest
import zipfile
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("extract_owner_turns.py")
SPEC = importlib.util.spec_from_file_location("extract_owner_turns", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ExtractOwnerTurnsTests(unittest.TestCase):
    def test_extracts_only_owner_turns_and_keeps_source_provenance(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "chats.zip"
            with zipfile.ZipFile(archive, "w") as zf:
                zf.writestr(
                    "conversation.txt",
                    "You: Friday must be independent.\n"
                    "ChatGPT: Friday is a chatbot.\n"
                    "You: The model is optional compute.\n"
                )
            turns = list(MODULE.owner_turns(str(archive)))
        self.assertEqual(len(turns), 2)
        self.assertEqual(turns[0][0], "conversation.txt")
        self.assertEqual(turns[0][1], 1)
        self.assertEqual(turns[0][2], "Friday must be independent.")
        self.assertEqual(turns[1][2], "The model is optional compute.")
        self.assertNotIn("chatbot", " ".join(turn[2] for turn in turns).casefold())

    def test_does_not_emit_assistant_only_source(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "chats.zip"
            with zipfile.ZipFile(archive, "w") as zf:
                zf.writestr("assistant.txt", "ChatGPT: A long assistant answer.\n")
            self.assertEqual(list(MODULE.owner_turns(str(archive))), [])


if __name__ == "__main__":
    unittest.main()
