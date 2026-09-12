import os
from collections import Counter
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from fastapi import FastAPI, HTTPException, Query
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from apscheduler.schedulers.background import BackgroundScheduler

from .parser import clean_text, key_text, parse_program
from .snapshot import (
    aggregate_news,
    diff_program,
    load_news,
    load_snapshot,
    load_status,
    save_news,
    save_snapshot,
    save_status,
    utc_now_iso,
)

app = FastAPI(title="Stagiune Filarmonica Transilvania API", version="0.2.0")
DOC_ID = os.getenv("GOOGLE_DOC_ID", "101B96OK81QjVMb_dzNQwHaCIwLA2hzA0vUBD31ldW74")
CREDS_PATH = os.getenv("GOOGLE_APPLICATION_CREDENTIALS", "/secrets/google.json")
SCOPES = ["https://www.googleapis.com/auth/documents.readonly"]
TZ_NAME = os.getenv("TZ", "Europe/Bucharest")
TZ = ZoneInfo(TZ_NAME)
scheduler = BackgroundScheduler(timezone=TZ)


def get_docs_service():
    if not os.path.exists(CREDS_PATH):
        raise FileNotFoundError(f"Cheia Google nu există la {CREDS_PATH}")
    creds = service_account.Credentials.from_service_account_file(CREDS_PATH, scopes=SCOPES)
    return build("docs", "v1", credentials=creds, cache_discovery=False)


def cell_text(cell):
    parts = []
    for item in cell.get("content", []):
        paragraph = item.get("paragraph")
        if not paragraph:
            continue
        p = "".join(el.get("textRun", {}).get("content", "") for el in paragraph.get("elements", [])).strip()
        if p:
            parts.append(p)
    return clean_text("\n".join(parts))


def fetch_document():
    return get_docs_service().documents().get(documentId=DOC_ID).execute()


def fetch_raw_program():
    doc = fetch_document()
    result = []
    for table_index, element in enumerate([x for x in doc.get("body", {}).get("content", []) if "table" in x], 1):
        rows_out = []
        for row_index, row in enumerate(element["table"].get("tableRows", []), 1):
            rows_out.append({"row": row_index, "cells": [cell_text(c) for c in row.get("tableCells", [])]})
        result.append({"table": table_index, "rows": rows_out})
    return {"title": doc.get("title"), "tables": result}


def refresh_snapshot(source="automatic"):
    now = utc_now_iso()
    try:
        raw = fetch_raw_program()
        new_program = parse_program(raw)
        old_program = load_snapshot()
        changes = diff_program(old_program, new_program)
        if old_program is None:
            save_snapshot(new_program)
            save_status({
                "ok": True,
                "last_scan": now,
                "last_change_count": 0,
                "baseline_created": True,
                "source": source,
                "version": "0.2.0",
            })
            return {"ok": True, "baseline_created": True, "change_count": 0, "program": new_program}
        if changes:
            entries = aggregate_news(changes, detected_at=now, source=source)
            save_news(entries + load_news())
        save_snapshot(new_program)
        save_status({
            "ok": True,
            "last_scan": now,
            "last_change_count": len(changes),
            "baseline_created": False,
            "source": source,
            "version": "0.2.0",
        })
        return {
            "ok": True,
            "baseline_created": False,
            "change_count": len(changes),
            "changes": changes,
            "program": new_program,
        }
    except Exception as e:
        save_status({"ok": False, "last_scan": now, "error": str(e), "source": source, "version": "0.2.0"})
        raise


def scheduled_refresh():
    try:
        refresh_snapshot(source="automatic")
    except Exception as e:
        print(f"[scheduler] refresh failed: {e}", flush=True)


@app.on_event("startup")
def startup_event():
    if not scheduler.running:
        scheduler.add_job(scheduled_refresh, "cron", hour=9, minute=0, id="daily_0900", replace_existing=True)
        scheduler.add_job(
            scheduled_refresh,
            "date",
            run_date=datetime.now(TZ) + timedelta(seconds=8),
            id="startup_baseline",
            replace_existing=True,
        )
        scheduler.start()


@app.on_event("shutdown")
def shutdown_event():
    if scheduler.running:
        scheduler.shutdown(wait=False)


