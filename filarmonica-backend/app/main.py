import os
from collections import Counter
from fastapi import FastAPI, HTTPException
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError

app = FastAPI(title="Stagiune Filarmonica Transilvania API", version="0.1.0")
DOC_ID = os.getenv("GOOGLE_DOC_ID", "101B96OK81QjVMb_dzNQwHaCIwLA2hzA0vUBD31ldW74")
CREDS_PATH = os.getenv("GOOGLE_APPLICATION_CREDENTIALS", "/secrets/google.json")
SCOPES = ["https://www.googleapis.com/auth/documents.readonly"]

def get_docs_service():
    if not os.path.exists(CREDS_PATH):
        raise FileNotFoundError(f"Cheia Google nu exista la {CREDS_PATH}")
    creds = service_account.Credentials.from_service_account_file(CREDS_PATH, scopes=SCOPES)
    return build("docs", "v1", credentials=creds, cache_discovery=False)

def cell_text(cell):
    parts=[]
    for item in cell.get("content", []):
        paragraph=item.get("paragraph")
        if not paragraph:
            continue
        p="".join(el.get("textRun",{}).get("content","") for el in paragraph.get("elements",[])).strip()
        if p:
            parts.append(p)
    return "\n".join(parts).strip()

@app.get("/")
def root():
    return {"app":"Stagiune Filarmonica Transilvania","status":"running","next":["/health","/doc-info","/raw-program"]}

@app.get("/health")
def health():
    return {"ok": True}

@app.get("/doc-info")
def doc_info():
    try:
        doc=get_docs_service().documents().get(documentId=DOC_ID).execute()
    except FileNotFoundError as e:
        raise HTTPException(status_code=500, detail=str(e))
    except HttpError as e:
        raise HTTPException(status_code=e.resp.status, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    content=doc.get("body",{}).get("content",[])
    kinds=Counter("TABLE" if "table" in x else "PARAGRAPH" if "paragraph" in x else "SECTION" if "sectionBreak" in x else "OTHER" for x in content)
    tables=[x["table"] for x in content if "table" in x]
    info=[]
    for idx,t in enumerate(tables,1):
        rows=t.get("tableRows",[])
        first=[cell_text(c) for c in rows[0].get("tableCells",[])] if rows else []
        info.append({"table":idx,"rows":len(rows),"columns":max((len(r.get("tableCells",[])) for r in rows), default=0),"header":first})
    return {"title":doc.get("title"),"elements":len(content),"types":dict(kinds),"tables":info}

@app.get("/raw-program")
def raw_program():
    try:
        doc=get_docs_service().documents().get(documentId=DOC_ID).execute()
    except FileNotFoundError as e:
        raise HTTPException(status_code=500, detail=str(e))
    except HttpError as e:
        raise HTTPException(status_code=e.resp.status, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    result=[]
    for table_index, element in enumerate([x for x in doc.get("body",{}).get("content",[]) if "table" in x],1):
        rows_out=[]
        for row_index,row in enumerate(element["table"].get("tableRows",[]),1):
            rows_out.append({"row":row_index,"cells":[cell_text(c) for c in row.get("tableCells",[])]})
        result.append({"table":table_index,"rows":rows_out})
    return {"title":doc.get("title"),"tables":result}
