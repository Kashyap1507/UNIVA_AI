"""
Admin views for the university app: overview metrics, students, catalog,
moderation queue and AI costs.

Admins see profiles, counts and costs. They never see course materials, notes,
tutor chats or submissions; the one exception is the text of a reported group
post, which moderation needs.
"""

from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
from typing import Dict, Iterable, List, Optional, Tuple

from google.cloud.firestore_v1.base_query import FieldFilter

from app.services import ai_usage_service, study_group_service as groups
from app.services.course_service import ServiceError
from app.services.firebase_service import get_firestore_client

RANGES = (7, 30, 90)
MODERATION_ACTIONS = ("DISMISS", "REMOVE")
PREVIEW_CHARS = 400


def _db():
    return get_firestore_client()


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _dt(value) -> Optional[datetime]:
    """Any stored timestamp (aware, naive or ISO string) as an aware UTC datetime."""
    if value is None:
        return None
    if isinstance(value, str):
        try:
            value = datetime.fromisoformat(value.replace("Z", "+00:00"))
        except ValueError:
            return None
    if isinstance(value, datetime):
        return value if value.tzinfo else value.replace(tzinfo=timezone.utc)
    return None


def _iso(value) -> Optional[str]:
    d = _dt(value)
    return d.isoformat() if d else None


def _days(days) -> int:
    try:
        days = int(days)
    except (TypeError, ValueError):
        raise ServiceError(f"days must be one of: {', '.join(map(str, RANGES))}")
    if days not in RANGES:
        raise ServiceError(f"days must be one of: {', '.join(map(str, RANGES))}")
    return days


def _stream(collection: str, *filters: Tuple[str, str, object]) -> Iterable[Tuple[str, Dict]]:
    q = _db().collection(collection)
    for field, op, value in filters:
        q = q.where(filter=FieldFilter(field, op, value))
    for d in q.stream():
        yield d.id, d.to_dict() or {}


def _date_range(days: int) -> List[str]:
    today = _now().date()
    return [(today - timedelta(days=i)).isoformat() for i in range(days - 1, -1, -1)]


def _students() -> Dict[str, Dict]:
    return {uid: u for uid, u in _stream("users") if not u.get("is_admin")}


def _profile(u: Dict) -> Dict:
    return u.get("student_profile") or {}


def _usage(since: str) -> List[Dict]:
    return [d for _, d in _stream(ai_usage_service.COLLECTION, ("date", ">=", since))]


def _last_active(since: str) -> Dict[str, str]:
    last: Dict[str, str] = {}
    for _, d in _stream("study_days", ("date", ">=", since)):
        uid, day = d.get("user_id"), d.get("date")
        if uid and day and day > last.get(uid, ""):
            last[uid] = day
    return last


def _in_range(rows: Iterable[Tuple[str, Dict]], field: str, start: datetime) -> List[Dict]:
    return [d for _, d in rows if (_dt(d.get(field)) or datetime.min.replace(tzinfo=timezone.utc)) >= start]


# --- overview ------------------------------------------------------------------------------------------------------