@app.get("/")
def root():
    return {
        "app": "Stagiune Filarmonica Transilvania",
        "status": "running",
        "version": "0.2.0",
        "endpoints": [
            "/health", "/doc-info", "/raw-program", "/api/program", "/api/program/live",
            "/api/refresh", "/api/news", "/api/status", "/api/search?q=Mahler",
        ],
    }


@app.get("/health")
def health():
    return {"ok": True, "version": "0.2.0", "scheduler": "09:00 Europe/Bucharest"}


@app.get("/doc-info")
def doc_info():
    try:
        doc = fetch_document()
    except FileNotFoundError as e:
        raise HTTPException(status_code=500, detail=str(e))
    except HttpError as e:
        raise HTTPException(status_code=e.resp.status, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    content = doc.get("body", {}).get("content", [])
    kinds = Counter(
        "TABLE" if "table" in x else "PARAGRAPH" if "paragraph" in x else "SECTION" if "sectionBreak" in x else "OTHER"
        for x in content
    )
    tables = [x["table"] for x in content if "table" in x]
    info = []
    for idx, t in enumerate(tables, 1):
        rows = t.get("tableRows", [])
        first = [cell_text(c) for c in rows[0].get("tableCells", [])] if rows else []
        info.append({
            "table": idx,
            "rows": len(rows),
            "columns": max((len(r.get("tableCells", [])) for r in rows), default=0),
            "header": first,
        })
    return {"title": doc.get("title"), "elements": len(content), "types": dict(kinds), "tables": info}


@app.get("/raw-program")
def raw_program():
    try:
        return fetch_raw_program()
    except FileNotFoundError as e:
        raise HTTPException(status_code=500, detail=str(e))
    except HttpError as e:
        raise HTTPException(status_code=e.resp.status, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/program")
def api_program():
    snap = load_snapshot()
    if snap is not None:
        return snap
    try:
        return refresh_snapshot(source="baseline")["program"]
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/program/live")
def api_program_live():
    try:
        return parse_program(fetch_raw_program())
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/refresh")
@app.get("/api/refresh")
def api_refresh():
    try:
        result = refresh_snapshot(source="manual")
        return {
            "ok": result["ok"],
            "baseline_created": result.get("baseline_created", False),
            "change_count": result.get("change_count", 0),
            "changes": result.get("changes", []),
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/news")
def api_news(limit: int = Query(100, ge=1, le=1000)):
    news = load_news()
    return {"count": min(len(news), limit), "items": news[:limit]}


@app.get("/api/status")
def api_status():
    return {**load_status(), "scheduler_time": "09:00", "timezone": TZ_NAME}


@app.get("/api/search")
def api_search(q: str = Query(..., min_length=1), limit: int = Query(50, ge=1, le=200)):
    needle = key_text(q)
    program = load_snapshot()
    if program is None:
        try:
            program = refresh_snapshot(source="baseline")["program"]
        except Exception as e:
            raise HTTPException(status_code=500, detail=str(e))
    results = []
    for month in program.get("months", []):
        events = []
        for week in month.get("weeks", []):
            events.extend(week.get("events", []))
        events.extend(month.get("tmc_recitals", []))
        for e in events:
            if needle not in e.get("search_text", ""):
                continue
            work_hits = []
            for w in e.get("works", []):
                if needle in key_text((w.get("composer") or "") + " " + (w.get("title") or "")):
                    work_hits.append({"composer": w.get("composer"), "title": w.get("title")})
            results.append({
                "event_id": e.get("id"),
                "type": e.get("type"),
                "date_label": e.get("raw", {}).get("date", ""),
                "month": month.get("month"),
                "month_name": month.get("name"),
                "year": month.get("year"),
                "week": e.get("week"),
                "conductor": e.get("conductor", {}).get("name"),
                "soloists": [s.get("raw") for s in e.get("soloists", [])],
                "works": work_hits or [{"composer": w.get("composer"), "title": w.get("title")} for w in e.get("works", [])],
            })
            if len(results) >= limit:
                return {"query": q, "count": len(results), "items": results}
    return {"query": q, "count": len(results), "items": results}
