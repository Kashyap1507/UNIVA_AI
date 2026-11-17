"""
Study groups, shared notes and content reports.

    study_groups:    name, description, course_code, university, invite_code, owner_id,
                     discoverable, member_count, is_deleted, created_at, updated_at
    group_members:   id "{group_id}_{user_id}"; group_id, user_id, role (OWNER|MEMBER),
                     status (ACTIVE|LEFT|REMOVED), display_name, joined_at
    shared_notes:    group_id, author_id, author_name, kind (TEXT|MATERIAL), title, body,
                     material_id, material_title, material_mime, hidden, is_deleted, created_at
    content_reports: reporter_id, group_id, target_type (NOTE|MEMBER|GROUP), target_id,
                     reason, details, status (OPEN|RESOLVED), resolution, created_at

Groups are private: joined by invite code, or found by course code at the same
university only when the owner made the group discoverable. Members see each
other as "First L."; emails are never returned. Sharing a material is opt-in and
per group. Three open reports hide a note until an admin reviews it (Phase 11).
"""

import re
import secrets
from datetime import datetime, timezone
from typing import Callable, Dict, List, Optional, Tuple

from google.cloud.firestore_v1.base_query import FieldFilter

from app.models.course import CourseMaterial
from app.services import course_service, material_service
from app.services.course_service import ServiceError
from app.services.firebase_service import get_firestore_client

GROUPS = "study_groups"
MEMBERS = "group_members"
NOTES = "shared_notes"
REPORTS = "content_reports"

MAX_MEMBERS = 50
MAX_GROUPS_PER_USER = 20
AUTO_HIDE_REPORTS = 3
REPORT_REASONS = ("SPAM", "INAPPROPRIATE", "COPYRIGHT", "ACADEMIC_INTEGRITY", "OTHER")
TARGET_TYPES = ("NOTE", "MEMBER", "GROUP")
CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"  # no 0/O or 1/I
CODE_LENGTH = 8


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _iso(value):
    return value.isoformat() if hasattr(value, "isoformat") else value


def _db():
    return get_firestore_client()


def normalise_code(code: Optional[str]) -> Optional[str]:
    """'comp 3300', 'COMP-3300' → 'COMP3300' (for matching course codes)."""
    if not code:
        return None
    return re.sub(r"[^A-Z0-9]", "", code.upper()) or None


def _user(user_id: str) -> Dict:
    snap = _db().collection("users").document(user_id).get()
    return snap.to_dict() or {} if snap.exists else {}


def display_name(user: Dict) -> str:
    """'Priya Shah' → 'Priya S.'; never the email."""
    name = (user.get("name") or (user.get("student_profile") or {}).get("name") or "").strip()
    parts = name.split()
    if not parts:
        return "Student"
    return parts[0][:30] + (f" {parts[-1][0].upper()}." if len(parts) > 1 else "")


def _university(user: Dict) -> Optional[str]:
    uni = ((user.get("student_profile") or {}).get("university") or "").strip()
    return uni.lower() if uni else None


def _new_code() -> str:
    for _ in range(10):
        code = "".join(secrets.choice(CODE_ALPHABET) for _ in range(CODE_LENGTH))
        clash = list(_db().collection(GROUPS).where(filter=FieldFilter("invite_code", "==", code)).limit(1).stream())
        if not clash:
            return code
    raise ServiceError("Couldn't create an invite code. Please try again.", 500)


# --- responses ------------------------------------------------------------------------------------


def group_response(group_id: str, g: Dict, member: Optional[Dict] = None, members: Optional[List[Dict]] = None) -> Dict:
    out = {
        "id": group_id,
        "name": g.get("name"),
        "description": g.get("description"),
        "course_code": g.get("course_code"),
        "discoverable": g.get("discoverable", False),
        "member_count": g.get("member_count", 0),
        "created_at": _iso(g.get("created_at")),
    }
    if member:
        out["role"] = member.get("role")
        out["invite_code"] = g.get("invite_code")  # members only
    if members is not None:
        out["members"] = members
    return out


