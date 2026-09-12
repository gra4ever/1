import re
import unicodedata
from datetime import date, timedelta

MONTHS = {
    "SEPT": (9, "septembrie"),
    "OCT": (10, "octombrie"),
    "NOV": (11, "noiembrie"),
    "DEC": (12, "decembrie"),
    "IAN": (1, "ianuarie"),
    "FEB": (2, "februarie"),
    "MAR": (3, "martie"),
    "APR": (4, "aprilie"),
    "MAI": (5, "mai"),
    "IUN": (6, "iunie"),
}
WEEKDAYS = {
    "luni": "Luni",
    "marti": "Marți",
    "miercuri": "Miercuri",
    "joi": "Joi",
    "vineri": "Vineri",
    "sambata": "Sâmbătă",
    "duminica": "Duminică",
}
STRING_NAMES = ["Vioara I", "Vioara II", "Viola", "Violoncel", "Contrabas"]
INSTRUMENT_WORDS = [
    "vioară", "vioara", "violoncel", "pian", "soprană", "soprana", "tenor",
    "bariton", "flaut", "clarinet", "corn", "acordeon", "narator", "narrator",
    "solo voce", "voce", "violă", "viola"
]


def strip_diacritics(s):
    return "".join(c for c in unicodedata.normalize("NFD", s or "") if unicodedata.category(c) != "Mn")


def key_text(s):
    s = strip_diacritics(s).lower()
    s = re.sub(r"\s+", " ", s).strip()
    return s


def clean_text(s):
    if not s:
        return ""
    s = s.replace("\x0b", "\n").replace("\r\n", "\n").replace("\r", "\n").replace("\u00a0", " ")
    lines = []
    for line in s.split("\n"):
        line = re.sub(r"[ \t]+", " ", line).strip()
        if line:
            lines.append(line)
    return "\n".join(lines)


_TIME_RANGE_RE = re.compile(
    r"(?<![\d.])(\d{1,2})(?::(\d{2}))?\s*[-–—]\s*(\d{1,2})(?::(\d{2}))?(?![\d.])"
)


def normalize_time_ranges(text):
    def repl(m):
        h1, m1, h2, m2 = m.groups()
        a, b = int(h1), int(h2)
        if a > 23 or b > 23:
            return m.group(0)
        return f"{a:02d}:{int(m1 or 0):02d}–{b:02d}:{int(m2 or 0):02d}"
    return _TIME_RANGE_RE.sub(repl, text or "")


def normalize_orchestration(line):
    line = clean_text(line).replace("\n", " ").strip()
    if not line:
        return ""
    compact = re.fullmatch(r"(\d{4})/(\d{4})/(.+)", line, flags=re.I)
    if compact:
        a, b, tail = compact.groups()
        first = " ".join(a)
        second = " ".join(b)
        tail_parts = [x.strip() for x in tail.split("/") if x.strip()]
        mapped = []
        names = {
            "hp": "Hp", "va": "Vla", "vc": "Vc", "db": "Cb", "cb": "Cb",
            "pf": "Pf", "cel": "Cel", "archi": "Archi", "t": "T", "p": "P",
        }
        for p in tail_parts:
            low = p.lower()
            dot_parts = low.split(".")
            if len(dot_parts) > 1 and all(x in names for x in dot_parts):
                mapped.extend(names[x] for x in dot_parts)
            else:
                mapped.append(names.get(low, p))
        return f"{first}; {second}; " + "; ".join(mapped)
    line = re.sub(r"\s*;\s*", "; ", line)
    line = re.sub(r";\s*;+", "; ", line)
    line = re.sub(r"\s+", " ", line).strip()
    return line


def looks_like_orchestration(line):
    x = line.strip()
    if not x:
        return False
    if re.fullmatch(r"\d{4}/\d{4}/.+", x, re.I):
        return True
    if re.match(r"^[0-9][0-9*+ ]{2,}", x) and (";" in x or re.search(r"\b[TP]\b|Archi|Hp|Pf|Cel", x, re.I)):
        return True
    return False


def parse_soloists(raw):
    raw = clean_text(raw)
    result = []
    for line in raw.splitlines():
        line = line.strip()
        if not line:
            continue
        instrument = None
        role = None
        name = line
        role_match = re.search(r"\s*(\([^)]*\))\s*$", line)
        base_line = line
        if role_match:
            role = role_match.group(1)
            base_line = line[:role_match.start()].strip()
        low_base = key_text(base_line)
        for inst in sorted(INSTRUMENT_WORDS, key=lambda x: len(key_text(x)), reverse=True):
            k = key_text(inst)
            if low_base.endswith(k):
                instrument = base_line[len(base_line) - len(inst):].strip()
                name = base_line[:len(base_line) - len(inst)].strip(" -–—")
                break
        result.append({"name": name, "instrument": instrument, "role": role, "raw": line})
    return result


