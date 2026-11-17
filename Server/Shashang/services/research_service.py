"""
Research tools: projects, papers, analysis, comparison, export, writing
feedback, thesis milestones and lecture summaries.

    research_projects: user_id, title, kind (THESIS|PROJECT|COURSEWORK), description,
                       due_date, is_deleted, created_at, updated_at
    papers:            user_id, project_id, title, authors[], year, venue, doi, url, type,
                       volume, issue, pages, publisher, notes, summary{…}, status
                       (NEW|ANALYSED|NO_TEXT), source (UPLOAD|MANUAL), storage_path,
                       file_name, is_deleted, created_at, updated_at, analysed_at
    paper_texts:       id = paper id; user_id, text (extracted, capped)
    thesis_milestones: user_id, project_id, title, due_at, status (TODO|IN_PROGRESS|DONE),
                       notes, template, is_deleted, created_at
"""

import logging
import re
import time
from datetime import date, datetime, timedelta, timezone
from typing import Callable, Dict, List, Optional, Tuple

from google.cloud.firestore_v1.base_query import FieldFilter

from app.ai_framework.workflows import research_workflows as rw
from app.models.course import CourseMaterial
from app.services import citation_service, course_service, deadline_service, material_extraction
from app.services.course_service import ServiceError
from app.services.firebase_service import get_firestore_client

logger = logging.getLogger(__name__)

PROJECTS = "research_projects"
PAPERS = "papers"
TEXTS = "paper_texts"
MILESTONES = "thesis_milestones"

PROJECT_KINDS = ("THESIS", "PROJECT", "COURSEWORK")
PAPER_TYPES = ("ARTICLE", "CONFERENCE", "BOOK", "THESIS", "PREPRINT", "OTHER")
MILESTONE_STATUSES = ("TODO", "IN_PROGRESS", "DONE")
PAPER_MIMES = ("application/pdf", "text/plain", "text/markdown")
MAX_PAPER_BYTES = 30 * 1024 * 1024
MAX_TEXT_CHARS = 200_000
MIN_WRITING_CHARS, MAX_WRITING_CHARS = 200, 60_000
MAX_COMPARE = 8

TEMPLATES = {
    "THESIS": [
        ("Research proposal approved", 0.10), ("Literature review drafted", 0.25), ("Methodology finalised", 0.35),
        ("Data collection complete", 0.55), ("Analysis complete", 0.70), ("Full first draft", 0.85),
        ("Supervisor feedback addressed", 0.93), ("Final submission", 1.0),
    ],
    "PROJECT": [
        ("Scope agreed with supervisor", 0.10), ("Background research done", 0.30), ("Build / experiments complete", 0.60),
        ("Results written up", 0.80), ("Report draft", 0.90), ("Submission", 1.0),
    ],
    "COURSEWORK": [
        ("Topic chosen", 0.15), ("Sources gathered", 0.35), ("Outline", 0.50), ("First draft", 0.75), ("Revised draft", 0.90), ("Submit", 1.0),
    ],
}


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _iso(value):
    return value.isoformat() if hasattr(value, "isoformat") else value


def _db():
    return get_firestore_client()


def _owned(collection: str, doc_id: str, user_id: str, label: str) -> Tuple[object, Dict]:
    if not doc_id or not isinstance(doc_id, str):
        raise ServiceError(f"{label} not found", 404)
    ref = _db().collection(collection).document(doc_id)
    snap = ref.get()
    d = snap.to_dict() if snap.exists else None
    if not d or d.get("user_id") != user_id or d.get("is_deleted"):
        raise ServiceError(f"{label} not found", 404)
    return ref, d


def _mine(collection: str, user_id: str, **equals) -> List[Tuple[str, Dict]]:
    q = _db().collection(collection).where(filter=FieldFilter("user_id", "==", user_id))
    for k, v in equals.items():
        q = q.where(filter=FieldFilter(k, "==", v))
    return [(d.id, d.to_dict() or {}) for d in q.stream() if not (d.to_dict() or {}).get("is_deleted")]


def _profile_level(user_id: str) -> Optional[str]:
    snap = _db().collection("users").document(user_id).get()
    return ((snap.to_dict() or {}).get("student_profile") or {}).get("degree_level") if snap.exists else None