def note_response(note_id: str, n: Dict, viewer_id: str, viewer_role: str) -> Dict:
    return {
        "id": note_id,
        "group_id": n.get("group_id"),
        "kind": n.get("kind"),
        "title": n.get("title"),
        "body": n.get("body"),
        "author_name": n.get("author_name"),
        "is_mine": n.get("author_id") == viewer_id,
        "can_remove": n.get("author_id") == viewer_id or viewer_role == "OWNER",
        "material_title": n.get("material_title"),
        "material_mime": n.get("material_mime"),
        "created_at": _iso(n.get("created_at")),
    }


# --- access ------------------------------------------------------------------------------------------


def _group(group_id: str) -> Tuple[object, Dict]:
    if not group_id or not isinstance(group_id, str):
        raise ServiceError("Group not found", 404)
    ref = _db().collection(GROUPS).document(group_id)
    snap = ref.get()
    g = snap.to_dict() if snap.exists else None
    if not g or g.get("is_deleted"):
        raise ServiceError("Group not found", 404)
    return ref, g


def _membership(group_id: str, user_id: str) -> Optional[Dict]:
    snap = _db().collection(MEMBERS).document(f"{group_id}_{user_id}").get()
    m = snap.to_dict() if snap.exists else None
    return m if m and m.get("status") == "ACTIVE" else None


def _require_member(group_id: str, user_id: str) -> Tuple[object, Dict, Dict]:
    ref, g = _group(group_id)
    m = _membership(group_id, user_id)
    if not m:
        raise ServiceError("Group not found", 404)  # don't reveal groups to non-members
    return ref, g, m


def _require_owner(group_id: str, user_id: str):
    ref, g, m = _require_member(group_id, user_id)
    if m.get("role") != "OWNER":
        raise ServiceError("Only the group owner can do that", 403)
    return ref, g, m


def _active_members(group_id: str) -> List[Dict]:
    rows = [d.to_dict() or {} for d in _db().collection(MEMBERS).where(filter=FieldFilter("group_id", "==", group_id)).stream()]
    return [r for r in rows if r.get("status") == "ACTIVE"]


def _my_group_count(user_id: str) -> int:
    rows = [d.to_dict() or {} for d in _db().collection(MEMBERS).where(filter=FieldFilter("user_id", "==", user_id)).stream()]
    return sum(1 for r in rows if r.get("status") == "ACTIVE")


def _add_member(group_ref, g: Dict, group_id: str, user_id: str, role: str) -> Dict:
    now = _now()
    member = {
        "group_id": group_id,
        "user_id": user_id,
        "role": role,
        "status": "ACTIVE",
        "display_name": display_name(_user(user_id)),
        "joined_at": now,
    }
    _db().collection(MEMBERS).document(f"{group_id}_{user_id}").set(member)
    group_ref.update({"member_count": g.get("member_count", 0) + 1, "updated_at": now})
    g["member_count"] = g.get("member_count", 0) + 1
    return member


# --- groups ------------------------------------------------------------------------------------------------


def _group_fields(data: Dict, creating: bool) -> Dict:
    out: Dict = {}
    if creating or "name" in data:
        out["name"] = course_service._text(data, "name", 80, required=True)
    if "description" in data:
        out["description"] = course_service._text(data, "description", 500)
    if "course_code" in data:
        code = course_service._text(data, "course_code", 30)
        out["course_code"] = code.upper() if code else None
        out["course_code_key"] = normalise_code(code)
    if "discoverable" in data:
        if not isinstance(data["discoverable"], bool):
            raise ServiceError("discoverable must be true or false")
        out["discoverable"] = data["discoverable"]
    return out


def create_group(user_id: str, data: Dict) -> Dict:
    fields = _group_fields(data, creating=True)
    if _my_group_count(user_id) >= MAX_GROUPS_PER_USER:
        raise ServiceError(f"You can be in at most {MAX_GROUPS_PER_USER} groups")
    user = _user(user_id)
    now = _now()
    g = {
        "description": None,
        "course_code": None,
        "course_code_key": None,
        "discoverable": False,
        **fields,
        "university": _university(user),
        "invite_code": _new_code(),
        "owner_id": user_id,
        "member_count": 0,
        "is_deleted": False,
        "created_at": now,
        "updated_at": now,
    }
    ref = _db().collection(GROUPS).document()
    ref.set(g)
    member = _add_member(ref, g, ref.id, user_id, "OWNER")
    return group_response(ref.id, g, member)