def parse_conductor(raw):
    raw = clean_text(raw)
    lines = [x for x in raw.splitlines() if x]
    choir = any("+cor" in key_text(x) for x in lines)
    names = [x for x in lines if "+cor" not in key_text(x)]
    reserve = None
    primary = []
    for x in names:
        if "rezerva" in key_text(x):
            reserve = x.strip("() ")
        else:
            primary.append(x)
    return {"name": "\n".join(primary).strip(), "choir": choir, "reserve": reserve, "raw": raw}


def month_meta(header, table_index):
    h = key_text(header).replace(".", "").upper()
    key = next((k for k in MONTHS if h.startswith(k)), None)
    if not key:
        order = ["SEPT", "OCT", "NOV", "DEC", "IAN", "FEB", "MAR", "APR", "MAI", "IUN"]
        key = order[table_index - 1]
    month_num, month_name = MONTHS[key]
    year = 2026 if month_num >= 9 else 2027
    return month_num, month_name, year


def classify_event(raw_date, raw_conductor, raw_program):
    """TMC is a festival marker, not a display category.

    TMC rows with a conductor are orchestral concerts and belong in Orchestră.
    TMC rows without conductor stay in Recitaluri. Non-TMC rows are orchestral.
    """
    if "tmc" not in key_text(raw_date):
        return "orchestra"
    if clean_text(raw_conductor):
        return "orchestra"
    return "recital"


def parse_date_cell(raw, month_num, year, event_type):
    raw = clean_text(raw)
    lines = raw.splitlines()
    first = lines[0] if lines else ""
    uncertain = "?" in first
    nums = [int(x) for x in re.findall(r"(?<!\d)(\d{1,2})(?!\d)", first)]
    multiple = "+" in first and len(nums) >= 2
    days = []
    candidate_days = []
    if multiple:
        days = nums[:]
    elif nums and not uncertain:
        days = [nums[0]]
    elif nums and uncertain:
        candidate_days = [nums[0]]
        for line in lines[1:]:
            if re.fullmatch(r"\d{1,2}", line.strip()):
                candidate_days.append(int(line.strip()))
    dates, candidate_dates = [], []
    for d in days:
        try:
            dates.append(date(year, month_num, d))
        except ValueError:
            pass
    for d in candidate_days:
        try:
            candidate_dates.append(date(year, month_num, d))
        except ValueError:
            pass
    hour_match = re.search(r"\bora\s*(\d{1,2})(?::(\d{2}))?\b", raw, re.I)
    explicit_time = None
    if hour_match:
        explicit_time = f"{int(hour_match.group(1)):02d}:{int(hour_match.group(2) or 0):02d}"
    venue = None
    venue_candidates = []
    for line in lines[1:]:
        k = key_text(line)
        if not line:
            continue
        if "tmc" in k:
            remainder = re.sub(r"(?i)\bTMC\b", "", line).strip(" -–—")
            if remainder and not re.search(r"(?i)\bora\b", remainder):
                venue_candidates.append(remainder)
            continue
        if re.search(r"\bora\b", k):
            continue
        if re.search(r"zile libere|paste|paște", k):
            continue
        if re.fullmatch(r"\d{1,2}", line.strip()):
            continue
        if re.fullmatch(r"\d+(?:[.,]\s*\d+)*.*", line):
            continue
        venue_candidates.append(line)
    if venue_candidates:
        venue = " • ".join(venue_candidates)
    notes = []
    for line in lines[1:]:
        k = key_text(line)
        if "tmc" in k or re.search(r"\bora\b", k):
            continue
        if venue and line in venue_candidates:
            continue
        if uncertain and re.fullmatch(r"\d{1,2}", line.strip()):
            continue
        notes.append(line)
    return {
        "raw": raw,
        "dates": [d.isoformat() for d in dates],
        "candidate_dates": [d.isoformat() for d in candidate_dates],
        "days": days,
        "candidate_days": candidate_days,
        "multiple": multiple,
        "uncertain": uncertain or ("(?)" in raw),
        "explicit_time": explicit_time,
        "venue": venue,
        "notes": notes,
    }