def _log(task: str, user_id: str, success: bool, started: float, error: Optional[str], **metrics):
    try:
        from app.ai_framework.utils.model_router import model_for
        from app.services.ai_workflow_logger import AIWorkflowLogger

        AIWorkflowLogger().log_workflow(
            workflow_type=task,
            model=model_for(task),
            prompt={"template_version": "1.0"},
            input_data={"parameters": {}},
            response={"success": success, "error_message": error},
            metrics={"latency_ms": int((time.time() - started) * 1000), **metrics},
            user_id=user_id,
        )
    except Exception as e:
        logger.warning(f"Could not log {task}: {e}")


# --- projects ----------------------------------------------------------------------------------------------


def project_response(pid: str, d: Dict, counts: Optional[Dict] = None) -> Dict:
    return {
        "id": pid,
        "title": d.get("title"),
        "kind": d.get("kind"),
        "description": d.get("description"),
        "due_date": d.get("due_date"),
        "paper_count": (counts or {}).get("papers", 0),
        "milestones_done": (counts or {}).get("done", 0),
        "milestones_total": (counts or {}).get("total", 0),
        "created_at": _iso(d.get("created_at")),
    }


def _project_fields(data: Dict, creating: bool) -> Dict:
    out: Dict = {}
    if creating or "title" in data:
        out["title"] = course_service._text(data, "title", 200, required=True)
    if creating or "kind" in data:
        kind = data.get("kind", "THESIS")
        if kind not in PROJECT_KINDS:
            raise ServiceError(f"kind must be one of: {', '.join(PROJECT_KINDS)}")
        out["kind"] = kind
    if "description" in data:
        out["description"] = course_service._text(data, "description", 2000)
    if "due_date" in data:
        out["due_date"] = course_service._iso_date(data.get("due_date"), "due_date")
    return out


def create_project(user_id: str, data: Dict) -> Dict:
    fields = _project_fields(data, creating=True)
    now = _now()
    doc = {"user_id": user_id, "description": None, "due_date": None, **fields, "is_deleted": False, "created_at": now, "updated_at": now}
    ref = _db().collection(PROJECTS).document()
    ref.set(doc)
    return project_response(ref.id, doc)


def list_projects(user_id: str) -> List[Dict]:
    papers = _mine(PAPERS, user_id)
    milestones = _mine(MILESTONES, user_id)
    out = []
    for pid, d in _mine(PROJECTS, user_id):
        ms = [m for _, m in milestones if m.get("project_id") == pid]
        counts = {"papers": sum(1 for _, p in papers if p.get("project_id") == pid), "total": len(ms), "done": sum(1 for m in ms if m.get("status") == "DONE")}
        out.append(project_response(pid, d, counts))
    out.sort(key=lambda p: p["created_at"] or "", reverse=True)
    return out


def get_project(user_id: str, project_id: str) -> Dict:
    _, d = _owned(PROJECTS, project_id, user_id, "Project")
    return project_response(project_id, d)


def update_project(user_id: str, project_id: str, data: Dict) -> Dict:
    ref, d = _owned(PROJECTS, project_id, user_id, "Project")
    fields = _project_fields(data, creating=False)
    if not fields:
        raise ServiceError("Nothing to update")
    fields["updated_at"] = _now()
    ref.update(fields)
    d.update(fields)
    return project_response(project_id, d)


def delete_project(user_id: str, project_id: str) -> None:
    ref, _ = _owned(PROJECTS, project_id, user_id, "Project")
    ref.update({"is_deleted": True, "updated_at": _now()})


# --- papers --------------------------------------------------------------------------------------------------


def paper_response(pid: str, d: Dict) -> Dict:
    return {
        "id": pid,
        "project_id": d.get("project_id"),
        "title": d.get("title"),
        "authors": d.get("authors") or [],
        "year": d.get("year"),
        "venue": d.get("venue"),
        "doi": d.get("doi"),
        "url": d.get("url"),
        "type": d.get("type") or "ARTICLE",
        "volume": d.get("volume"),
        "issue": d.get("issue"),
        "pages": d.get("pages"),
        "publisher": d.get("publisher"),
        "notes": d.get("notes"),
        "summary": d.get("summary"),
        "status": d.get("status"),
        "source": d.get("source"),
        "file_name": d.get("file_name"),
        "has_text": d.get("status") in ("NEW", "ANALYSED") and d.get("source") == "UPLOAD",
        "citation": rw.in_text_citation(d),
        "created_at": _iso(d.get("created_at")),
    }


