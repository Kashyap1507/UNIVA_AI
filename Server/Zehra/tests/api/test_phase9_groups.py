"""
Phase 9 tests: study groups, shared notes and reports.
"""

import json
from datetime import datetime, timedelta

import jwt
import pytest

from app.services import firebase_service, material_service, study_group_service
from app.utils.decorators import clear_rate_limits
from tests.fakes import FakeFirestore


@pytest.fixture
def db(monkeypatch):
    fake = FakeFirestore()
    monkeypatch.setattr(firebase_service, "db", fake)
    monkeypatch.setattr(material_service, "download_url", lambda m: f"https://signed.example/{m.storage_path}")
    clear_rate_limits()
    return fake


def headers(app, uid):
    token = jwt.encode({"user_id": uid, "exp": datetime.utcnow() + timedelta(hours=1)}, app.config["JWT_SECRET_KEY"], algorithm="HS256")
    return {"Authorization": f"Bearer {token}"}


def user(db, uid, name="Priya Shah", university="University of Windsor"):
    db.collection("users").document(uid).set(
        {"email": f"{uid}@uni.edu", "name": name, "auth_provider": "email", "is_active": True, "student_profile": {"degree_level": "BACHELOR", "university": university}}
    )


class Api:
    def __init__(self, app, client):
        self.app, self.client = app, client

    def call(self, method, uid, url, body=None):
        res = getattr(self.client, method)(url, headers=headers(self.app, uid), json=body)
        return res.status_code, (res.get_json() or {})

    def create(self, uid, **body):
        body.setdefault("name", "COMP-3300 study group")
        status, data = self.call("post", uid, "/api/groups", body)
        assert status == 201, data
        return data["data"]


@pytest.fixture
def api(app, client):
    return Api(app, client)


class TestGroups:
    def test_create_join_by_code_and_members(self, api, db):
        user(db, "u1", "Priya Shah")
        user(db, "u2", "Alex")
        g = api.create("u1", course_code="comp 3300", description="Weekly problem sets")
        assert g["role"] == "OWNER" and g["member_count"] == 1 and len(g["invite_code"]) == 8 and g["course_code"] == "COMP 3300"
        assert db.docs("study_groups")[g["id"]]["university"] == "university of windsor"

        status, joined = api.call("post", "u2", "/api/groups/join", {"invite_code": g["invite_code"].lower()[:4] + "-" + g["invite_code"].lower()[4:]})
        assert status == 200 and joined["data"]["role"] == "MEMBER" and joined["data"]["member_count"] == 2
        assert api.call("post", "u2", "/api/groups/join", {"invite_code": g["invite_code"]})[1]["data"]["member_count"] == 2  # idempotent

        detail = api.call("get", "u2", f"/api/groups/{g['id']}")[1]["data"]
        assert [(m["display_name"], m["role"], m["is_me"]) for m in detail["members"]] == [("Priya S.", "OWNER", False), ("Alex", "MEMBER", True)]
        assert "u1@uni.edu" not in json.dumps(detail)
        assert [x["id"] for x in api.call("get", "u2", "/api/groups")[1]["data"]] == [g["id"]]
        assert api.call("post", "u2", "/api/groups/join", {"invite_code": "NOPE1234"})[0] == 404

    def test_discovery_is_opt_in_same_university_and_hides_codes(self, api, db):
        user(db, "owner")
        user(db, "same")
        user(db, "other", university="University of Toronto")
        hidden = api.create("owner", name="Private", course_code="COMP-3300")
        visible = api.create("owner", name="Open", course_code="COMP-3300", discoverable=True)
        status, found = api.call("get", "same", "/api/groups/discover?course_code=comp3300")
        assert status == 200 and [x["id"] for x in found["data"]] == [visible["id"]]
        assert "invite_code" not in found["data"][0] and hidden["id"] not in json.dumps(found)
        assert api.call("get", "other", "/api/groups/discover?course_code=COMP-3300")[1]["data"] == []
        assert api.call("post", "other", f"/api/groups/{visible['id']}/join")[0] == 404
        assert api.call("post", "same", f"/api/groups/{hidden['id']}/join")[0] == 404
        assert api.call("post", "same", f"/api/groups/{visible['id']}/join")[0] == 200
        assert api.call("get", "same", "/api/groups/discover?course_code=COMP-3300")[1]["data"] == []  # already a member
        assert api.call("get", "same", "/api/groups/discover?course_code=AB")[0] == 400

    def test_non_members_see_nothing_and_owner_only_actions(self, api, db):
        user(db, "u1")
        user(db, "u2")
        user(db, "u3")
        g = api.create("u1")
        api.call("post", "u2", "/api/groups/join", {"invite_code": g["invite_code"]})
        for method, url in [("get", f"/api/groups/{g['id']}"), ("get", f"/api/groups/{g['id']}/notes"), ("post", f"/api/groups/{g['id']}/leave")]:
            assert api.call(method, "u3", url)[0] == 404
        assert api.call("put", "u2", f"/api/groups/{g['id']}", {"name": "Mine now"})[0] == 403
        assert api.call("post", "u2", f"/api/groups/{g['id']}/invite-code")[0] == 403
        assert api.call("delete", "u2", f"/api/groups/{g['id']}")[0] == 403
        new = api.call("post", "u1", f"/api/groups/{g['id']}/invite-code")[1]["data"]["invite_code"]
        assert new != g["invite_code"] and api.call("post", "u3", "/api/groups/join", {"invite_code": g["invite_code"]})[0] == 404
        assert api.call("put", "u1", f"/api/groups/{g['id']}", {"discoverable": "yes"})[0] == 400

    def test_remove_member_leave_and_ownership_transfer(self, api, db):
        for u in ("u1", "u2", "u3"):
            user(db, u)
        g = api.create("u1")
        for u in ("u2", "u3"):
            api.call("post", u, "/api/groups/join", {"invite_code": g["invite_code"]})
        assert api.call("delete", "u1", f"/api/groups/{g['id']}/members/u3")[0] == 200
        assert api.call("get", "u3", f"/api/groups/{g['id']}")[0] == 404
        assert api.call("post", "u3", "/api/groups/join", {"invite_code": g["invite_code"]})[0] == 403  # removed stays removed
        assert api.call("post", "u1", f"/api/groups/{g['id']}/leave")[0] == 200
        detail = api.call("get", "u2", f"/api/groups/{g['id']}")[1]["data"]
        assert detail["role"] == "OWNER" and detail["member_count"] == 1
        assert api.call("post", "u2", f"/api/groups/{g['id']}/leave")[0] == 200
        assert db.docs("study_groups")[g["id"]]["is_deleted"] is True

    def test_limits_and_validation(self, api, db, monkeypatch):
        user(db, "u1")
        user(db, "u2")
        assert api.call("post", "u1", "/api/groups", {"name": ""})[0] == 400
        monkeypatch.setattr(study_group_service, "MAX_MEMBERS", 1)
        g = api.create("u1")
        status, data = api.call("post", "u2", "/api/groups/join", {"invite_code": g["invite_code"]})
        assert status == 400 and "full" in data["error"]


