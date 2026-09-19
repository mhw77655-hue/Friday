import json
import sys

prd_file, idx_str, reason = sys.argv[1], sys.argv[2], sys.argv[3]
idx = int(idx_str)

with open(prd_file) as f:
    d = json.load(f)

d["userStories"][idx]["passes"] = False
existing = d["userStories"][idx].get("notes", "")
d["userStories"][idx]["notes"] = (existing + " | " + reason).strip(" |")

with open(prd_file, "w") as f:
    json.dump(d, f, indent=2)