def overview(days) -> Dict:
    days = _days(days)
    start = _now() - timedelta(days=days)
    dates = _date_range(days)
    since = dates[0]

    students = _students()
    new_students = [u for u in students.values() if (_dt(u.get("created_at")) or start - timedelta(days=1)) >= start]
    degrees = Counter((_profile(u).get("degree_level") or "UNSET").upper() for u in students.values())

    active_by_day: Dict[str, set] = defaultdict(set)
    for _, d in _stream("study_days", ("date", ">=", since)):
        if d.get("user_id") in students:
            active_by_day[d.get("date")].add(d["user_id"])
    active = set().union(*active_by_day.values()) if active_by_day else set()

    reviews = _in_range(_stream("flashcard_reviews", ("reviewed_at", ">=", start)), "reviewed_at", start)
    reviews_by_day = Counter((_dt(r.get("reviewed_at")) or start).date().isoformat() for r in reviews)

    usage = _usage(since)
    cost_by_day: Dict[str, float] = defaultdict(float)
    for u in usage:
        cost_by_day[u.get("date")] += u.get("cost_usd") or 0.0

    courses = [d for _, d in _stream("courses") if not d.get("is_deleted")]
    materials = [d for _, d in _stream("course_materials") if not d.get("is_deleted")]
    open_reports = sum(1 for _ in _stream(groups.REPORTS, ("status", "==", "OPEN")))
    universities = Counter(groups._university(u) for u in students.values() if groups._university(u))
    spellings: Dict[str, Counter] = defaultdict(Counter)
    for u in students.values():
        if groups._university(u):
            spellings[groups._university(u)][_profile(u)["university"].strip()] += 1

    return {
        "days": days,
        "students": {
            "total": len(students),
            "new": len(new_students),
            "active": len(active),
            "bachelor": degrees.get("BACHELOR", 0),
            "master": degrees.get("MASTER", 0),
            "profile_incomplete": degrees.get("UNSET", 0),
        },
        "content": {
            "courses": len(courses),
            "materials": len(materials),
            "materials_failed": sum(1 for m in materials if m.get("ingestion_status") == "FAILED"),
            "flashcard_reviews": len(reviews),
            "practice_attempts": len(_in_range(_stream("practice_attempts"), "finished_at", start)),
            "tutor_sessions": len(_in_range(_stream("tutor_sessions"), "created_at", start)),
            "work_checks": len(_in_range(_stream("work_submissions"), "created_at", start)),
            "study_groups": sum(1 for _, g in _stream("study_groups") if not g.get("is_deleted")),
            "papers": sum(1 for _, p in _stream("papers") if not p.get("is_deleted")),
        },
        "moderation": {"open_reports": open_reports},
        "ai": {
            "cost_usd": round(sum(cost_by_day.values()), 4),
            "calls": sum(u.get("calls") or 0 for u in usage),
        },
        "daily": [
            {
                "date": day,
                "active_students": len(active_by_day.get(day, ())),
                "flashcard_reviews": reviews_by_day.get(day, 0),
                "ai_cost_usd": round(cost_by_day.get(day, 0.0), 4),
            }
            for day in dates
        ],
        "top_universities": [{"name": _display(spellings[key]), "students": n} for key, n in universities.most_common(5)],
    }


# --- students ------------------------------------------------------------------------------------------------------


def _student_row(uid: str, u: Dict, course_counts: Counter, last_active: Dict[str, str], cost_30d: Dict[str, float]) -> Dict:
    p = _profile(u)
    return {
        "id": uid,
        "name": u.get("name"),
        "email": u.get("email"),
        "university": p.get("university"),
        "degree_level": p.get("degree_level"),
        "program": p.get("program"),
        "current_semester": p.get("current_semester"),
        "is_active": u.get("is_active", True) is not False,
        "created_at": _iso(u.get("created_at")),
        "course_count": course_counts.get(uid, 0),
        "last_active": last_active.get(uid),
        "ai_cost_30d": round(cost_30d.get(uid, 0.0), 4),
    }


def list_students(query: Optional[str] = None, degree: Optional[str] = None, limit=200) -> Dict:
    try:
        limit = max(1, min(int(limit), 500))
    except (TypeError, ValueError):
        limit = 200
    students = _students()
    course_counts = Counter(d.get("user_id") for _, d in _stream("courses") if not d.get("is_deleted"))
    since_30 = (_now().date() - timedelta(days=29)).isoformat()
    cost_30d: Dict[str, float] = defaultdict(float)
    for row in _usage(since_30):
        cost_30d[row.get("user_id")] += row.get("cost_usd") or 0.0
    last_active = _last_active((_now().date() - timedelta(days=365)).isoformat())

    rows = [_student_row(uid, u, course_counts, last_active, cost_30d) for uid, u in students.items()]
    if degree:
        rows = [r for r in rows if (r["degree_level"] or "").upper() == degree.upper()]
    if query and query.strip():
        needle = query.strip().lower()
        rows = [r for r in rows if any(needle in (r.get(f) or "").lower() for f in ("name", "email", "university", "program"))]
    rows.sort(key=lambda r: r["created_at"] or "", reverse=True)
    return {"total": len(rows), "students": rows[:limit]}


