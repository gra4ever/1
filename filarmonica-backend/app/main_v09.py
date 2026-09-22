import os

from . import main_v08 as v8
from .drive_activity import health as drive_activity_health
from .drive_activity import latest_edit


base = v8.base
VERSION = "0.9.0"
base.VERSION = VERSION
base.app.version = VERSION
app = base.app


def _drive_metadata(previous_scan, now):
    try:
        activity = latest_edit(base.DOC_ID, after=previous_scan, before=now)
        return activity, None
    except Exception as e:
        # Drive Activity is deliberately non-critical: program reading must
        # continue even if OAuth/token/API access fails.
        return None, str(e)


def _enrich_news(entries, activity, detected_at):
    for entry in entries:
        entry["modified_at"] = activity.get("timestamp") if activity else detected_at
        entry["modified_at_source"] = "drive_activity" if activity else "backend_detection"

        if not activity:
            continue

        actors = activity.get("actors") or []
        entry["drive_activity_candidate_count"] = activity.get("candidate_count", 1)
        entry["drive_activity_match_method"] = activity.get("match_method")
        entry["actors"] = actors

        # Friendly label is optional. It can be supplied by
        # /data/drive_actor_names.json without exposing Google IDs in the UI.
        labels = [a.get("label") for a in actors if a.get("label")]
        if labels:
            entry["actor_label"] = ", ".join(dict.fromkeys(labels))

        person_names = [a.get("person_name") for a in actors if a.get("person_name")]
        if person_names:
            entry["actor_person_names"] = list(dict.fromkeys(person_names))

    return entries


def refresh_snapshot_v09(source="automatic"):
    now = base.utc_now_iso()
    try:
        raw = base.fetch_raw_program()
        new_program = base.parse_program(raw)
        old_program = base.load_snapshot()
        previous_status = base.load_status()

        migrating = bool(old_program is not None and previous_status.get("version") != VERSION)

        if old_program is None or migrating:
            # Do not create false News items on a backend/parser upgrade.
            _, drive_error = _drive_metadata(None, now)
            base.save_snapshot(new_program)
            base.save_status({
                "ok": True,
                "last_scan": now,
                "last_change_count": 0,
                "baseline_created": old_program is None,
                "schema_migrated": migrating,
                "source": source,
                "version": VERSION,
                "drive_activity_ok": drive_error is None,
                "drive_activity_error": drive_error,
            })
            return {
                "ok": True,
                "baseline_created": old_program is None,
                "schema_migrated": migrating,
                "change_count": 0,
                "program": new_program,
                "last_scan": now,
            }

        changes = base.diff_program(old_program, new_program)
        activity = None
        drive_error = None

        if changes:
            activity, drive_error = _drive_metadata(previous_status.get("last_scan"), now)
            entries = base.aggregate_news(changes, detected_at=now, source=source)
            entries = _enrich_news(entries, activity, now)
            base.save_news(entries + base.load_news())
        else:
            # A no-change scan should not query Drive Activity unnecessarily.
            drive_error = previous_status.get("drive_activity_error")

        base.save_snapshot(new_program)
        base.save_status({
            "ok": True,
            "last_scan": now,
            "last_change_count": len(changes),
            "baseline_created": False,
            "schema_migrated": False,
            "source": source,
            "version": VERSION,
            "drive_activity_ok": drive_error is None,
            "drive_activity_error": drive_error,
            "drive_activity_last_match": activity,
        })
        return {
            "ok": True,
            "baseline_created": False,
            "schema_migrated": False,
            "change_count": len(changes),
            "changes": changes,
            "program": new_program,
            "last_scan": now,
            "drive_activity": activity,
            "drive_activity_error": drive_error,
        }
    except Exception as e:
        base.save_status({
            "ok": False,
            "last_scan": now,
            "error": str(e),
            "source": source,
            "version": VERSION,
        })
        raise


# Existing v0.4-v0.8 route handlers and scheduler resolve this global at
# runtime, so replacing it upgrades refresh behavior without duplicating routes.
base.refresh_snapshot = refresh_snapshot_v09


@app.get("/admin/drive-activity-test")
def admin_drive_activity_test(request: base.Request):
    # Reuse v0.8 Cloudflare/admin authorization.
    v8.admin_guard(request)
    try:
        return drive_activity_health(base.DOC_ID)
    except Exception as e:
        return {
            "ok": False,
            "error": str(e),
            "token_exists": os.path.exists(
                os.getenv("DRIVE_ACTIVITY_TOKEN", "/secrets/drive_activity_token.json")
            ),
        }
