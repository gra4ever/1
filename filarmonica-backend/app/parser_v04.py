import re
from datetime import date, timedelta

from .parser import clean_text, key_text, parse_program as base_parse_program

RO_MONTHS = {
    1: "ianuarie", 2: "februarie", 3: "martie", 4: "aprilie",
    5: "mai", 6: "iunie", 7: "iulie", 8: "august",
    9: "septembrie", 10: "octombrie", 11: "noiembrie", 12: "decembrie",
}
RO_WEEKDAYS = {
    0: "Luni", 1: "Marți", 2: "Miercuri", 3: "Joi",
    4: "Vineri", 5: "Sâmbătă", 6: "Duminică",
}


def _replace_cbas_in_event(e):
    for s in e.get("string_distributions", []) or []:
        if isinstance(s.get("display"), str):
            s["display"] = s["display"].replace("Contrabas", "C-bas")
    for w in e.get("works", []) or []:
        sd = w.get("string_distribution")
        if isinstance(sd, dict) and isinstance(sd.get("display"), str):
            sd["display"] = sd["display"].replace("Contrabas", "C-bas")


def _classify_event(e):
    raw_date = ((e.get("raw") or {}).get("date") or "")
    if "tmc" not in key_text(raw_date):
        return "orchestra"
    conductor = ((e.get("conductor") or {}).get("name") or "").strip()
    return "orchestra" if conductor else "recital"


def _event_basis_date(e):
    d = e.get("date") or {}
    values = d.get("dates") or d.get("candidate_dates") or []
    if values:
        return values[0]
    for c in e.get("concerts", []) or []:
        if c.get("date"):
            return c["date"]
        candidates = c.get("candidate_dates") or []
        if candidates:
            return candidates[0]
    return None


def _week_end_for_event(d):
    monday = d - timedelta(days=d.weekday())
    friday = monday + timedelta(days=4)
    if d.weekday() == 5:
        return monday + timedelta(days=5)
    if d.weekday() == 6:
        return monday + timedelta(days=6)
    return friday


def _week_label(monday, end):
    if monday.month == end.month:
        return f"{monday.day}–{end.day} {RO_MONTHS[end.month]}"
    return f"{monday.day} {RO_MONTHS[monday.month]} – {end.day} {RO_MONTHS[end.month]}"


def _week_info_for_date(iso_date):
    d = date.fromisoformat(iso_date)
    monday = d - timedelta(days=d.weekday())
    end = _week_end_for_event(d)
    return {
        "start": monday.isoformat(),
        "end": end.isoformat(),
        "label": _week_label(monday, end),
        "collapsed_label": f"{end.day} {RO_MONTHS[end.month]}",
        "key": monday.isoformat(),
    }


def _ensure_orchestra_concert_time(e):
    if e.get("type") != "orchestra":
        return
    for c in e.get("concerts", []) or []:
        if not c.get("time"):
            c["time"] = "19:00"
            c["time_source"] = "default"


def _concert_dates(e):
    out = []
    for c in e.get("concerts", []) or []:
        iso = c.get("date")
        if iso:
            try:
                out.append(date.fromisoformat(iso))
            except Exception:
                pass
    return out


def _mark_concert_in_rehearsals(e):
    if e.get("type") != "orchestra":
        return
    dates = _concert_dates(e)
    if not dates:
        return
    concert_times = [c.get("time") for c in e.get("concerts", []) or [] if c.get("time")]
    preferred = concert_times[0] if concert_times else "19:00"

    for cd in dates:
        target_day = RO_WEEKDAYS[cd.weekday()]
        for rep in e.get("rehearsals", []) or []:
            day = (rep.get("day") or "").split("–")[0].strip()
            if day != target_day:
                continue
            lines = rep.get("lines") or []
            if any("concert" in key_text((x or {}).get("display") or "") for x in lines):
                continue

            chosen = None
            chosen_match = None
            for line in reversed(lines):
                display = (line or {}).get("display") or ""
                ranges = list(re.finditer(r"(\d{2}:\d{2})–(\d{2}:\d{2})", display))
                if not ranges:
                    continue
                exact = [m for m in ranges if m.group(1) == preferred]
                if exact:
                    chosen = line
                    chosen_match = exact[-1]
                    break
                evening = [m for m in ranges if int(m.group(1)[:2]) >= 17]
                if evening and chosen is None:
                    chosen = line
                    chosen_match = evening[-1]
            if chosen is not None and chosen_match is not None:
                display = chosen.get("display") or ""
                start, end = chosen_match.span()
                rng = chosen_match.group(0)
                prefix = display[:start]
                suffix = display[end:]
                chosen["display"] = prefix + "Concert " + rng + suffix
            break


def _rebuild_month(month):
    all_events = []
    for week in month.get("weeks", []) or []:
        all_events.extend(week.get("events", []) or [])
    all_events.extend(month.get("tmc_recitals", []) or [])
    all_events.extend(month.get("recitals", []) or [])

    unique = []
    seen = set()
    for e in all_events:
        old_id = e.get("id") or str(id(e))
        if old_id in seen:
            continue
        seen.add(old_id)
        e["type"] = _classify_event(e)
        if e.get("id"):
            e["id"] = re.sub(r"-(tmc|recital|orchestra)-(\d+)$", rf"-{e['type']}-\2", e["id"])
        _replace_cbas_in_event(e)
        _ensure_orchestra_concert_time(e)
        basis = _event_basis_date(e)
        e["week"] = _week_info_for_date(basis) if basis else None
        _mark_concert_in_rehearsals(e)
        unique.append(e)

    orchestra = [e for e in unique if e.get("type") == "orchestra"]
    recitals = [e for e in unique if e.get("type") == "recital"]

    weeks_map = {}
    undated = []
    for e in orchestra:
        wi = e.get("week")
        if not wi:
            undated.append(e)
            continue
        key = wi["key"]
        if key not in weeks_map:
            weeks_map[key] = {
                "key": key,
                "start": wi["start"],
                "end": wi["end"],
                "label": wi["label"],
                "collapsed_label": wi["collapsed_label"],
                "events": [],
            }
        weeks_map[key]["events"].append(e)
        if wi["end"] > weeks_map[key]["end"]:
            monday = date.fromisoformat(weeks_map[key]["start"])
            end = date.fromisoformat(wi["end"])
            weeks_map[key]["end"] = wi["end"]
            weeks_map[key]["label"] = _week_label(monday, end)
            weeks_map[key]["collapsed_label"] = f"{end.day} {RO_MONTHS[end.month]}"

    weeks = [weeks_map[k] for k in sorted(weeks_map)]
    if undated:
        year = month.get("year")
        month_num = month.get("month")
        weeks.append({
            "key": f"{year}-{month_num:02d}-undated",
            "start": None,
            "end": None,
            "label": "Dată neclară",
            "collapsed_label": "Dată neclară",
            "events": undated,
        })

    month["weeks"] = weeks
    month["recitals"] = recitals
    month["tmc_recitals"] = recitals


def parse_program(raw_doc):
    program = base_parse_program(raw_doc)
    for month in program.get("months", []) or []:
        _rebuild_month(month)
    return program
