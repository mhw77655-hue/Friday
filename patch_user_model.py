import sys

def patch(path, old, new, label):
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    if old not in content:
        print(f"FAILED: could not find target block for {label} in {path} — nothing written.")
        sys.exit(1)
    if content.count(old) > 1:
        print(f"FAILED: target block for {label} appears more than once in {path} — refusing to guess. Nothing written.")
        sys.exit(1)
    content = content.replace(old, new)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"OK: patched {label} in {path}")

# --- 1. core/db.py: add append-only user_model_log table + one-time migration ---
db_old = '''    con.execute("""CREATE TABLE IF NOT EXISTS user_model (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        ts TEXT, attribute TEXT UNIQUE NOT NULL, value TEXT)""")'''

db_new = '''    con.execute("""CREATE TABLE IF NOT EXISTS user_model (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        ts TEXT, attribute TEXT UNIQUE NOT NULL, value TEXT)""")
    con.execute("""CREATE TABLE IF NOT EXISTS user_model_log (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        ts TEXT, attribute TEXT NOT NULL, value TEXT, user_id TEXT DEFAULT 'default')""")
    # One-time migration: carry forward existing overwrite-table rows into
    # the new append-only log so history-based reads see current values
    # immediately instead of starting empty. Guarded so it only runs once
    # (skips if user_model_log already has rows).
    try:
        already_migrated = con.execute("SELECT COUNT(*) FROM user_model_log").fetchone()[0]
        if already_migrated == 0:
            legacy_rows = con.execute("SELECT ts, attribute, value FROM user_model").fetchall()
            for ts, attribute, value in legacy_rows:
                con.execute(
                    "INSERT INTO user_model_log (ts, attribute, value) VALUES (?, ?, ?)",
                    (ts, attribute, value),
                )
            con.commit()
    except Exception:
        pass'''

patch("core/db.py", db_old, db_new, "user_model_log schema + migration")

# --- 2. core/memory.py: switch update_user_model / get_user_model to append-only ---
mem_old = '''def update_user_model(attribute, value):
    if not attribute or not str(attribute).strip():
        return
    con = sqlite3.connect(DB_PATH)
    try:
        con.execute(
            """INSERT INTO user_model(ts, attribute, value) VALUES (?, ?, ?)
               ON CONFLICT(attribute) DO UPDATE SET ts=excluded.ts, value=excluded.value""",
            (datetime.datetime.utcnow().isoformat(), str(attribute).strip(), str(value))
        )
        con.commit()
    except Exception:
        pass
    finally:
        con.close()


def get_user_model():
    con = sqlite3.connect(DB_PATH)
    try:
        rows = con.execute("SELECT attribute, value FROM user_model ORDER BY attribute").fetchall()
    except Exception:
        rows = []
    finally:
        con.close()
    return {attr: val for attr, val in rows}'''

mem_new = '''def update_user_model(attribute, value):
    """Append-only write: every call adds a new row to user_model_log
    rather than overwriting the previous value for this attribute.
    Matches the same append-only principle used elsewhere (vault writes,
    belief_tracker) -- history is preserved, and get_user_model() below
    resolves "current value" at read time instead of destroying the old
    one at write time."""
    if not attribute or not str(attribute).strip():
        return
    con = sqlite3.connect(DB_PATH)
    try:
        con.execute(
            "INSERT INTO user_model_log (ts, attribute, value) VALUES (?, ?, ?)",
            (datetime.datetime.utcnow().isoformat(), str(attribute).strip(), str(value))
        )
        con.commit()
    except Exception:
        pass
    finally:
        con.close()


def get_user_model():
    """Returns the current (most recent) value per attribute, resolved
    from the full append-only log rather than read off a table that
    overwrites in place. Same return shape as before ({attribute: value}),
    so callers (dispatch.py, process_request.py, api/routes.py,
    correlate.py) need no changes."""
    con = sqlite3.connect(DB_PATH)
    try:
        rows = con.execute(
            """SELECT attribute, value FROM user_model_log um
               WHERE id = (SELECT MAX(id) FROM user_model_log WHERE attribute = um.attribute)
               ORDER BY attribute"""
        ).fetchall()
    except Exception:
        rows = []
    finally:
        con.close()
    return {attr: val for attr, val in rows}'''

patch("core/memory.py", mem_old, mem_new, "update_user_model / get_user_model")

print("Done.")