def _paper_fields(user_id: str, data: Dict, creating: bool) -> Dict:
    out: Dict = {}
    if "project_id" in data or creating:
        pid = data.get("project_id")
        if pid:
            _owned(PROJECTS, pid, user_id, "Project")
        out["project_id"] = pid or None
    if "title" in data or (creating and data.get("source") != "UPLOAD"):
        out["title"] = course_service._text(data, "title", 500, required=creating and data.get("source") != "UPLOAD")
    if "authors" in data:
        authors = data["authors"]
        if not isinstance(authors, list) or len(authors) > 100 or not all(isinstance(a, str) for a in authors):
            raise ServiceError("authors must be a list of names")
        out["authors"] = [" ".join(a.split())[:120] for a in authors if a.strip()]
    if "year" in data:
        y = data["year"]
        if y is not None and (isinstance(y, bool) or not isinstance(y, int) or not 1500 <= y <= date.today().year + 1):
            raise ServiceError("year must be a four-digit year")
        out["year"] = y
    if "type" in data:
        if data["type"] not in PAPER_TYPES:
            raise ServiceError(f"type must be one of: {', '.join(PAPER_TYPES)}")
        out["type"] = data["type"]
    if "doi" in data:
        doi = course_service._text(data, "doi", 200)
        if doi and not citation_service.doi_url(doi):
            raise ServiceError("doi must look like 10.1234/abcd")
        out["doi"] = doi
    if "url" in data:
        url = course_service._text(data, "url", 500)
        if url and not re.match(r"^https?://", url):
            raise ServiceError("url must start with http:// or https://")
        out["url"] = url
    for field, limit in (("venue", 300), ("volume", 20), ("issue", 20), ("pages", 30), ("publisher", 200), ("notes", 5000)):
        if field in data:
            out[field] = course_service._text(data, field, limit)
    return out


def create_paper(user_id: str, data: Dict) -> Dict:
    fields = _paper_fields(user_id, {**data, "source": "MANUAL"}, creating=True)
    now = _now()
    doc = {"user_id": user_id, "authors": [], "type": "ARTICLE", **fields, "summary": None, "status": "NEW", "source": "MANUAL", "is_deleted": False, "created_at": now, "updated_at": now}
    ref = _db().collection(PAPERS).document()
    ref.set(doc)
    return paper_response(ref.id, doc)


def _upload(path: str, data: bytes, mime: str):
    from app.services.firebase_service import get_storage_bucket

    get_storage_bucket().blob(path).upload_from_string(data, content_type=mime)  # private


def upload_paper(user_id: str, filename: str, data: bytes, declared_mime: Optional[str], project_id: Optional[str], upload: Optional[Callable] = None) -> Dict:
    if not data:
        raise ServiceError("The file is empty")
    if len(data) > MAX_PAPER_BYTES:
        raise ServiceError("Papers must be 30 MB or smaller", 413)
    mime = material_extraction.mime_for_filename(filename, declared_mime)
    if mime not in PAPER_MIMES:
        raise ServiceError("Upload a PDF or a text file")
    if project_id:
        _owned(PROJECTS, project_id, user_id, "Project")
    try:
        text = "\n".join(s.text for s in material_extraction.extract_segments(data, mime))
    except Exception as e:
        logger.info(f"Paper text extraction failed: {e}")
        text = ""
    now = _now()
    ref = _db().collection(PAPERS).document()
    safe = re.sub(r"[^A-Za-z0-9._-]+", "_", filename or "paper").strip("._")[:100] or "paper"
    path = f"users/{user_id}/research/papers/{ref.id}/{safe}"
    (upload or _upload)(path, data, mime)
    title = re.sub(r"\.(pdf|txt|md)$", "", filename or "", flags=re.I).replace("_", " ").strip() or "Untitled paper"
    doc = {
        "user_id": user_id, "project_id": project_id or None, "title": title[:500], "authors": [], "type": "ARTICLE",
        "summary": None, "status": "NEW" if text.strip() else "NO_TEXT", "source": "UPLOAD", "storage_path": path,
        "file_name": filename[:200] if filename else None, "mime": mime, "is_deleted": False, "created_at": now, "updated_at": now,
    }
    ref.set(doc)
    if text.strip():
        _db().collection(TEXTS).document(ref.id).set({"user_id": user_id, "text": text[:MAX_TEXT_CHARS]})
    return paper_response(ref.id, doc)