def split_sections(program):
    program = clean_text(program)
    lines = program.splitlines() if program else []
    rep_idx = None
    cord_idx = None
    for i, line in enumerate(lines):
        k = key_text(line).rstrip(":")
        if rep_idx is None and ("program repetitii" in k or k == "repetitii"):
            rep_idx = i
        if cord_idx is None and k == "cordari":
            cord_idx = i
    day_idx = None
    if rep_idx is None:
        for i, line in enumerate(lines):
            if re.match(r"^(Luni|Marți|Marti|Miercuri|Joi|Vineri|Sâmbătă|Sambata|Duminică|Duminica)\b", line, re.I):
                if cord_idx is not None and i > cord_idx:
                    day_idx = i
                    break
    if rep_idx is None:
        rep_idx = day_idx
    cut_candidates = [x for x in (rep_idx, cord_idx) if x is not None]
    program_end = min(cut_candidates) if cut_candidates else len(lines)
    program_lines = lines[:program_end]
    cord_lines = []
    if cord_idx is not None:
        end = len(lines)
        if rep_idx is not None and rep_idx > cord_idx:
            end = rep_idx
        else:
            for i in range(cord_idx + 1, len(lines)):
                if re.match(r"^(Luni|Marți|Marti|Miercuri|Joi|Vineri|Sâmbătă|Sambata|Duminică|Duminica)\b", lines[i], re.I):
                    end = i
                    if rep_idx is None:
                        rep_idx = i
                    break
        cord_lines = lines[cord_idx + 1:end]
    rep_lines = []
    if rep_idx is not None:
        start = rep_idx + 1 if "repet" in key_text(lines[rep_idx]) else rep_idx
        end = cord_idx if cord_idx is not None and cord_idx > rep_idx else len(lines)
        rep_lines = lines[start:end]
    return program_lines, cord_lines, rep_lines


def parse_works(program_lines):
    works = []
    notes = []
    current = None
    ensemble_label = None
    heading_re = re.compile(r"^(.{2,}?)\s*[-–—]\s*(.{2,})$")
    def is_heading(line):
        m = heading_re.match(line)
        return bool(m and not re.match(r"^(Concert|Luni|Marți|Marti|Miercuri|Joi|Vineri)\b", line, re.I))
    has_composer_headings = any(is_heading(line.strip()) for line in program_lines)
    def new_work(composer, title, raw):
        w = {"composer": composer, "title": title, "raw_heading": raw, "orchestrations": [], "details": [], "string_distribution": None}
        works.append(w)
        return w
    for line in program_lines:
        line = line.strip()
        if not line:
            continue
        k = key_text(line)
        if k in ("pauza", "pauză") or re.match(r"^Concert\s+\d{1,2}\.\d{1,2}\s*:", line, re.I):
            notes.append(line)
            continue
        if re.match(r"^orchestr[ăa] (mare|de camer[ăa])\s*:", line, re.I):
            ensemble_label = line.rstrip(":")
            if current:
                current["details"].append(line)
            else:
                notes.append(line)
            continue
        if looks_like_orchestration(line):
            if current is None:
                notes.append(normalize_orchestration(line))
            else:
                current["orchestrations"].append({"label": ensemble_label, "raw": line, "display": normalize_orchestration(line)})
            ensemble_label = None
            continue
        m = heading_re.match(line)
        if m and is_heading(line):
            current = new_work(m.group(1).strip(), m.group(2).strip(), line)
            ensemble_label = None
            continue
        if current is None:
            if has_composer_headings:
                notes.append(line)
            elif not works:
                current = new_work(None, line, line)
            else:
                notes.append(line)
        elif current is not None:
            current["details"].append(line)
        else:
            notes.append(line)
    return works, notes


def format_strings(nums, compact=False):
    names = ["Vl I", "Vl II", "Vla", "Vc", "Cb"] if compact else STRING_NAMES
    return " • ".join(f"{names[i]} - {n}" for i, n in enumerate(nums[:5]))


def parse_strings(lines):
    items = []
    for line in lines:
        nums = [int(x) for x in re.findall(r"(?<![\d.])\d{1,2}(?![\d.])", line)]
        if not nums:
            continue
        nums = nums[:5]
        if len(nums) < 2:
            continue
        label = re.sub(r"(?<![\d.])\d{1,2}(?![\d.])", " ", line)
        label = re.sub(r"\s+", " ", label).strip(" -–—;:")
        items.append({
            "values": nums,
            "display": format_strings(nums, compact=False),
            "compact_display": format_strings(nums, compact=True),
            "applies_to": label or None,
            "raw": line,
        })
    return items