def list_my_groups(user_id: str) -> List[Dict]:
    out = []
    for d in _db().collection(MEMBERS).where(filter=FieldFilter("user_id", "==", user_id)).stream():
        m = d.to_dict() or {}
        if m.get("status") != "ACTIVE":
            continue
        snap = _db().collection(GROUPS).document(m["group_id"]).get()
        g = snap.to_dict() if snap.exists else None
        if g and not g.get("is_deleted"):
            out.append(group_response(snap.id, g, m))
    out.sort(key=lambda x: (x["name"] or "").lower())
    return out


def discover(user_id: str, course_code) -> List[Dict]:
    """Discoverable groups for a course code at the student's own university (no invite codes)."""
    key = normalise_code(course_code if isinstance(course_code, str) else None)
    if not key or len(key) < 3:
        raise ServiceError("Enter a course code, e.g. COMP-3300")
    university = _university(_user(user_id))
    if not university:
        raise ServiceError("Add your university to your profile to find groups")
    out = []
    q = _db().collection(GROUPS).where(filter=FieldFilter("course_code_key", "==", key)).where(filter=FieldFilter("discoverable", "==", True))
    for d in q.stream():
        g = d.to_dict() or {}
        if g.get("is_deleted") or g.get("university") != university or _membership(d.id, user_id):
            continue
        out.append(group_response(d.id, g))
    out.sort(key=lambda x: -x["member_count"])
    return out[:30]


def _join(user_id: str, group_id: str, g: Dict, ref) -> Dict:
    existing = _membership(group_id, user_id)
    if existing:
        return group_response(group_id, g, existing)
    if g.get("member_count", 0) >= MAX_MEMBERS:
        raise ServiceError(f"This group is full ({MAX_MEMBERS} members)")
    if _my_group_count(user_id) >= MAX_GROUPS_PER_USER:
        raise ServiceError(f"You can be in at most {MAX_GROUPS_PER_USER} groups")
    snap = _db().collection(MEMBERS).document(f"{group_id}_{user_id}").get()
    if snap.exists and (snap.to_dict() or {}).get("status") == "REMOVED":
        raise ServiceError("You were removed from this group", 403)
    member = _add_member(ref, g, group_id, user_id, "MEMBER")
    return group_response(group_id, g, member)


def join_by_code(user_id: str, code) -> Dict:
    if not isinstance(code, str) or not code.strip():
        raise ServiceError("Enter an invite code")
    code = re.sub(r"[^A-Z0-9]", "", code.upper())
    matches = [d for d in _db().collection(GROUPS).where(filter=FieldFilter("invite_code", "==", code)).limit(1).stream()]
    if not matches or (matches[0].to_dict() or {}).get("is_deleted"):
        raise ServiceError("No group has that invite code", 404)
    d = matches[0]
    return _join(user_id, d.id, d.to_dict() or {}, d.reference)


def join_discoverable(user_id: str, group_id: str) -> Dict:
    ref, g = _group(group_id)
    if not g.get("discoverable") or g.get("university") != _university(_user(user_id)):
        raise ServiceError("Group not found", 404)
    return _join(user_id, group_id, g, ref)


def get_group(user_id: str, group_id: str) -> Dict:
    _, g, m = _require_member(group_id, user_id)
    members = sorted(
        ({"user_id": x["user_id"], "display_name": x.get("display_name"), "role": x.get("role"), "is_me": x["user_id"] == user_id} for x in _active_members(group_id)),
        key=lambda x: (x["role"] != "OWNER", (x["display_name"] or "").lower()),
    )
    return group_response(group_id, g, m, members)


def update_group(user_id: str, group_id: str, data: Dict) -> Dict:
    ref, g, m = _require_owner(group_id, user_id)
    fields = _group_fields(data, creating=False)
    if not fields:
        raise ServiceError("Nothing to update")
    fields["updated_at"] = _now()
    ref.update(fields)
    g.update(fields)
    return group_response(group_id, g, m)