def student_detail(user_id: str) -> Dict:
    snap = _db().collection("users").document(user_id).get()
    u = snap.to_dict() if snap.exists else None
    if not u or u.get("is_admin"):
        raise ServiceError("Student not found", 404)

    courses = [(cid, c) for cid, c in _stream("courses", ("user_id", "==", user_id)) if not c.get("is_deleted")]
    materials = [m for _, m in _stream("course_materials", ("user_id", "==", user_id)) if not m.get("is_deleted")]
    per_course = Counter(m.get("course_id") for m in materials)
    since_30 = (_now().date() - timedelta(days=29)).isoformat()
    usage = [d for _, d in _stream(ai_usage_service.COLLECTION, ("user_id", "==", user_id)) if (d.get("date") or "") >= since_30]
    by_task: Dict[str, Dict] = defaultdict(lambda: {"calls": 0, "tokens_in": 0, "tokens_out": 0, "cost_usd": 0.0})
    for day in usage:
        for task, t in (day.get("by_task") or {}).items():
            for k in by_task[task]:
                by_task[task][k] += t.get(k) or 0
    days = sorted(d.get("date") for _, d in _stream("study_days", ("user_id", "==", user_id)) if d.get("date"))
    memberships = [m for _, m in _stream("group_members", ("user_id", "==", user_id)) if m.get("status") == "ACTIVE"]

    def count(collection: str) -> int:
        return sum(1 for _, d in _stream(collection, ("user_id", "==", user_id)) if not d.get("is_deleted"))

    row = _student_row(user_id, u, Counter({user_id: len(courses)}), {user_id: days[-1]} if days else {}, {user_id: sum(d.get("cost_usd") or 0.0 for d in usage)})
    return {
        **row,
        "auth_provider": u.get("auth_provider"),
        "study_days_total": len(days),
        "courses": sorted(
            [
                {
                    "id": cid,
                    "name": c.get("name"),
                    "code": c.get("code"),
                    "status": c.get("status", "ACTIVE"),
                    "structure_status": c.get("structure_status", "NONE"),
                    "material_count": per_course.get(cid, 0),
                    "created_at": _iso(c.get("created_at")),
                }
                for cid, c in courses
            ],
            key=lambda c: c["created_at"] or "",
            reverse=True,
        ),
        "counts": {
            "materials": len(materials),
            "materials_failed": sum(1 for m in materials if m.get("ingestion_status") == "FAILED"),
            "flashcards": count("flashcards"),
            "flashcard_reviews": count("flashcard_reviews"),
            "practice_attempts": count("practice_attempts"),
            "tutor_sessions": count("tutor_sessions"),
            "work_checks": count("work_submissions"),
            "deadlines": count("deadlines"),
            "papers": count("papers"),
            "research_projects": count("research_projects"),
            "study_groups": len(memberships),
        },
        "ai_usage_30d": {
            "calls": sum(d.get("calls") or 0 for d in usage),
            "tokens_in": sum(d.get("tokens_in") or 0 for d in usage),
            "tokens_out": sum(d.get("tokens_out") or 0 for d in usage),
            "cost_usd": round(sum(d.get("cost_usd") or 0.0 for d in usage), 4),
            "by_task": sorted(
                [{"task": task, **{k: (round(v, 4) if k == "cost_usd" else v) for k, v in t.items()}} for task, t in by_task.items()],
                key=lambda t: t["cost_usd"],
                reverse=True,
            ),
        },
    }


# --- catalog -------------------------------------------------------------------------------------------------------


def _display(names: Counter) -> str:
    """The most common spelling of a name."""
    return names.most_common(1)[0][0]


def catalog() -> Dict:
    students = _students()
    unis: Dict[str, Dict] = {}
    uni_of: Dict[str, str] = {}
    for uid, u in students.items():
        p = _profile(u)
        raw = (p.get("university") or "").strip()
        key = groups._university(u) or ""
        uni_of[uid] = key
        entry = unis.setdefault(key, {"names": Counter(), "students": 0, "degrees": Counter(), "programs": defaultdict(Counter), "courses": {}})
        if raw:
            entry["names"][raw] += 1
        entry["students"] += 1
        entry["degrees"][(p.get("degree_level") or "UNSET").upper()] += 1
        program = (p.get("program") or "").strip()
        if program:
            entry["programs"][program.lower()][program] += 1

    for _, c in _stream("courses"):
        uid = c.get("user_id")
        if c.get("is_deleted") or uid not in uni_of:
            continue
        code = " ".join((c.get("code") or "").upper().replace("-", " ").split())
        key = code or f"~{(c.get('name') or '').strip().lower()}"
        if key == "~":
            continue
        course = unis[uni_of[uid]]["courses"].setdefault(key, {"code": code or None, "names": Counter(), "students": set()})
        if c.get("name"):
            course["names"][c["name"].strip()] += 1
        course["students"].add(uid)

    result = []
    for key, e in unis.items():
        programs = sorted(
            ({"name": _display(names), "students": sum(names.values())} for names in e["programs"].values()),
            key=lambda p: (-p["students"], p["name"]),
        )
        courses = sorted(
            (
                {"code": c["code"], "name": _display(c["names"]) if c["names"] else None, "students": len(c["students"])}
                for c in e["courses"].values()
            ),
            key=lambda c: (-c["students"], c["code"] or "~", c["name"] or ""),
        )
        result.append(
            {
                "name": _display(e["names"]) if e["names"] else None,
                "students": e["students"],
                "bachelor": e["degrees"].get("BACHELOR", 0),
                "master": e["degrees"].get("MASTER", 0),
                "programs": programs,
                "courses": courses[:100],
                "course_count": len(courses),
            }
        )
    result.sort(key=lambda u: (u["name"] is None, -u["students"], u["name"] or ""))
    return {"universities": result}