def material(db, uid, mid="m1", title="Week 3 notes"):
    db.collection("course_materials").document(mid).set({"user_id": uid, "course_id": "c1", "title": title, "kind": "NOTES", "storage_path": f"users/{uid}/m/{mid}.pdf", "mime": "application/pdf", "ingestion_status": "READY", "is_deleted": False})


class TestNotes:
    def setup_group(self, api, db):
        for u in ("u1", "u2", "u3", "u4"):
            user(db, u, name=f"Student {u}")
        g = api.create("u1")
        for u in ("u2", "u3", "u4"):
            api.call("post", u, "/api/groups/join", {"invite_code": g["invite_code"]})
        return g

    def test_text_and_material_notes(self, api, db):
        g = self.setup_group(api, db)
        material(db, "u2")
        status, post = api.call("post", "u2", f"/api/groups/{g['id']}/notes", {"kind": "TEXT", "body": "Meet Thursday 5pm in the library?"})
        assert status == 201 and post["data"]["author_name"] == "Student U." and post["data"]["is_mine"] is True
        status, share = api.call("post", "u2", f"/api/groups/{g['id']}/notes", {"kind": "MATERIAL", "material_id": "m1", "body": "My summary"})
        assert status == 201 and share["data"]["title"] == "Week 3 notes" and db.docs("course_materials")["m1"]["shared"] is True
        assert api.call("post", "u2", f"/api/groups/{g['id']}/notes", {"kind": "MATERIAL", "material_id": "m1"})[0] == 400  # once per group

        notes = api.call("get", "u3", f"/api/groups/{g['id']}/notes")[1]["data"]
        assert [n["kind"] for n in notes] == ["MATERIAL", "TEXT"] and notes[0]["can_remove"] is False
        dl = api.call("get", "u3", f"/api/groups/{g['id']}/notes/{share['data']['id']}/download")[1]["data"]
        assert dl["url"] == "https://signed.example/users/u2/m/m1.pdf"

        # Someone else's material can't be shared
        material(db, "u1", mid="m9")
        assert api.call("post", "u2", f"/api/groups/{g['id']}/notes", {"kind": "MATERIAL", "material_id": "m9"})[0] == 404
        assert api.call("post", "u2", f"/api/groups/{g['id']}/notes", {"kind": "TEXT", "body": ""})[0] == 400

        # Author deletes the file → download explains; removing the note unshares it
        db.collection("course_materials").document("m1").update({"is_deleted": True})
        assert api.call("get", "u3", f"/api/groups/{g['id']}/notes/{share['data']['id']}/download")[0] == 410
        assert api.call("delete", "u3", f"/api/groups/{g['id']}/notes/{share['data']['id']}")[0] == 403
        assert api.call("delete", "u1", f"/api/groups/{g['id']}/notes/{share['data']['id']}")[0] == 200  # owner can remove
        assert db.docs("course_materials")["m1"]["shared"] is False

    def test_outsiders_cannot_read_or_download(self, api, db):
        g = self.setup_group(api, db)
        user(db, "x")
        material(db, "u2")
        share = api.call("post", "u2", f"/api/groups/{g['id']}/notes", {"kind": "MATERIAL", "material_id": "m1"})[1]["data"]
        assert api.call("get", "x", f"/api/groups/{g['id']}/notes/{share['id']}/download")[0] == 404
        api.call("post", "u3", f"/api/groups/{g['id']}/leave")
        assert api.call("get", "u3", f"/api/groups/{g['id']}/notes/{share['id']}/download")[0] == 404