def reset_invite_code(user_id: str, group_id: str) -> Dict:
    ref, g, m = _require_owner(group_id, user_id)
    g["invite_code"] = _new_code()
    ref.update({"invite_code": g["invite_code"], "updated_at": _now()})
    return group_response(group_id, g, m)


def _set_member_status(group_ref, g: Dict, group_id: str, user_id: str, status: str):
    _db().collection(MEMBERS).document(f"{group_id}_{user_id}").update({"status": status, "left_at": _now()})
    count = max(0, g.get("member_count", 1) - 1)
    group_ref.update({"member_count": count, "updated_at": _now()})
    g["member_count"] = count


def leave_group(user_id: str, group_id: str) -> None:
    ref, g, m = _require_member(group_id, user_id)
    _set_member_status(ref, g, group_id, user_id, "LEFT")
    if m.get("role") == "OWNER":
        others = sorted((x for x in _active_members(group_id) if x["user_id"] != user_id), key=lambda x: x.get("joined_at") or _now())
        if others:
            heir = others[0]
            _db().collection(MEMBERS).document(f"{group_id}_{heir['user_id']}").update({"role": "OWNER"})
            ref.update({"owner_id": heir["user_id"]})
        else:
            ref.update({"is_deleted": True, "updated_at": _now()})


def remove_member(user_id: str, group_id: str, member_user_id: str) -> None:
    ref, g, _ = _require_owner(group_id, user_id)
    if member_user_id == user_id:
        raise ServiceError("Use leave to leave your own group")
    if not _membership(group_id, member_user_id):
        raise ServiceError("Member not found", 404)
    _set_member_status(ref, g, group_id, member_user_id, "REMOVED")


def delete_group(user_id: str, group_id: str) -> None:
    ref, _, _ = _require_owner(group_id, user_id)
    ref.update({"is_deleted": True, "updated_at": _now()})


# --- notes ---------------------------------------------------------------------------------------------------


def list_notes(user_id: str, group_id: str) -> List[Dict]:
    _, _, m = _require_member(group_id, user_id)
    rows = [(d.id, d.to_dict() or {}) for d in _db().collection(NOTES).where(filter=FieldFilter("group_id", "==", group_id)).stream()]
    rows = [r for r in rows if not r[1].get("is_deleted") and not r[1].get("hidden")]
    rows.sort(key=lambda r: r[1].get("created_at") or _now(), reverse=True)
    return [note_response(i, n, user_id, m.get("role")) for i, n in rows[:200]]


def create_note(user_id: str, group_id: str, data: Dict) -> Dict:
    _, _, m = _require_member(group_id, user_id)
    kind = data.get("kind", "TEXT")
    if kind not in ("TEXT", "MATERIAL"):
        raise ServiceError("kind must be TEXT or MATERIAL")
    now = _now()
    note = {
        "group_id": group_id,
        "author_id": user_id,
        "author_name": m.get("display_name"),
        "kind": kind,
        "title": None,
        "body": course_service._text(data, "body", 5000),
        "material_id": None,
        "material_title": None,
        "material_mime": None,
        "hidden": False,
        "is_deleted": False,
        "created_at": now,
    }
    if kind == "TEXT":
        note["title"] = course_service._text(data, "title", 150)
        if not note["body"]:
            raise ServiceError("Write something to post")
    else:
        # Only your own, processed materials can be shared
        material = course_service._get_owned(CourseMaterial, data.get("material_id"), user_id, "Material")
        dupes = [
            d for d in _db().collection(NOTES).where(filter=FieldFilter("group_id", "==", group_id)).where(filter=FieldFilter("material_id", "==", material.id)).stream()
            if not (d.to_dict() or {}).get("is_deleted")
        ]
        if dupes:
            raise ServiceError("You've already shared this file here")
        note.update(
            title=course_service._text(data, "title", 150) or material.title,
            material_id=material.id,
            material_title=material.title,
            material_mime=getattr(material, "mime", None),
        )
        _db().collection(CourseMaterial.collection_name).document(material.id).update({"shared": True})
    ref = _db().collection(NOTES).document()
    ref.set(note)
    return note_response(ref.id, note, user_id, m.get("role"))