def attach_strings_to_works(works, string_items):
    for item in string_items:
        label = item["applies_to"]
        if not label:
            continue
        tokens = [key_text(x) for x in re.split(r"[/,]+", label) if key_text(x)]
        for w in works:
            hay = key_text((w.get("composer") or "") + " " + (w.get("title") or ""))
            if any(t and (t in hay or any(part in hay for part in t.split())) for t in tokens):
                w["string_distribution"] = item


def parse_rehearsals(lines):
    result = []
    current = None
    day_re = re.compile(
        r"^(Luni|Marți|Marti|Miercuri|Joi|Vineri|Sâmbătă|Sambata|Duminică|Duminica)"
        r"(?:\s*[-–—]\s*(Luni|Marți|Marti|Miercuri|Joi|Vineri|Sâmbătă|Sambata|Duminică|Duminica))?"
        r"(\s*(?:\([^)]*\)|\d{2}\.\d{2}(?:-\d{2}\.\d{2})?)?)\s*:?\s*(.*)$",
        re.I,
    )
    for line in lines:
        line = line.strip()
        if not line:
            continue
        m = day_re.match(line)
        if m:
            d1, d2, annotation, rest = m.groups()
            day = WEEKDAYS.get(key_text(d1), d1)
            if d2:
                day = f"{day}–{WEEKDAYS.get(key_text(d2), d2)}"
            current = {"day": day, "annotation": annotation.strip() or None, "lines": []}
            result.append(current)
            if rest.strip():
                current["lines"].append({
                    "raw": rest.strip(),
                    "display": normalize_time_ranges(rest.strip()),
                    "time_ranges": [normalize_time_ranges(x.group(0)) for x in _TIME_RANGE_RE.finditer(rest.strip())],
                })
        else:
            if current is None:
                current = {"day": None, "annotation": None, "lines": []}
                result.append(current)
            current["lines"].append({
                "raw": line,
                "display": normalize_time_ranges(line),
                "time_ranges": [normalize_time_ranges(x.group(0)) for x in _TIME_RANGE_RE.finditer(line)],
            })
    return result


def explicit_concert_time(date_info, program):
    if date_info.get("explicit_time"):
        return date_info["explicit_time"]
    p = clean_text(program)
    m = re.search(r"concert\s*(\d{1,2})(?::(\d{2}))?\s*[-–—]\s*\d{1,2}", p, re.I)
    if not m:
        m = re.search(r"(\d{1,2})(?::(\d{2}))?\s*[-–—]\s*\d{1,2}\s*concert", p, re.I)
    if m:
        return f"{int(m.group(1)):02d}:{int(m.group(2) or 0):02d}"
    return None


def week_info(iso_date):
    d = date.fromisoformat(iso_date)
    monday = d - timedelta(days=d.weekday())
    sunday = monday + timedelta(days=6)
    ro_months = {1:"ianuarie",2:"februarie",3:"martie",4:"aprilie",5:"mai",6:"iunie",7:"iulie",8:"august",9:"septembrie",10:"octombrie",11:"noiembrie",12:"decembrie"}
    if monday.month == sunday.month:
        label = f"{monday.day}–{sunday.day} {ro_months[sunday.month]}"
    else:
        label = f"{monday.day} {ro_months[monday.month]} – {sunday.day} {ro_months[sunday.month]}"
    return {
        "start": monday.isoformat(),
        "end": sunday.isoformat(),
        "label": label,
        "collapsed_label": f"{sunday.day} {ro_months[sunday.month]}",
        "key": monday.isoformat(),
    }