class TestReports:
    def test_three_reports_hide_a_note(self, api, db):
        for u in ("u1", "u2", "u3", "u4", "u5"):
            user(db, u)
        g = api.create("u1")
        for u in ("u2", "u3", "u4", "u5"):
            api.call("post", u, "/api/groups/join", {"invite_code": g["invite_code"]})
        note = api.call("post", "u5", f"/api/groups/{g['id']}/notes", {"kind": "TEXT", "body": "Here are the answers to assignment 2"})[1]["data"]
        body = {"group_id": g["id"], "target_type": "NOTE", "target_id": note["id"], "reason": "ACADEMIC_INTEGRITY"}
        assert api.call("post", "u2", "/api/reports", body)[1]["data"]["hidden"] is False
        assert api.call("post", "u2", "/api/reports", body)[0] == 409  # once per person
        assert api.call("post", "u5", "/api/reports", body)[0] == 400  # not your own post
        assert api.call("post", "u3", "/api/reports", body)[1]["data"]["hidden"] is False
        assert api.call("post", "u4", "/api/reports", body)[1]["data"]["hidden"] is True
        assert note["id"] not in [n["id"] for n in api.call("get", "u1", f"/api/groups/{g['id']}/notes")[1]["data"]]
        reports = db.docs("content_reports")
        assert len(reports) == 3 and all(r["status"] == "OPEN" for r in reports.values())

    def test_member_and_group_reports_and_validation(self, api, db):
        for u in ("u1", "u2", "x"):
            user(db, u)
        g = api.create("u1")
        api.call("post", "u2", "/api/groups/join", {"invite_code": g["invite_code"]})
        assert api.call("post", "u2", "/api/reports", {"group_id": g["id"], "target_type": "MEMBER", "target_id": "u1", "reason": "SPAM"})[0] == 201
        assert api.call("post", "u2", "/api/reports", {"group_id": g["id"], "target_type": "GROUP", "target_id": "whatever", "reason": "OTHER", "details": "Off-topic"})[0] == 201
        assert db.docs("content_reports") and any(r["target_id"] == g["id"] for r in db.docs("content_reports").values())
        assert api.call("post", "u2", "/api/reports", {"group_id": g["id"], "target_type": "MEMBER", "target_id": "u2", "reason": "SPAM"})[0] == 404
        assert api.call("post", "u2", "/api/reports", {"group_id": g["id"], "target_type": "NOTE", "target_id": "n", "reason": "RUDE"})[0] == 400
        assert api.call("post", "x", "/api/reports", {"group_id": g["id"], "target_type": "GROUP", "target_id": g["id"], "reason": "SPAM"})[0] == 404


def test_display_names():
    assert study_group_service.display_name({"name": "Priya Anand Shah"}) == "Priya S."
    assert study_group_service.display_name({"name": "Alex"}) == "Alex"
    assert study_group_service.display_name({}) == "Student"
    assert study_group_service.normalise_code("comp - 3300") == "COMP3300"
