"""
Deadlines: exams, assignments, quizzes, labs and presentations.

    deadlines: user_id, course_id (None = not linked to a course), title, type, due_at,
               all_day, weight_pct, notes, source (MANUAL|SYLLABUS|ICAL|DEVICE),
               external_uid, completed, is_deleted, created_at, updated_at

`due_at` is an ISO string: "YYYY-MM-DD" for all-day items or a UTC datetime
"YYYY-MM-DDTHH:MM:SSZ". Both sort correctly as strings. Syllabus deadlines
written by course_structure_generation use the same collection and format.
"""

import ipaddress
import logging
import re
import socket
import time
from datetime import date, datetime, timedelta, timezone
from typing import Callable, Dict, List, Optional, Tuple
from urllib.parse import urlparse

from google.cloud.firestore_v1.base_query import FieldFilter

from app.models.course import MATERIAL_CHUNKS, CourseMaterial
from app.services import course_service
from app.services.course_service import ServiceError
from app.services.firebase_service import get_firestore_client

logger = logging.getLogger(__name__)

DEADLINES = "deadlines"
TYPES = ("EXAM", "ASSIGNMENT", "QUIZ", "LAB", "PRESENTATION", "OTHER")
SOURCES = ("MANUAL", "SYLLABUS", "ICAL", "DEVICE")
MAX_BULK = 200
MAX_ICS_BYTES = 2 * 1024 * 1024
MAX_ICS_EVENTS = 500
FETCH_TIMEOUT = 10
SYLLABUS_TASK = "syllabus_extraction"


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _iso(value):
    return value.isoformat() if hasattr(value, "isoformat") else value


def _db():
    return get_firestore_client()


