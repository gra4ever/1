import json
from urllib.parse import parse_qs

from fastapi import HTTPException, Query, Request
from fastapi.responses import HTMLResponse, RedirectResponse, JSONResponse
from pydantic import BaseModel

from . import main_v07 as v7
from .access_control import (
    admin_authorized,
    admin_set_status,
    client_ip,
    device_activity,
    device_by_token,
    esc,
    get_device,
    list_devices,
    log_activity,
    public_device,
    request_access,
    set_notifications,
)

base = v7.v6.v5.base
base.VERSION = "0.8.0"
base.app.version = base.VERSION
app = base.app


class AccessRequest(BaseModel):
    device_id: str
    device_token: str
    name: str
    email: str
    manufacturer: str = ""
    model: str = ""
    device_name: str = ""
    android_version: str = ""
    app_version: str = ""


class NotificationPreference(BaseModel):
    enabled: bool


def bearer_token(request: Request):
    auth = request.headers.get("authorization", "")
    if auth.lower().startswith("bearer "):
        return auth[7:].strip()
    return request.headers.get("x-device-token", "").strip()


def device_for_request(request: Request, approved_only=True):
    token = bearer_token(request)
    if not token:
        raise HTTPException(status_code=401, detail="Dispozitiv neautorizat")
    row = device_by_token(
        token,
        touch=True,
        ip=client_ip(request),
        user_agent=request.headers.get("user-agent", ""),
    )
    if not row:
        raise HTTPException(status_code=401, detail="Dispozitiv necunoscut")
    if approved_only and row["status"] != "approved":
        raise HTTPException(status_code=403, detail=f"Acces {row['status']}")
    return row


PUBLIC_PATHS = {
    "/",
    "/health",
    "/api/access/request",
    "/api/access/status",
}


@app.middleware("http")
async def protect_private_program(request: Request, call_next):
    path = request.url.path

    if path.startswith("/admin"):
        return await call_next(request)

    if path in PUBLIC_PATHS:
        return await call_next(request)

    if path.startswith("/docs") or path.startswith("/redoc") or path == "/openapi.json":
        return JSONResponse(status_code=404, content={"detail": "Not found"})

    protected = (
        path.startswith("/api/")
        or path in {"/doc-info", "/raw-program"}
    )
    if not protected:
        return await call_next(request)

    try:
        row = device_for_request(request, approved_only=True)
        request.state.device = row
    except HTTPException as e:
        return JSONResponse(status_code=e.status_code, content={"detail": e.detail})

    response = await call_next(request)

    if path == "/api/search" and response.status_code < 400:
        q = request.query_params.get("q", "")
        if q:
            log_activity(row["id"], "search", q, client_ip(request))
    return response