def list_papers(user_id: str, project_id: Optional[str] = None) -> List[Dict]:
    if project_id:
        _owned(PROJECTS, project_id, user_id, "Project")
    rows = [(i, d) for i, d in _mine(PAPERS, user_id) if not project_id or d.get("project_id") == project_id]
    rows.sort(key=lambda r: citation_service.sort_key(r[1]))
    return [paper_response(i, d) for i, d in rows]


def get_paper(user_id: str, paper_id: str) -> Dict:
    return paper_response(paper_id, _owned(PAPERS, paper_id, user_id, "Paper")[1])


def update_paper(user_id: str, paper_id: str, data: Dict) -> Dict:
    ref, d = _owned(PAPERS, paper_id, user_id, "Paper")
    fields = _paper_fields(user_id, data, creating=False)
    if not fields:
        raise ServiceError("Nothing to update")
    fields["updated_at"] = _now()
    ref.update(fields)
    d.update(fields)
    return paper_response(paper_id, d)


def delete_paper(user_id: str, paper_id: str) -> None:
    ref, _ = _owned(PAPERS, paper_id, user_id, "Paper")
    ref.update({"is_deleted": True, "updated_at": _now()})


def analyse_paper(user_id: str, paper_id: str, analyse: Optional[Callable] = None) -> Dict:
    ref, d = _owned(PAPERS, paper_id, user_id, "Paper")
    snap = _db().collection(TEXTS).document(paper_id).get()
    text = ((snap.to_dict() or {}).get("text") or "") if snap.exists else ""
    if not text.strip():
        raise ServiceError("No text was found in this paper. Upload a text-based PDF, or fill in the details yourself.", 409)
    started = time.time()
    try:
        result = rw.analyse_paper(text, analyse)
    except Exception as e:
        logger.error(f"Paper analysis failed for {paper_id}: {e}", exc_info=True)
        _log(rw.TASK_PAPER, user_id, False, started, str(e)[:300])
        raise ServiceError("Couldn't analyse this paper right now. Please try again later.", 502)
    details = result.details
    updates: Dict = {"summary": result.summary.model_dump(), "status": "ANALYSED", "analysed_at": _now(), "updated_at": _now()}
    # Details the student entered win; the model only fills blanks
    uploaded_name = re.sub(r"\.(pdf|txt|md)$", "", d.get("file_name") or "", flags=re.I).replace("_", " ").strip()
    if details.title and (not d.get("title") or d.get("title") == uploaded_name):
        updates["title"] = details.title.strip()[:500]
    if details.authors and not d.get("authors"):
        updates["authors"] = [" ".join(a.split())[:120] for a in details.authors if a.strip()][:100]
    if details.year and not d.get("year") and 1500 <= details.year <= date.today().year + 1:
        updates["year"] = details.year
    if details.venue and not d.get("venue"):
        updates["venue"] = details.venue.strip()[:300]
    if details.doi and not d.get("doi") and citation_service.doi_url(details.doi):
        updates["doi"] = details.doi.strip()[:200]
    if details.type and details.type.upper() in PAPER_TYPES and d.get("type", "ARTICLE") == "ARTICLE":
        updates["type"] = details.type.upper()
    ref.update(updates)
    d.update(updates)
    _log(rw.TASK_PAPER, user_id, True, started, None, chars=len(text))
    return paper_response(paper_id, d)


def paper_download(user_id: str, paper_id: str) -> Dict:
    _, d = _owned(PAPERS, paper_id, user_id, "Paper")
    if not d.get("storage_path"):
        raise ServiceError("This paper has no file", 404)
    fake_material = CourseMaterial(storage_path=d["storage_path"], title=d.get("file_name") or d.get("title"))
    from app.services import material_service

    return {"url": material_service.download_url(fake_material), "file_name": d.get("file_name")}


