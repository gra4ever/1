import json
import os
from datetime import datetime, timezone

from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from googleapiclient.discovery import build


TOKEN_PATH = os.getenv("DRIVE_ACTIVITY_TOKEN", "/secrets/drive_activity_token.json")
ACTOR_MAP_PATH = os.getenv("DRIVE_ACTIVITY_ACTOR_MAP", "/data/drive_actor_names.json")
SCOPES = ["https://www.googleapis.com/auth/drive.activity.readonly"]


def _parse_time(value):
    if not value:
        return None
    value = str(value).strip()
    if value.endswith("Z"):
        value = value[:-1] + "+00:00"
    try:
        dt = datetime.fromisoformat(value)
    except ValueError:
        return None
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def _rfc3339(value):
    dt = _parse_time(value)
    if not dt:
        return None
    return dt.isoformat().replace("+00:00", "Z")


def _activity_time(activity):
    if activity.get("timestamp"):
        return activity["timestamp"]
    rng = activity.get("timeRange") or {}
    return rng.get("endTime") or rng.get("startTime")


def _actor_rows(activity):
    out = []
    for actor in activity.get("actors", []):
        user = actor.get("user") or {}
        known = user.get("knownUser")
        if known:
            out.append({
                "person_name": known.get("personName"),
                "is_current_user": bool(known.get("isCurrentUser", False)),
                "kind": "known_user",
            })
            continue
        if user.get("anonymousUser") is not None:
            out.append({"person_name": None, "is_current_user": False, "kind": "anonymous_user"})
            continue
        if user.get("deletedUser") is not None:
            out.append({"person_name": None, "is_current_user": False, "kind": "deleted_user"})
            continue
        if actor.get("system") is not None:
            out.append({"person_name": None, "is_current_user": False, "kind": "system"})
            continue
        out.append({"person_name": None, "is_current_user": False, "kind": "unknown"})
    return out


def _load_actor_map():
    try:
        with open(ACTOR_MAP_PATH, "r", encoding="utf-8") as f:
            raw = json.load(f)
        return raw if isinstance(raw, dict) else {}
    except Exception:
        return {}


def _apply_actor_labels(actors):
    mapping = _load_actor_map()
    current_label = os.getenv("DRIVE_ACTIVITY_CURRENT_USER_LABEL", "").strip()
    out = []
    for actor in actors:
        row = dict(actor)
        person_name = row.get("person_name")
        label = mapping.get(person_name) if person_name else None
        if not label and row.get("is_current_user") and current_label:
            label = current_label
        row["label"] = label
        out.append(row)
    return out


def get_credentials():
    if not os.path.exists(TOKEN_PATH):
        raise FileNotFoundError(f"Tokenul Drive Activity nu există la {TOKEN_PATH}")

    creds = Credentials.from_authorized_user_file(TOKEN_PATH, SCOPES)
    if not creds.valid:
        if creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            raise RuntimeError("Tokenul Drive Activity nu este valid și nu poate fi reîmprospătat")
    return creds


def get_service():
    return build("driveactivity", "v2", credentials=get_credentials(), cache_discovery=False)


def query_edits(document_id, after=None, before=None, page_size=100):
    filters = ["detail.action_detail_case:EDIT"]
    after_rfc = _rfc3339(after)
    before_rfc = _rfc3339(before)

    if after_rfc:
        filters.append(f'time > "{after_rfc}"')
    if before_rfc:
        filters.append(f'time <= "{before_rfc}"')

    body = {
        "itemName": f"items/{document_id}",
        "filter": " AND ".join(filters),
        "pageSize": max(1, min(int(page_size), 100)),
    }

    service = get_service()
    activities = []
    page_token = None

    while True:
        request_body = dict(body)
        if page_token:
            request_body["pageToken"] = page_token

        response = service.activity().query(body=request_body).execute()
        activities.extend(response.get("activities", []))
        page_token = response.get("nextPageToken")

        if not page_token or len(activities) >= 500:
            break

    rows = []
    for activity in activities:
        ts = _activity_time(activity)
        dt = _parse_time(ts)
        if not dt:
            continue
        rows.append({
            "timestamp": ts,
            "_sort": dt.timestamp(),
            "actors": _apply_actor_labels(_actor_rows(activity)),
        })

    rows.sort(key=lambda x: x["_sort"], reverse=True)
    for row in rows:
        row.pop("_sort", None)
    return rows


def latest_edit(document_id, after=None, before=None):
    rows = query_edits(document_id, after=after, before=before)
    if not rows:
        return None

    latest = dict(rows[0])
    latest["candidate_count"] = len(rows)
    latest["match_method"] = "latest_document_edit_in_scan_window"
    return latest


def health(document_id):
    latest = latest_edit(document_id)
    return {
        "ok": True,
        "token_path": TOKEN_PATH,
        "latest_edit": latest,
    }
