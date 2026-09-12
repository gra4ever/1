import json
import os
from copy import deepcopy
from datetime import datetime, timezone

DATA_DIR = os.getenv("DATA_DIR", "/data")
SNAPSHOT_PATH = os.path.join(DATA_DIR, "snapshot.json")
NEWS_PATH = os.path.join(DATA_DIR, "news.json")
STATUS_PATH = os.path.join(DATA_DIR, "status.json")


def _ensure():
    os.makedirs(DATA_DIR, exist_ok=True)


def _read(path, default):
    try:
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        return deepcopy(default)
    except Exception:
        return deepcopy(default)


def _write(path, obj):
    _ensure()
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
    os.replace(tmp, path)


def load_snapshot():
    return _read(SNAPSHOT_PATH, None)


def save_snapshot(program):
    _write(SNAPSHOT_PATH, program)


def load_news():
    return _read(NEWS_PATH, [])


def save_news(news):
    _write(NEWS_PATH, news)


def load_status():
    return _read(STATUS_PATH, {})


def save_status(status):
    _write(STATUS_PATH, status)


def utc_now_iso():
    return datetime.now(timezone.utc).isoformat()


def flatten_events(program):
    out = {}
    for month in program.get("months", []):
        for week in month.get("weeks", []):
            for e in week.get("events", []):
                out[e["id"]] = e
        for e in month.get("tmc_recitals", []):
            out[e["id"]] = e
    return out


def _orchestration_sem(works):
    return [
        {
            "composer": w.get("composer"),
            "title": w.get("title"),
            "orchestrations": [
                {"label": o.get("label"), "display": o.get("display")}
                for o in w.get("orchestrations", [])
            ],
            "strings": (w.get("string_distribution") or {}).get("display"),
            "details": w.get("details", []),
        }
        for w in works
    ]


def semantic_event(e):
    return {
        "type": e.get("type"),
        "date": {
            "dates": e.get("date", {}).get("dates", []),
            "candidate_dates": e.get("date", {}).get("candidate_dates", []),
            "multiple": e.get("date", {}).get("multiple"),
            "uncertain": e.get("date", {}).get("uncertain"),
            "notes": e.get("date", {}).get("notes", []),
        },
        "concerts": e.get("concerts", []),
        "venue": e.get("venue"),
        "conductor": {
            "name": e.get("conductor", {}).get("name"),
            "choir": e.get("conductor", {}).get("choir"),
            "reserve": e.get("conductor", {}).get("reserve"),
        },
        "soloists": [
            {"name": s.get("name"), "instrument": s.get("instrument"), "role": s.get("role")}
            for s in e.get("soloists", [])
        ],
        "works": _orchestration_sem(e.get("works", [])),
        "program_notes": e.get("program_notes", []),
        "strings": [
            {"display": s.get("display"), "applies_to": s.get("applies_to")}
            for s in e.get("string_distributions", [])
        ],
        "rehearsals": [
            {
                "day": r.get("day"),
                "annotation": r.get("annotation"),
                "lines": [x.get("display") for x in r.get("lines", [])],
            }
            for r in e.get("rehearsals", [])
        ],
        "program_status": e.get("program_status"),
    }


def _summary(e):
    if not e:
        return None
    return {
        "id": e.get("id"),
        "date": e.get("raw", {}).get("date"),
        "week": e.get("week"),
        "type": e.get("type"),
        "conductor": e.get("conductor", {}).get("name"),
        "soloists": [s.get("raw") for s in e.get("soloists", [])],
    }


def diff_program(old_program, new_program):
    if old_program is None:
        return []
    old = flatten_events(old_program)
    new = flatten_events(new_program)
    changes = []
    for event_id in sorted(set(old) | set(new)):
        o = old.get(event_id)
        n = new.get(event_id)
        if o is None:
            changes.append({
                "event_id": event_id,
                "type": "event_added",
                "label": "Eveniment adăugat",
                "important": True,
                "old": None,
                "new": _summary(n),
                "week": n.get("week"),
            })
            continue
        if n is None:
            changes.append({
                "event_id": event_id,
                "type": "event_removed",
                "label": "Eveniment eliminat",
                "important": True,
                "old": _summary(o),
                "new": None,
                "week": o.get("week"),
            })
            continue
        so, sn = semantic_event(o), semantic_event(n)
        field_map = [
            ("date", "concert", "Data concertului modificată", True),
            ("concerts", "concert", "Ora/data concertului modificată", True),
            ("venue", "venue", "Locație modificată", True),
            ("conductor", "conductor", "Dirijor/COR modificat", True),
            ("soloists", "soloists", "Solist modificat", True),
            ("works", "program", "Program/distribuție modificată", True),
            ("program_notes", "program", "Program modificat", True),
            ("strings", "strings", "Distribuția cordarilor modificată", True),
            ("rehearsals", "rehearsals", "Programul repetițiilor modificat", True),
            ("program_status", "status", "Starea programului modificată", True),
        ]
        for field, ctype, label, important in field_map:
            if so[field] != sn[field]:
                changes.append({
                    "event_id": event_id,
                    "type": ctype,
                    "label": label,
                    "field": field,
                    "important": important,
                    "old": so[field],
                    "new": sn[field],
                    "week": n.get("week") or o.get("week"),
                    "event": _summary(n),
                })
    return changes


def aggregate_news(changes, detected_at=None, source="automatic"):
    if not changes:
        return []
    detected_at = detected_at or utc_now_iso()
    grouped = {}
    for ch in changes:
        week = ch.get("week") or {}
        key = week.get("key") or f"event:{ch.get('event_id')}"
        if key not in grouped:
            grouped[key] = {
                "id": f"{detected_at}|{key}",
                "detected_at": detected_at,
                "source": source,
                "week_key": week.get("key"),
                "week_label": week.get("label") or "Eveniment",
                "collapsed_label": week.get("collapsed_label"),
                "change_count": 0,
                "important_count": 0,
                "changes": [],
            }
        grouped[key]["changes"].append(ch)
        grouped[key]["change_count"] += 1
        if ch.get("important"):
            grouped[key]["important_count"] += 1
    return list(grouped.values())