# --- moderation ----------------------------------------------------------------------------------------------------


def _target_preview(target_type: str, target_id: str, group_id: Optional[str]) -> Dict:
    g_snap = _db().collection(groups.GROUPS).document(group_id).get() if group_id else None
    g = (g_snap.to_dict() or {}) if g_snap is not None and g_snap.exists else {}
    preview = {"group_id": group_id, "group_name": g.get("name"), "group_deleted": bool(g.get("is_deleted"))}
    if target_type == "NOTE":
        snap = _db().collection(groups.NOTES).document(target_id).get()
        n = (snap.to_dict() or {}) if snap.exists else {}
        preview.update(
            {
                "kind": n.get("kind"),
                "title": n.get("title") or n.get("material_title"),
                "body": (n.get("body") or "")[:PREVIEW_CHARS],
                "author_id": n.get("author_id"),
                "author_name": n.get("author_name"),
                "hidden": bool(n.get("hidden")),
                "removed": bool(n.get("is_deleted")),
                "posted_at": _iso(n.get("created_at")),
            }
        )
    elif target_type == "MEMBER":
        snap = _db().collection(groups.MEMBERS).document(f"{group_id}_{target_id}").get()
        m = (snap.to_dict() or {}) if snap.exists else {}
        preview.update({"member_name": m.get("display_name"), "member_status": m.get("status"), "removed": m.get("status") == "REMOVED"})
    else:
        preview.update({"description": (g.get("description") or "")[:PREVIEW_CHARS], "removed": bool(g.get("is_deleted"))})
    return preview


def moderation_queue(status: str = "OPEN") -> Dict:
    status = (status or "OPEN").upper()
    if status not in ("OPEN", "RESOLVED"):
        raise ServiceError("status must be OPEN or RESOLVED")
    targets: Dict[Tuple[str, str], Dict] = {}
    for rid, r in _stream(groups.REPORTS, ("status", "==", status)):
        key = (r.get("target_type"), r.get("target_id"))
        t = targets.setdefault(key, {"reports": [], "group_id": r.get("group_id")})
        t["reports"].append((rid, r))

    reporters = {}
    items = []
    for (target_type, target_id), t in targets.items():
        reports = sorted(t["reports"], key=lambda x: _dt(x[1].get("created_at")) or datetime.min.replace(tzinfo=timezone.utc))
        for _, r in reports:
            uid = r.get("reporter_id")
            if uid and uid not in reporters:
                snap = _db().collection("users").document(uid).get()
                reporters[uid] = groups.display_name(snap.to_dict() or {}) if snap.exists else "Unknown"
        last = reports[-1][1]
        items.append(
            {
                "id": reports[0][0],
                "report_ids": [rid for rid, _ in reports],
                "target_type": target_type,
                "target_id": target_id,
                "report_count": len(reports),
                "reasons": dict(Counter(r.get("reason") for _, r in reports)),
                "details": [
                    {"reporter": reporters.get(r.get("reporter_id")), "reason": r.get("reason"), "details": r.get("details"), "created_at": _iso(r.get("created_at"))}
                    for _, r in reports
                ],
                "first_reported_at": _iso(reports[0][1].get("created_at")),
                "last_reported_at": _iso(last.get("created_at")),
                "resolution": last.get("resolution") if status == "RESOLVED" else None,
                "target": _target_preview(target_type, target_id, t["group_id"]),
            }
        )
    items.sort(key=lambda i: (-i["report_count"], i["last_reported_at"] or ""), reverse=False)
    if status == "RESOLVED":
        items.sort(key=lambda i: (i.get("resolution") or {}).get("at") or "", reverse=True)
    return {"status": status, "items": items}