def compare(user_id: str, paper_ids, focus=None, synthesise: Optional[Callable] = None) -> Dict:
    if not isinstance(paper_ids, list) or not 2 <= len(paper_ids) <= MAX_COMPARE or len(set(paper_ids)) != len(paper_ids):
        raise ServiceError(f"Choose 2 to {MAX_COMPARE} different papers")
    if focus is not None and (not isinstance(focus, str) or len(focus) > 200):
        raise ServiceError("focus must be text of at most 200 characters")
    papers = []
    for pid in paper_ids:
        _, d = _owned(PAPERS, pid, user_id, "Paper")
        papers.append({"id": pid, **d})
    missing = [p.get("title") or "Untitled" for p in papers if not p.get("summary")]
    if missing:
        raise ServiceError(f"Analyse these papers first: {', '.join(missing[:3])}", 409)
    table = rw.review_table(papers)
    synthesis, note = None, None
    started = time.time()
    try:
        synthesis = rw.synthesise(papers, (focus or "").strip() or None, synthesise).model_dump()
        _log(rw.TASK_SYNTHESIS, user_id, True, started, None, papers=len(papers))
    except Exception as e:
        logger.error(f"Synthesis failed: {e}", exc_info=True)
        _log(rw.TASK_SYNTHESIS, user_id, False, started, str(e)[:300])
        note = "The comparison table is ready, but the written synthesis isn't available right now."
    return {"columns": ["citation", *rw.REVIEW_COLUMNS], "rows": table, "synthesis": synthesis, "note": note}


def export(user_id: str, fmt: str, project_id: Optional[str] = None, paper_ids: Optional[List[str]] = None) -> Dict:
    if fmt not in ("bibtex", "apa"):
        raise ServiceError("format must be bibtex or apa")
    if paper_ids:
        papers = [{"id": pid, **_owned(PAPERS, pid, user_id, "Paper")[1]} for pid in paper_ids]
    else:
        papers = [{"id": i, **d} for i, d in _mine(PAPERS, user_id) if not project_id or d.get("project_id") == project_id]
        if project_id:
            _owned(PROJECTS, project_id, user_id, "Project")
    return {"format": fmt, "count": len(papers), "text": citation_service.export(papers, fmt)}


# --- writing feedback ---------------------------------------------------------------------------------------------


def writing_feedback(user_id: str, data: Dict, writing_fn: Optional[Callable] = None) -> Dict:
    text = data.get("text")
    kind = data.get("kind", "OTHER")
    if kind not in rw.WRITING_KINDS:
        raise ServiceError(f"kind must be one of: {', '.join(rw.WRITING_KINDS)}")
    if not isinstance(text, str) or len(text.strip()) < MIN_WRITING_CHARS:
        raise ServiceError(f"Paste at least {MIN_WRITING_CHARS} characters of your writing")
    if len(text) > MAX_WRITING_CHARS:
        raise ServiceError(f"Paste at most {MAX_WRITING_CHARS} characters at a time (about 9,000 words)")
    started = time.time()
    try:
        result = rw.writing_feedback(text, kind, _profile_level(user_id), writing_fn)
    except Exception as e:
        logger.error(f"Writing feedback failed: {e}", exc_info=True)
        _log(rw.TASK_WRITING, user_id, False, started, str(e)[:300])
        raise ServiceError("Couldn't review your writing right now. Please try again later.", 502)
    _log(rw.TASK_WRITING, user_id, True, started, None, chars=len(text))
    return {"kind": kind, **result}


# --- milestones ---------------------------------------------------------------------------------------------------


def milestone_response(mid: str, d: Dict) -> Dict:
    return {"id": mid, "project_id": d.get("project_id"), "title": d.get("title"), "due_at": d.get("due_at"), "status": d.get("status"), "notes": d.get("notes"), "template": d.get("template", False)}


def _milestone_fields(data: Dict, creating: bool) -> Dict:
    out: Dict = {}
    if creating or "title" in data:
        out["title"] = course_service._text(data, "title", 200, required=True)
    if creating or "due_at" in data:
        out["due_at"] = course_service._iso_date(data.get("due_at"), "due_at")
        if creating and not out["due_at"]:
            raise ServiceError("due_at is required")
    if "status" in data:
        if data["status"] not in MILESTONE_STATUSES:
            raise ServiceError(f"status must be one of: {', '.join(MILESTONE_STATUSES)}")
        out["status"] = data["status"]
    if "notes" in data:
        out["notes"] = course_service._text(data, "notes", 2000)
    return out


def list_milestones(user_id: str, project_id: str) -> List[Dict]:
    _owned(PROJECTS, project_id, user_id, "Project")
    rows = [milestone_response(i, d) for i, d in _mine(MILESTONES, user_id, project_id=project_id)]
    rows.sort(key=lambda m: (m["due_at"] or "9999", m["title"] or ""))
    return rows