def parse_event(row, month_num, month_name, year, same_date_occurrence):
    cells = (row.get("cells") or []) + ["", "", "", ""]
    raw_date, raw_conductor, raw_soloist, raw_program = [clean_text(x) for x in cells[:4]]
    event_type = classify_event(raw_date, raw_conductor, raw_program)
    date_info = parse_date_cell(raw_date, month_num, year, event_type)
    conductor = parse_conductor(raw_conductor)
    soloists = parse_soloists(raw_soloist)
    program_lines, cord_lines, rep_lines = split_sections(raw_program)
    works, program_notes = parse_works(program_lines)
    string_distributions = parse_strings(cord_lines)
    attach_strings_to_works(works, string_distributions)
    rehearsals = parse_rehearsals(rep_lines)
    if not raw_conductor and not raw_soloist and not raw_program:
        status = "uncompleted"
    elif not raw_program:
        status = "partial"
    else:
        status = "complete"
    ctime = explicit_concert_time(date_info, raw_program)
    if not ctime and event_type == "orchestra" and (date_info["dates"] or date_info["candidate_dates"]):
        ctime = "19:00"
    concerts = []
    if date_info["dates"]:
        for d in date_info["dates"]:
            concerts.append({
                "date": d,
                "time": ctime,
                "time_source": "explicit" if explicit_concert_time(date_info, raw_program) else ("default" if ctime else None),
                "uncertain": False,
            })
    elif date_info["candidate_dates"]:
        concerts.append({
            "date": None,
            "candidate_dates": date_info["candidate_dates"],
            "raw_date": date_info["raw"],
            "time": ctime,
            "time_source": "explicit" if explicit_concert_time(date_info, raw_program) else ("default" if ctime else None),
            "uncertain": True,
        })
    date_key = re.sub(r"[^0-9+?]+", "-", raw_date.splitlines()[0] if raw_date else "undated").strip("-")
    event_id = f"{year}-{month_num:02d}-{date_key or 'undated'}-{event_type}-{same_date_occurrence}"
    basis_date = date_info["dates"][0] if date_info["dates"] else (date_info["candidate_dates"][0] if date_info["candidate_dates"] else None)
    wi = week_info(basis_date) if basis_date else None
    search = " ".join([
        raw_date, raw_conductor, raw_soloist, raw_program,
        " ".join((w.get("composer") or "") + " " + (w.get("title") or "") for w in works),
    ])
    return {
        "id": event_id,
        "type": event_type,
        "month": month_num,
        "month_name": month_name,
        "year": year,
        "date": date_info,
        "concerts": concerts,
        "week": wi,
        "venue": date_info.get("venue"),
        "conductor": conductor,
        "soloists": soloists,
        "works": works,
        "program_notes": program_notes,
        "string_distributions": string_distributions,
        "rehearsals": rehearsals,
        "program_status": status,
        "search_text": key_text(search),
        "raw": {"date": raw_date, "conductor": raw_conductor, "soloist": raw_soloist, "program": raw_program},
    }


def parse_program(raw_doc):
    title = raw_doc.get("title", "")
    months = []
    all_events = []
    for table_index, table in enumerate(raw_doc.get("tables", []), start=1):
        rows = table.get("rows", [])
        header = rows[0]["cells"][0] if rows and rows[0].get("cells") else ""
        month_num, month_name, year = month_meta(header, table_index)
        events = []
        occurrence = {}
        for row in rows[1:]:
            cells = row.get("cells") or []
            raw_date = clean_text(cells[0] if cells else "")
            raw_conductor = clean_text(cells[1] if len(cells) > 1 else "")
            raw_program = clean_text(cells[3] if len(cells) > 3 else "")
            event_type = classify_event(raw_date, raw_conductor, raw_program)
            first = raw_date.splitlines()[0] if raw_date else "undated"
            occ_key = key_text(first) + "|" + event_type
            occurrence[occ_key] = occurrence.get(occ_key, 0) + 1
            ev = parse_event(row, month_num, month_name, year, occurrence[occ_key])
            events.append(ev)
            all_events.append(ev)
        orchestra = [e for e in events if e["type"] == "orchestra"]
        recitals = [e for e in events if e["type"] == "recital"]
        weeks_map = {}
        undated = []
        for e in orchestra:
            if e["week"]:
                wk = e["week"]["key"]
                if wk not in weeks_map:
                    weeks_map[wk] = {
                        "key": wk,
                        "start": e["week"]["start"],
                        "end": e["week"]["end"],
                        "label": e["week"]["label"],
                        "collapsed_label": e["week"]["collapsed_label"],
                        "events": [],
                    }
                weeks_map[wk]["events"].append(e)
            else:
                undated.append(e)
        weeks = [weeks_map[k] for k in sorted(weeks_map)]
        if undated:
            weeks.append({
                "key": f"{year}-{month_num:02d}-undated",
                "start": None,
                "end": None,
                "label": "Dată neclară",
                "collapsed_label": "Dată neclară",
                "events": undated,
            })
        months.append({
            "month": month_num,
            "name": month_name,
            "year": year,
            "label": f"{month_name.upper()} {year}",
            "weeks": weeks,
            "tmc_recitals": recitals,
        })
    return {"title": title, "season": "2026/2027", "months": months, "event_count": len(all_events)}