@app.post("/api/access/request")
def access_request(payload: AccessRequest, request: Request):
    try:
        row = request_access(
            device_id=payload.device_id,
            device_token=payload.device_token,
            name=payload.name,
            email=payload.email,
            manufacturer=payload.manufacturer,
            model=payload.model,
            device_name=payload.device_name,
            android_version=payload.android_version,
            app_version=payload.app_version,
            ip=client_ip(request),
            user_agent=request.headers.get("user-agent", ""),
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    return {
        "ok": True,
        "status": row["status"],
        "message": "Cererea a fost trimisă" if row["status"] == "pending" else "Cererea există deja",
    }


@app.get("/api/access/status")
def access_status(request: Request):
    row = device_for_request(request, approved_only=False)
    return {
        "ok": True,
        "status": row["status"],
        "approved": row["status"] == "approved",
        "device": public_device(row),
    }


@app.post("/api/activity/open")
def activity_open(request: Request):
    row = getattr(request.state, "device", None) or device_for_request(request)
    log_activity(row["id"], "app_open", "", client_ip(request))
    return {"ok": True}


@app.post("/api/activity/event-open")
def activity_event_open(request: Request, event_id: str = Query(..., min_length=1, max_length=220)):
    row = getattr(request.state, "device", None) or device_for_request(request)
    log_activity(row["id"], "event_open", event_id, client_ip(request))
    return {"ok": True}


@app.get("/api/preferences")
def preferences(request: Request):
    row = getattr(request.state, "device", None) or device_for_request(request)
    return {"notifications_enabled": bool(row["notifications_enabled"])}


@app.post("/api/preferences/notifications")
def preferences_notifications(payload: NotificationPreference, request: Request):
    row = getattr(request.state, "device", None) or device_for_request(request)
    enabled = set_notifications(row["id"], payload.enabled)
    log_activity(
        row["id"],
        "notifications",
        "enabled" if enabled else "disabled",
        client_ip(request),
    )
    return {"ok": True, "notifications_enabled": enabled}


def admin_guard(request: Request):
    if not admin_authorized(request):
        raise HTTPException(status_code=403, detail="Admin access denied")


def _status_badge(status):
    labels = {
        "pending": "În așteptare",
        "approved": "Aprobat",
        "denied": "Respins",
        "revoked": "Revocat",
    }
    return labels.get(status, status)


def admin_page():
    rows = list_devices()
    table_rows = []
    for d in rows:
        actions = []
        if d["status"] != "approved":
            actions.append(f'<button name="action" value="approve">Aprobă</button>')
        if d["status"] != "denied":
            actions.append(f'<button class="secondary" name="action" value="deny">Respinge</button>')
        if d["status"] == "approved":
            actions.append(f'<button class="danger" name="action" value="revoke">Revocă</button>')
        if d["status"] in {"denied", "revoked"}:
            actions.append(f'<button class="secondary" name="action" value="pending">Pune în așteptare</button>')

        phone = " ".join(x for x in [d.get("manufacturer"), d.get("model")] if x).strip() or d.get("device_name") or "—"
        last_search = d.get("last_search") or "—"
        table_rows.append(
            f"""
            <tr>
              <td><a href="/admin/device/{d['id']}"><strong>{esc(d['name'])}</strong></a><br><span>{esc(d['email'])}</span></td>
              <td>{esc(phone)}<br><span>Android {esc(d.get('android_version'))} · App {esc(d.get('app_version'))}</span></td>
              <td><strong>{esc(_status_badge(d['status']))}</strong><br><span>Cerere: {esc(d.get('requested_at'))}</span></td>
              <td>{esc(d.get('last_seen_at') or '—')}<br><span>IP: {esc(d.get('last_ip') or '—')}</span></td>
              <td>{esc(last_search)}</td>
              <td>
                <form method="post" action="/admin/action">
                  <input type="hidden" name="device_id" value="{d['id']}">
                  {' '.join(actions)}
                </form>
              </td>
            </tr>
            """
        )

    body = "".join(table_rows) or '<tr><td colspan="6">Nu există încă solicitări.</td></tr>'
    return f"""<!doctype html>
<html lang="ro"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Stagiune FST · Admin</title>
<style>
body{{font-family:system-ui,-apple-system,sans-serif;background:#f5f1e8;color:#102d52;margin:0}}
header{{background:#102d52;color:#fff;padding:18px 24px}} h1{{margin:0;font-size:22px}}
main{{padding:20px;overflow:auto}} table{{width:100%;border-collapse:collapse;background:#fff;border-radius:14px;overflow:hidden}}
th,td{{padding:13px 12px;border-bottom:1px solid #e7e0d3;text-align:left;vertical-align:top;font-size:14px}}
th{{background:#eee6d6;color:#102d52}} span{{color:#6c7480;font-size:12px}}
a{{color:#102d52}} button{{border:0;border-radius:9px;padding:8px 10px;margin:2px;background:#102d52;color:#fff;cursor:pointer}}
button.secondary{{background:#8a7342}} button.danger{{background:#9e3b3b}}
</style></head><body><header><h1>Stagiune FST · Administrare acces</h1></header>
<main><table><thead><tr><th>Persoană</th><th>Telefon</th><th>Status</th><th>Ultima accesare</th><th>Ultima căutare</th><th>Acțiuni</th></tr></thead>
<tbody>{body}</tbody></table></main></body></html>"""


@app.get("/admin", response_class=HTMLResponse)
def admin_home(request: Request):
    admin_guard(request)
    return HTMLResponse(admin_page())


@app.post("/admin/action")
async def admin_action(request: Request):
    admin_guard(request)
    raw = (await request.body()).decode("utf-8", errors="replace")
    form = parse_qs(raw)
    try:
        device_id = int(form.get("device_id", [""])[0])
    except ValueError:
        raise HTTPException(status_code=400, detail="device_id invalid")
    action = form.get("action", [""])[0]
    if not admin_set_status(device_id, action):
        raise HTTPException(status_code=400, detail="Acțiune invalidă")
    return RedirectResponse(url="/admin", status_code=303)


@app.get("/admin/device/{device_id}", response_class=HTMLResponse)
def admin_device(device_id: int, request: Request):
    admin_guard(request)
    d = get_device(device_id)
    if not d:
        raise HTTPException(status_code=404, detail="Dispozitiv inexistent")
    activity = device_activity(device_id)
    lines = []
    labels = {
        "app_open": "Deschidere aplicație",
        "search": "Căutare",
        "event_open": "Concert deschis",
        "notifications": "Notificări",
    }
    for a in activity:
        lines.append(
            f"<tr><td>{esc(a['created_at'])}</td><td>{esc(labels.get(a['action'], a['action']))}</td><td>{esc(a.get('detail'))}</td><td>{esc(a.get('ip'))}</td></tr>"
        )
    phone = " ".join(x for x in [d.get("manufacturer"), d.get("model")] if x).strip() or d.get("device_name") or "—"
    return HTMLResponse(f"""<!doctype html><html lang="ro"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>{esc(d['name'])} · Stagiune FST</title><style>
body{{font-family:system-ui,-apple-system,sans-serif;background:#f5f1e8;color:#102d52;margin:0;padding:20px}} .card{{background:#fff;border-radius:14px;padding:18px;margin-bottom:16px}}
table{{width:100%;border-collapse:collapse;background:#fff}}th,td{{padding:10px;border-bottom:1px solid #e7e0d3;text-align:left;font-size:14px}}a{{color:#102d52}}
</style></head><body><p><a href="/admin">← Înapoi</a></p><div class="card"><h2>{esc(d['name'])}</h2><p>{esc(d['email'])}</p>
<p><strong>{esc(phone)}</strong> · Android {esc(d.get('android_version'))} · App {esc(d.get('app_version'))}</p>
<p>Status: <strong>{esc(_status_badge(d['status']))}</strong><br>Cerere: {esc(d.get('requested_at'))}<br>Aprobat: {esc(d.get('approved_at') or '—')}<br>Ultima accesare: {esc(d.get('last_seen_at') or '—')}<br>Deschideri aplicație: {esc(d.get('open_count'))}<br>Notificări: {'Pornite' if d.get('notifications_enabled') else 'Oprite'}</p></div>
<table><thead><tr><th>Data/ora</th><th>Acțiune</th><th>Detaliu</th><th>IP</th></tr></thead><tbody>{''.join(lines) or '<tr><td colspan="4">Fără activitate.</td></tr>'}</tbody></table></body></html>""")