def resolve(report_id: str, admin_id: Optional[str], data: Dict) -> Dict:
    action = (data.get("action") or "").upper()
    if action not in MODERATION_ACTIONS:
        raise ServiceError(f"action must be one of: {', '.join(MODERATION_ACTIONS)}")
    note = data.get("note")
    if note is not None and not isinstance(note, str):
        raise ServiceError("note must be text")
    note = (note or "").strip()[:500] or None

    snap = _db().collection(groups.REPORTS).document(report_id).get()
    if not snap.exists:
        raise ServiceError("Report not found", 404)
    r = snap.to_dict() or {}
    if r.get("status") != "OPEN":
        raise ServiceError("This report has already been resolved", 409)
    target_type, target_id, group_id = r.get("target_type"), r.get("target_id"), r.get("group_id")
    now = _now()

    if target_type == "NOTE":
        note_ref = _db().collection(groups.NOTES).document(target_id)
        if note_ref.get().exists:
            if action == "REMOVE":
                note_ref.update({"is_deleted": True, "hidden": True, "removed_by_admin": True, "removed_at": now})
            else:
                note_ref.update({"hidden": False})
    elif target_type == "MEMBER" and action == "REMOVE":
        g_ref = _db().collection(groups.GROUPS).document(group_id)
        g_snap = g_ref.get()
        member = groups._membership(group_id, target_id)
        if g_snap.exists and member and member.get("status") == "ACTIVE":
            if member.get("role") == "OWNER":
                raise ServiceError("This member owns the group: remove the group instead", 409)
            groups._set_member_status(g_ref, g_snap.to_dict() or {}, group_id, target_id, "REMOVED")
    elif target_type == "GROUP" and action == "REMOVE":
        g_ref = _db().collection(groups.GROUPS).document(group_id)
        if g_ref.get().exists:
            g_ref.update({"is_deleted": True, "removed_by_admin": True, "updated_at": now})

    resolution = {"action": action, "note": note, "by": admin_id, "at": now.isoformat()}
    resolved = 0
    for rid, other in _stream(groups.REPORTS, ("target_id", "==", target_id), ("status", "==", "OPEN")):
        if other.get("target_type") != target_type:
            continue
        _db().collection(groups.REPORTS).document(rid).update({"status": "RESOLVED", "resolution": resolution, "resolved_at": now})
        resolved += 1
    return {"action": action, "resolved_reports": resolved, "target_type": target_type, "target_id": target_id}


# --- AI costs ------------------------------------------------------------------------------------------------------


def ai_costs(days) -> Dict:
    days = _days(days)
    dates = _date_range(days)
    usage = _usage(dates[0])
    by_day: Dict[str, Dict] = {d: {"date": d, "calls": 0, "cost_usd": 0.0, "tokens_in": 0, "tokens_out": 0} for d in dates}
    by_task: Dict[str, Dict] = defaultdict(lambda: {"calls": 0, "tokens_in": 0, "tokens_out": 0, "cost_usd": 0.0})
    by_user: Dict[str, Dict] = defaultdict(lambda: {"calls": 0, "cost_usd": 0.0})
    for row in usage:
        day = by_day.get(row.get("date"))
        if day is None:
            continue
        for k in ("calls", "tokens_in", "tokens_out", "cost_usd"):
            day[k] += row.get(k) or 0
        for task, t in (row.get("by_task") or {}).items():
            for k in by_task[task]:
                by_task[task][k] += t.get(k) or 0
        u = by_user[row.get("user_id")]
        u["calls"] += row.get("calls") or 0
        u["cost_usd"] += row.get("cost_usd") or 0.0

    top = sorted(by_user.items(), key=lambda x: x[1]["cost_usd"], reverse=True)[:20]
    top_rows = []
    for uid, u in top:
        snap = _db().collection("users").document(uid).get() if uid else None
        user = (snap.to_dict() or {}) if snap is not None and snap.exists else {}
        top_rows.append(
            {
                "user_id": uid,
                "name": user.get("name"),
                "email": user.get("email"),
                "university": _profile(user).get("university"),
                "calls": u["calls"],
                "cost_usd": round(u["cost_usd"], 4),
            }
        )
    total_cost = sum(d["cost_usd"] for d in by_day.values())
    return {
        "days": days,
        "totals": {
            "cost_usd": round(total_cost, 4),
            "calls": sum(d["calls"] for d in by_day.values()),
            "tokens_in": sum(d["tokens_in"] for d in by_day.values()),
            "tokens_out": sum(d["tokens_out"] for d in by_day.values()),
            "students": len(by_user),
            "cost_per_student": round(total_cost / len(by_user), 4) if by_user else 0.0,
        },
        "daily": [{**d, "cost_usd": round(d["cost_usd"], 4)} for d in by_day.values()],
        "by_task": sorted(
            [{"task": task, **{k: (round(v, 4) if k == "cost_usd" else v) for k, v in t.items()}} for task, t in by_task.items()],
            key=lambda t: t["cost_usd"],
            reverse=True,
        ),
        "top_students": top_rows,
    }