def create_milestone(user_id: str, project_id: str, data: Dict) -> Dict:
    _owned(PROJECTS, project_id, user_id, "Project")
    fields = _milestone_fields(data, creating=True)
    doc = {"user_id": user_id, "project_id": project_id, "status": "TODO", "notes": None, **fields, "template": False, "is_deleted": False, "created_at": _now()}
    ref = _db().collection(MILESTONES).document()
    ref.set(doc)
    return milestone_response(ref.id, doc)


def update_milestone(user_id: str, milestone_id: str, data: Dict) -> Dict:
    ref, d = _owned(MILESTONES, milestone_id, user_id, "Milestone")
    fields = _milestone_fields(data, creating=False)
    if not fields:
        raise ServiceError("Nothing to update")
    ref.update(fields)
    d.update(fields)
    return milestone_response(milestone_id, d)


def delete_milestone(user_id: str, milestone_id: str) -> None:
    ref, _ = _owned(MILESTONES, milestone_id, user_id, "Milestone")
    ref.update({"is_deleted": True})


def template_dates(kind: str, start: date, due: date) -> List[Tuple[str, str]]:
    span = (due - start).days
    return [(title, (start + timedelta(days=round(fraction * span))).isoformat()) for title, fraction in TEMPLATES[kind]]


def suggest_milestones(user_id: str, project_id: str, data: Dict) -> List[Dict]:
    """Add the template milestones for the project kind, spread from start to due date.
    Replaces earlier template milestones that are still TODO; the student's own ones stay."""
    _, project = _owned(PROJECTS, project_id, user_id, "Project")
    due_value = data.get("due_date") or project.get("due_date")
    due = course_service._iso_date(due_value, "due_date")
    if not due:
        raise ServiceError("Set the project's due date first")
    start_value = data.get("start_date")
    start = date.fromisoformat(course_service._iso_date(start_value, "start_date")) if start_value else date.fromisoformat(deadline_service._now().date().isoformat())
    due_d = date.fromisoformat(due)
    if (due_d - start).days < 7:
        raise ServiceError("The due date must be at least a week away")
    for mid, d in _mine(MILESTONES, user_id, project_id=project_id):
        if d.get("template") and d.get("status") == "TODO":
            _db().collection(MILESTONES).document(mid).update({"is_deleted": True})
    batch = _db().batch()
    created, now = [], _now()
    for title, when in template_dates(project.get("kind", "THESIS"), start, due_d):
        doc = {"user_id": user_id, "project_id": project_id, "title": title, "due_at": when, "status": "TODO", "notes": None, "template": True, "is_deleted": False, "created_at": now}
        ref = _db().collection(MILESTONES).document()
        batch.set(ref, doc)
        created.append(milestone_response(ref.id, doc))
    batch.commit()
    if not project.get("due_date"):
        _db().collection(PROJECTS).document(project_id).update({"due_date": due})
    return created


# --- lecture summaries ------------------------------------------------------------------------------------------------


def summarise_material(user_id: str, material_id: str, lecture_fn: Optional[Callable] = None) -> Dict:
    material = course_service._get_owned(CourseMaterial, material_id, user_id, "Material")
    if material.ingestion_status != "READY":
        raise ServiceError("This file hasn't been processed yet", 409)
    text = deadline_service._material_text(user_id, material.id, max_chars=rw.MAX_PAPER_CHARS)
    if not text.strip():
        raise ServiceError("No text was found in this file", 409)
    course = course_service.get_course(user_id, material.course_id)
    started = time.time()
    try:
        summary = rw.lecture_summary(material.title, text, course.name, _profile_level(user_id), lecture_fn)
    except Exception as e:
        logger.error(f"Lecture summary failed for {material_id}: {e}", exc_info=True)
        _log(rw.TASK_LECTURE, user_id, False, started, str(e)[:300])
        raise ServiceError("Couldn't summarise this file right now. Please try again later.", 502)
    _db().collection(CourseMaterial.collection_name).document(material_id).update({"summary": summary, "summary_at": _now()})
    _log(rw.TASK_LECTURE, user_id, True, started, None, chars=len(text))
    return {"material_id": material_id, "title": material.title, "summary": summary}
