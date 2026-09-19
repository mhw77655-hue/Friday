# ======================================================================
# mobile/compute_node.py — Phase 4.4, Tablet as Compute Node.
#
# Reads the state file tablet_sync.sh writes after every sync attempt
# and turns it into a status 4.5 (Local Sergeant) can actually use.
# This is the whole point of building 4.4 before 4.5: without it,
# 4.5 either duplicates this parsing itself or — worse — answers as
# if the local checkout were live. Import get_sync_status() /
# staleness_note() instead of reading STATE_FILE directly, so there's
# exactly one place that understands the state file's shape.
#
# Deliberately dependency-free (stdlib only) — this runs under
# Termux's Python, not the Space's environment, no requirements.txt
# to keep in sync between the two.
# ======================================================================

import json
import os
import datetime
import pathlib

STATE_FILE = pathlib.Path(os.path.expanduser("~/.jarvis_tablet_state.json"))
REPO_DIR = pathlib.Path(os.path.expanduser("~/jarvis-repo"))


def get_sync_status():
    """Returns the raw synced state, or an honest 'never synced' shape
    if tablet_sync.sh has never run successfully. Never raises —
    a missing/corrupt state file is itself a status worth reporting,
    not an exception 4.5 has to handle."""
    if not STATE_FILE.exists():
        return {
            "status": "never_synced",
            "last_success_utc": None,
            "last_attempt_utc": None,
            "last_commit": None,
            "repo_path": str(REPO_DIR),
            "error": "tablet_sync.sh has not run yet on this device",
        }
    try:
        with open(STATE_FILE) as f:
            state = json.load(f)
    except (json.JSONDecodeError, OSError) as e:
        return {
            "status": "corrupt_state",
            "last_success_utc": None,
            "last_attempt_utc": None,
            "last_commit": None,
            "repo_path": str(REPO_DIR),
            "error": f"state file unreadable: {e}",
        }
    return state


def age_seconds():
    """Seconds since the last *successful* sync. None if never synced."""
    state = get_sync_status()
    last_success = state.get("last_success_utc")
    if not last_success:
        return None
    then = datetime.datetime.strptime(last_success, "%Y-%m-%dT%H:%M:%SZ").replace(
        tzinfo=datetime.timezone.utc
    )
    now = datetime.datetime.now(datetime.timezone.utc)
    return (now - then).total_seconds()


def staleness_note():
    """The honest-staleness string 4.5's own spec calls for —
    e.g. 'answering from a snapshot ~14h old' — instead of silently
    answering as if live. Returns a ready-to-use sentence fragment."""
    seconds = age_seconds()
    if seconds is None:
        return "no local sync has ever completed on this device"

    hours = seconds / 3600
    if hours < 1:
        minutes = int(seconds / 60)
        return f"answering from a snapshot ~{minutes}m old"
    elif hours < 48:
        return f"answering from a snapshot ~{int(hours)}h old"
    else:
        days = int(hours / 24)
        return f"answering from a snapshot ~{days}d old — sync has been failing or not run"


def is_stale(threshold_hours=6):
    """Simple boolean gate 4.5 can use to decide whether to warn louder
    or refuse a time-sensitive answer outright. Also true if a sync
    attempt is currently failing, even if an old success exists."""
    state = get_sync_status()
    if state.get("status") == "failed":
        return True
    seconds = age_seconds()
    if seconds is None:
        return True
    return (seconds / 3600) > threshold_hours


if __name__ == "__main__":
    # Quick manual check from a Termux shell: `python compute_node.py`
    status = get_sync_status()
    print(json.dumps(status, indent=2))
    print(staleness_note())
    print("stale:", is_stale())