def _note(group_id: str, note_id: str) -> Tuple[object, Dict]:
    if not note_id or not isinstance(note_id, str):
        raise ServiceError("Note not found", 404)
    ref = _db().collection(NOTES).document(note_id)
    snap = ref.get()
    n = snap.to_dict() if snap.exists else None
    if not n or n.get("group_id") != group_id or n.get("is_deleted"):
        raise ServiceError("Note not found", 404)
    return ref, n


def delete_note(user_id: str, group_id: str, note_id: str) -> None:
    _, _, m = _require_member(group_id, user_id)
    ref, n = _note(group_id, note_id)
    if n.get("author_id") != user_id and m.get("role") != "OWNER":
        raise ServiceError("Only the author or the group owner can remove this", 403)
    ref.update({"is_deleted": True, "removed_by": user_id, "removed_at": _now()})
    if n.get("material_id"):
        still_shared = [
            d for d in _db().collection(NOTES).where(filter=FieldFilter("material_id", "==", n["material_id"])).stream()
            if not (d.to_dict() or {}).get("is_deleted")
        ]
        if not still_shared:
            _db().collection(CourseMaterial.collection_name).document(n["material_id"]).update({"shared": False})


def note_download(user_id: str, group_id: str, note_id: str, sign: Optional[Callable] = None) -> Dict:
    _require_member(group_id, user_id)
    _, n = _note(group_id, note_id)
    if n.get("hidden") or n.get("kind") != "MATERIAL":
        raise ServiceError("Note not found", 404)
    snap = _db().collection(CourseMaterial.collection_name).document(n["material_id"]).get()
    data = snap.to_dict() if snap.exists else None
    if not data or data.get("is_deleted"):
        raise ServiceError("The author has removed this file", 410)
    material = CourseMaterial.from_dict(data, snap.id)
    url = (sign or material_service.download_url)(material)
    if not url:
        raise ServiceError("This file can't be opened right now", 503)
    return {"url": url, "title": material.title, "mime": getattr(material, "mime", None)}


# --- reports ------------------------------------------------------------------------------------------------------


def report(user_id: str, data: Dict) -> Dict:
    target_type, target_id, group_id = data.get("target_type"), data.get("target_id"), data.get("group_id")
    reason = data.get("reason")
    if target_type not in TARGET_TYPES:
        raise ServiceError(f"target_type must be one of: {', '.join(TARGET_TYPES)}")
    if reason not in REPORT_REASONS:
        raise ServiceError(f"reason must be one of: {', '.join(REPORT_REASONS)}")
    details = course_service._text(data, "details", 1000)
    _require_member(group_id, user_id)
    note_ref = None
    if target_type == "NOTE":
        note_ref, n = _note(group_id, target_id)
        if n.get("author_id") == user_id:
            raise ServiceError("You can't report your own post")
    elif target_type == "MEMBER":
        if not isinstance(target_id, str) or not _membership(group_id, target_id) or target_id == user_id:
            raise ServiceError("Member not found", 404)
    else:
        target_id = group_id
    open_reports = [
        d.to_dict() or {}
        for d in _db().collection(REPORTS).where(filter=FieldFilter("target_id", "==", target_id)).where(filter=FieldFilter("status", "==", "OPEN")).stream()
    ]
    if any(r.get("reporter_id") == user_id and r.get("target_type") == target_type for r in open_reports):
        raise ServiceError("You've already reported this. Thanks, it's being looked at.", 409)
    now = _now()
    doc = {
        "reporter_id": user_id,
        "group_id": group_id,
        "target_type": target_type,
        "target_id": target_id,
        "reason": reason,
        "details": details,
        "status": "OPEN",
        "resolution": None,
        "created_at": now,
    }
    ref = _db().collection(REPORTS).document()
    ref.set(doc)
    hidden = False
    if note_ref is not None and len({r.get("reporter_id") for r in open_reports} | {user_id}) >= AUTO_HIDE_REPORTS:
        note_ref.update({"hidden": True, "hidden_at": now})
        hidden = True
    return {"id": ref.id, "status": "OPEN", "hidden": hidden}