def normalise_title(title: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", (title or "").lower()).strip()


def deadline_response(deadline_id: str, data: Dict) -> Dict:
    due = data.get("due_at")
    return {
        "id": deadline_id,
        "course_id": data.get("course_id"),
        "title": data.get("title", ""),
        "type": data.get("type", "OTHER"),
        "due_at": due,
        "all_day": data.get("all_day", bool(due) and len(due) == 10),
        "weight_pct": data.get("weight_pct"),
        "notes": data.get("notes"),
        "source": data.get("source", "MANUAL"),
        "completed": data.get("completed", False),
        "created_at": _iso(data.get("created_at")),
    }


# --- validation --------------------------------------------------------------------------------


def parse_due(value, required: bool = True) -> Tuple[Optional[str], bool]:
    """'2026-10-05' → ('2026-10-05', all_day=True); '2026-10-05T14:00:00-04:00' → UTC datetime string."""
    if value in (None, ""):
        if required:
            raise ServiceError("due_at is required")
        return None, True
    if not isinstance(value, str):
        raise ServiceError("due_at must be a date (YYYY-MM-DD) or an ISO 8601 date-time")
    value = value.strip()
    if len(value) == 10:
        try:
            return date.fromisoformat(value).isoformat(), True
        except ValueError:
            raise ServiceError("due_at must be a date (YYYY-MM-DD) or an ISO 8601 date-time")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        raise ServiceError("due_at must be a date (YYYY-MM-DD) or an ISO 8601 date-time")
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"), False


def _fields(user_id: str, data: Dict, creating: bool) -> Dict:
    out: Dict = {}
    if creating or "title" in data:
        out["title"] = course_service._text(data, "title", 200, required=True)
    if creating or "type" in data:
        t = data.get("type", "OTHER")
        if t not in TYPES:
            raise ServiceError(f"type must be one of: {', '.join(TYPES)}")
        out["type"] = t
    if creating or "due_at" in data:
        out["due_at"], out["all_day"] = parse_due(data.get("due_at"), required=creating)
    if "weight_pct" in data:
        w = data["weight_pct"]
        if w is not None and (isinstance(w, bool) or not isinstance(w, (int, float)) or not 0 <= w <= 100):
            raise ServiceError("weight_pct must be between 0 and 100")
        out["weight_pct"] = w
    if "notes" in data:
        out["notes"] = course_service._text(data, "notes", 2000)
    if "completed" in data:
        if not isinstance(data["completed"], bool):
            raise ServiceError("completed must be true or false")
        out["completed"] = data["completed"]
    if creating or "course_id" in data:
        course_id = data.get("course_id")
        if course_id not in (None, ""):
            course_service.get_course(user_id, course_id)
            out["course_id"] = course_id
        else:
            out["course_id"] = None
    return out


# --- CRUD ------------------------------------------------------------------------------------------


def _all(user_id: str) -> List[Tuple[str, Dict]]:
    rows = [(d.id, d.to_dict() or {}) for d in _db().collection(DEADLINES).where(filter=FieldFilter("user_id", "==", user_id)).stream()]
    return [r for r in rows if not r[1].get("is_deleted")]


def _get(user_id: str, deadline_id: str):
    if not deadline_id or not isinstance(deadline_id, str):
        raise ServiceError("Deadline not found", 404)
    ref = _db().collection(DEADLINES).document(deadline_id)
    snap = ref.get()
    data = snap.to_dict() if snap.exists else None
    if not data or data.get("user_id") != user_id or data.get("is_deleted"):
        raise ServiceError("Deadline not found", 404)
    return ref, data


def get_deadline(user_id: str, deadline_id: str) -> Dict:
    return deadline_response(deadline_id, _get(user_id, deadline_id)[1])


def list_deadlines(
    user_id: str,
    course_id: Optional[str] = None,
    date_from: Optional[str] = None,
    date_to: Optional[str] = None,
    include_completed: bool = True,
) -> List[Dict]:
    if course_id:
        course_service.get_course(user_id, course_id)
    for name, value in (("from", date_from), ("to", date_to)):
        if value:
            parse_due(value)
    active = {c.id for c in course_service.list_courses(user_id)}
    out = []
    for i, d in _all(user_id):
        if course_id and d.get("course_id") != course_id:
            continue
        if d.get("course_id") and d["course_id"] not in active:
            continue  # course deleted
        if not include_completed and d.get("completed"):
            continue
        due = d.get("due_at")
        if date_from and (not due or due[:10] < date_from[:10]):
            continue
        if date_to and (not due or due[:10] > date_to[:10]):
            continue
        out.append(deadline_response(i, d))
    out.sort(key=lambda d: (d["due_at"] is None, d["due_at"] or "", d["title"].lower()))
    return out


def create_deadline(user_id: str, data: Dict, source: str = "MANUAL") -> Dict:
    fields = _fields(user_id, data, creating=True)
    now = _now()
    doc = {
        "user_id": user_id,
        "weight_pct": None,
        "notes": None,
        "completed": False,
        "external_uid": None,
        **fields,
        "source": source,
        "is_deleted": False,
        "created_at": now,
        "updated_at": now,
    }
    ref = _db().collection(DEADLINES).document()
    ref.set(doc)
    return deadline_response(ref.id, doc)


def create_bulk(user_id: str, items, source) -> List[Dict]:
    """Save deadlines the student picked from a syllabus or device calendar preview."""
    if source not in ("MANUAL", "SYLLABUS", "DEVICE"):
        raise ServiceError("source must be MANUAL, SYLLABUS or DEVICE")
    if not isinstance(items, list) or not items:
        raise ServiceError("deadlines must be a non-empty list")
    if len(items) > MAX_BULK:
        raise ServiceError(f"At most {MAX_BULK} deadlines at a time")
    prepared = []
    for i, item in enumerate(items):
        if not isinstance(item, dict):
            raise ServiceError(f"deadlines[{i}] must be an object")
        try:
            prepared.append(_fields(user_id, item, creating=True))
        except ServiceError as e:
            raise ServiceError(f"deadlines[{i}]: {e.message}", e.status)
    existing = {(normalise_title(d.get("title")), (d.get("due_at") or "")[:10]) for _, d in _all(user_id)}
    batch = _db().batch()
    out, now = [], _now()
    for fields in prepared:
        key = (normalise_title(fields["title"]), (fields.get("due_at") or "")[:10])
        if key in existing:
            continue  # already saved
        existing.add(key)
        doc = {"user_id": user_id, "weight_pct": None, "notes": None, "completed": False, "external_uid": None, **fields,
               "source": source, "is_deleted": False, "created_at": now, "updated_at": now}
        ref = _db().collection(DEADLINES).document()
        batch.set(ref, doc)
        out.append(deadline_response(ref.id, doc))
    if out:
        batch.commit()
    return out


def update_deadline(user_id: str, deadline_id: str, data: Dict) -> Dict:
    ref, doc = _get(user_id, deadline_id)
    fields = _fields(user_id, data, creating=False)
    if not fields:
        raise ServiceError("Nothing to update")
    fields["updated_at"] = _now()
    ref.update(fields)
    doc.update(fields)
    return deadline_response(deadline_id, doc)


def delete_deadline(user_id: str, deadline_id: str) -> None:
    ref, _ = _get(user_id, deadline_id)
    ref.update({"is_deleted": True, "updated_at": _now()})


# --- iCal import -----------------------------------------------------------------------------------

# Most specific first: "Final presentation" is a presentation, "Lab quiz" a quiz
_TYPE_WORDS = [
    ("QUIZ", r"\bquiz"),
    ("PRESENTATION", r"\bpresentation|\bpitch\b|\bposter\b"),
    ("LAB", r"\blab\b|\blaboratory\b"),
    ("EXAM", r"\b(exam|midterm|mid-term|final|test)\b"),
    ("ASSIGNMENT", r"\b(assignment|homework|hw\d*|problem set|project|essay|report|due|submission|deliverable)\b"),
]
_CLASS_MEETING = re.compile(r"\b(lecture|class|tutorial|office hours?|seminar|recitation)\b", re.I)


def infer_type(title: str) -> str:
    low = (title or "").lower()
    for t, pattern in _TYPE_WORDS:
        if re.search(pattern, low):
            return t
    return "OTHER"


def _match_course(courses, summary: str, categories: List[str]) -> Tuple[Optional[str], str]:
    """Canvas puts the course in "[CODE ...]" at the end of the title; Moodle uses CATEGORIES."""
    title = summary
    hints = [c for c in categories if c]
    m = re.search(r"\[([^\]]+)\]\s*$", summary)
    if m:
        hints.insert(0, m.group(1))
        title = summary[: m.start()].strip() or summary
    for hint in hints + [summary]:
        h = re.sub(r"[^a-z0-9]", "", hint.lower())
        if not h:
            continue
        for c in courses:
            code = re.sub(r"[^a-z0-9]", "", (c.code or "").lower())
            name = re.sub(r"[^a-z0-9]", "", c.name.lower())
            if (code and code in h) or (name and len(name) >= 6 and name in h):
                return c.id, title
    return None, title


def _to_due(value) -> Tuple[Optional[str], bool]:
    if isinstance(value, datetime):
        if value.tzinfo is None:
            value = value.replace(tzinfo=timezone.utc)  # floating time: treat as UTC
        return value.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"), False
    if isinstance(value, date):
        return value.isoformat(), True
    return None, True


def parse_ics(data: bytes, courses, now: Optional[datetime] = None) -> List[Dict]:
    """Deadlines from an .ics file. Skips past items, recurring class meetings and undated items."""
    import icalendar

    try:
        cal = icalendar.Calendar.from_ical(data)
    except Exception:
        raise ServiceError("This isn't a valid calendar (.ics) file")
    now = now or _now()
    cutoff = (now - timedelta(days=1)).strftime("%Y-%m-%d")
    horizon = (now + timedelta(days=400)).strftime("%Y-%m-%d")
    out = []
    for comp in cal.walk():
        if comp.name not in ("VEVENT", "VTODO"):
            continue
        summary = str(comp.get("SUMMARY") or "").strip()
        if not summary:
            continue
        prop = (comp.get("DUE") or comp.get("DTSTART")) if comp.name == "VTODO" else comp.get("DTSTART")
        due, all_day = _to_due(prop.dt if prop is not None else None)
        if not due or due[:10] < cutoff or due[:10] > horizon:
            continue
        categories: List[str] = []
        cats = comp.get("CATEGORIES")
        for c in cats if isinstance(cats, list) else [cats] if cats is not None else []:
            categories += [str(x) for x in getattr(c, "cats", [c])]
        course_id, title = _match_course(courses, summary, categories)
        kind = infer_type(title)
        if kind == "OTHER" and (comp.get("RRULE") is not None or _CLASS_MEETING.search(title)):
            continue  # lectures and tutorials aren't deadlines
        uid = str(comp.get("UID") or "") or f"{normalise_title(summary)}|{due}"
        out.append({"external_uid": uid[:300], "title": title[:200], "type": kind, "due_at": due, "all_day": all_day, "course_id": course_id})
        if len(out) >= MAX_ICS_EVENTS:
            break
    return out


def _check_public_host(host: str) -> None:
    try:
        infos = socket.getaddrinfo(host, 443, proto=socket.IPPROTO_TCP)
    except socket.gaierror:
        raise ServiceError("Couldn't reach that calendar address")
    for info in infos:
        ip = ipaddress.ip_address(info[4][0])
        if not ip.is_global:
            raise ServiceError("That calendar address isn't allowed")


def validate_feed_url(url) -> str:
    if not isinstance(url, str) or len(url) > 2000:
        raise ServiceError("url must be a calendar feed address")
    url = url.strip()
    if url.lower().startswith("webcal://"):
        url = "https://" + url[len("webcal://"):]
    parsed = urlparse(url)
    if parsed.scheme != "https" or not parsed.hostname:
        raise ServiceError("The calendar address must start with https:// or webcal://")
    if parsed.username or parsed.password:
        raise ServiceError("That calendar address isn't allowed")
    _check_public_host(parsed.hostname)
    return url


def fetch_feed(url: str) -> bytes:
    """Download a feed: https only, public hosts only (checked on every redirect), 2 MB, 10 s."""
    import requests

    url = validate_feed_url(url)
    for _ in range(4):
        with requests.get(url, timeout=FETCH_TIMEOUT, allow_redirects=False, stream=True, headers={"User-Agent": "WizlyAI calendar import"}) as res:
            if res.is_redirect:
                url = validate_feed_url(requests.compat.urljoin(url, res.headers.get("Location", "")))
                continue
            if res.status_code != 200:
                raise ServiceError(f"The calendar address returned an error ({res.status_code})")
            body = b""
            for chunk in res.iter_content(64 * 1024):
                body += chunk
                if len(body) > MAX_ICS_BYTES:
                    raise ServiceError("That calendar is too large (over 2 MB)")
            return body
    raise ServiceError("The calendar address redirected too many times")


def import_ical(user_id: str, data: Optional[bytes] = None, url: Optional[str] = None, course_id: Optional[str] = None, fetch: Optional[Callable] = None) -> Dict:
    if course_id:
        course_service.get_course(user_id, course_id)
    if data is None:
        if not url:
            raise ServiceError("Upload an .ics file or give a calendar feed url")
        data = (fetch or fetch_feed)(url)
    if len(data) > MAX_ICS_BYTES:
        raise ServiceError("That calendar is too large (over 2 MB)")
    courses = course_service.list_courses(user_id)
    items = parse_ics(data, courses)
    if course_id:
        for item in items:
            item["course_id"] = course_id
    existing = {d.get("external_uid"): (i, d) for i, d in _all(user_id) if d.get("external_uid")}
    created = updated = unchanged = 0
    batch, now = _db().batch(), _now()
    for item in items:
        match = existing.get(item["external_uid"])
        if match:
            doc_id, doc = match
            changes = {k: item[k] for k in ("title", "due_at", "all_day", "type") if doc.get(k) != item[k]}
            if item["course_id"] and not doc.get("course_id"):
                changes["course_id"] = item["course_id"]
            if changes:
                changes["updated_at"] = now  # never touches `completed`
                batch.update(_db().collection(DEADLINES).document(doc_id), changes)
                updated += 1
            else:
                unchanged += 1
            continue
        doc = {"user_id": user_id, **item, "weight_pct": None, "notes": None, "source": "ICAL", "completed": False,
               "is_deleted": False, "created_at": now, "updated_at": now}
        batch.set(_db().collection(DEADLINES).document(), doc)
        created += 1
    if created or updated:
        batch.commit()
    return {
        "created": created,
        "updated": updated,
        "unchanged": unchanged,
        "unmatched": sum(1 for i in items if not i["course_id"]),
        "total": len(items),
    }


# --- syllabus extraction ------------------------------------------------------------------------------

SYLLABUS_PROMPT = """You extract graded deadlines from a university course syllabus.

Course: {course_name}{course_code}
Today is {today}.{term}

List every graded item with a date: exams (midterm, final), assignments, quizzes,
labs, presentations and project milestones. For each give the title as written,
the type, the due date as YYYY-MM-DD and the weight as a percentage if stated.
If the syllabus gives a date without a year, use the year that puts it in the
current or next term. Leave the date empty if the syllabus gives none (for
example "during finals week"). Do not invent items, dates or weights.

=== SYLLABUS ===
{text}
"""


def _syllabus_material(user_id: str, course_id: str, material_id: Optional[str]) -> CourseMaterial:
    if material_id:
        material = course_service._get_owned(CourseMaterial, material_id, user_id, "Material")
        if material.course_id != course_id:
            raise ServiceError("Material not found", 404)
    else:
        materials = [m for m in course_service._list_owned(CourseMaterial, user_id, course_id=course_id)]
        materials.sort(key=lambda m: (m.kind != "SYLLABUS", m.ingestion_status != "READY"))
        material = next((m for m in materials if m.kind == "SYLLABUS"), None)
        if material is None:
            raise ServiceError("Upload the syllabus to this course first (as kind SYLLABUS), or choose a file", 409)
    if material.ingestion_status != "READY":
        raise ServiceError("This file is still being processed. Try again in a minute.", 409)
    return material


def _material_text(user_id: str, material_id: str, max_chars: int = 60_000) -> str:
    chunks = [
        d.to_dict() or {}
        for d in _db().collection(MATERIAL_CHUNKS).where(filter=FieldFilter("user_id", "==", user_id)).where(filter=FieldFilter("material_id", "==", material_id)).stream()
    ]
    chunks.sort(key=lambda c: c.get("chunk_index", 0))
    text, last = "", ""
    for c in chunks:
        t = c.get("text", "")
        if last and t.startswith(last[-100:]):
            t = t[len(last[-100:]):]
        text += t + "\n"
        last = c.get("text", "")
        if len(text) > max_chars:
            break
    return text[:max_chars]


def _default_extract(prompt: str):
    from app.ai_framework.utils.model_router import client_for
    from app.ai_framework.workflows.course_structure_generation import CourseStructure

    return client_for(SYLLABUS_TASK).generate_structured(response_model=CourseStructure, prompt=prompt)


def extract_from_syllabus(user_id: str, course_id: str, material_id: Optional[str] = None, extract: Optional[Callable] = None) -> Dict:
    """Candidate deadlines for the student to confirm; nothing is saved here."""
    course = course_service.get_course(user_id, course_id)
    material = _syllabus_material(user_id, course_id, material_id)
    text = _material_text(user_id, material.id)
    if not text.strip():
        raise ServiceError("No text was found in this file", 409)
    semester = None
    if course.semester_id:
        snap = _db().collection("semesters").document(course.semester_id).get()
        semester = snap.to_dict() if snap.exists else None
    term = ""
    if semester and (semester.get("start_date") or semester.get("end_date")):
        term = f" The term runs {semester.get('start_date') or '?'} to {semester.get('end_date') or '?'}."
    prompt = SYLLABUS_PROMPT.format(
        course_name=course.name,
        course_code=f" ({course.code})" if course.code else "",
        today=_now().date().isoformat(),
        term=term,
        text=text,
    )
    started = time.time()
    try:
        result = (extract or _default_extract)(prompt)
    except Exception as e:
        logger.error(f"Syllabus extraction failed for {course_id}: {e}", exc_info=True)
        _log(user_id, course_id, False, started, 0, str(e)[:300])
        raise ServiceError("Couldn't read the syllabus right now. Please try again later.", 502)
    existing = {(normalise_title(d.get("title")), (d.get("due_at") or "")[:10]): i for i, d in _all(user_id) if d.get("course_id") == course_id}
    candidates, seen = [], set()
    for d in result.deadlines:
        title = (d.title or "").strip()[:200]
        if not title:
            continue
        key = (normalise_title(title), d.due_date or "")
        if key in seen:
            continue
        seen.add(key)
        candidates.append(
            {"title": title, "type": d.type, "due_at": d.due_date, "all_day": True, "weight_pct": d.weight_pct, "course_id": course_id, "duplicate_of": existing.get(key)}
        )
    candidates.sort(key=lambda c: (c["due_at"] is None, c["due_at"] or ""))
    _log(user_id, course_id, True, started, len(candidates), None)
    return {"material_id": material.id, "material_title": material.title, "candidates": candidates}


def _log(user_id, course_id, success: bool, started: float, count: int, error: Optional[str]):
    try:
        from app.ai_framework.utils.model_router import model_for
        from app.services.ai_workflow_logger import AIWorkflowLogger

        AIWorkflowLogger().log_workflow(
            workflow_type=SYLLABUS_TASK,
            model=model_for(SYLLABUS_TASK),
            prompt={"template_version": "1.0"},
            input_data={"parameters": {"course_id": course_id}},
            response={"success": success, "error_message": error},
            metrics={"latency_ms": int((time.time() - started) * 1000), "deadlines": count},
            user_id=user_id,
        )
    except Exception as e:
        logger.warning(f"Could not log syllabus extraction: {e}")
