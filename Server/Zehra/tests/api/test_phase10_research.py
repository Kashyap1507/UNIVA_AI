"""
Phase 10 tests: citations, research projects and papers, analysis, comparison,
writing feedback, milestones and lecture summaries. Gemini and Storage are faked.
"""

import io
from datetime import date, datetime, timedelta, timezone

import jwt
import pytest

from app.ai_framework.workflows import research_workflows as rw
from app.services import citation_service as cs
from app.services import deadline_service, firebase_service, material_service, research_service
from app.utils.decorators import clear_rate_limits
from tests.fakes import FakeFirestore

VASWANI = {"title": "Attention Is All You Need", "authors": ["Ashish Vaswani", "Noam Shazeer", "Niki Parmar"], "year": 2017, "venue": "Advances in Neural Information Processing Systems", "type": "CONFERENCE", "doi": "https://doi.org/10.48550/arXiv.1706.03762"}
LECUN = {"title": "Deep learning & the brain: 50% of it", "authors": ["LeCun, Yann", "Bengio, Yoshua", "Geoffrey E. Hinton"], "year": 2015, "venue": "Nature", "type": "ARTICLE", "volume": "521", "issue": "7553", "pages": "436-444", "doi": "10.1038/nature14539"}


# --- citations -----------------------------------------------------------------------------------------


class TestCitations:
    def test_bibtex(self):
        assert cs.bibtex(LECUN) == (
            "@article{lecun2015deep,\n"
            "  author = {LeCun, Yann and Bengio, Yoshua and Hinton, Geoffrey E.},\n"
            "  title = {{Deep learning \\& the brain: 50\\% of it}},\n"
            "  journal = {Nature},\n  volume = {521},\n  number = {7553},\n  pages = {436--444},\n"
            "  year = {2015},\n  doi = {10.1038/nature14539}\n}"
        )
        assert cs.bibtex(VASWANI).startswith("@inproceedings{vaswani2017attention,") and "booktitle = {Advances" in cs.bibtex(VASWANI)

    def test_apa(self):
        assert cs.apa(LECUN) == "LeCun, Y., Bengio, Y., & Hinton, G. E. (2015). Deep learning & the brain: 50% of it. Nature, 521(7553), 436–444. https://doi.org/10.1038/nature14539"
        assert cs.apa(VASWANI) == "Vaswani, A., Shazeer, N., & Parmar, N. (2017). Attention Is All You Need. In Advances in Neural Information Processing Systems. https://doi.org/10.48550/arXiv.1706.03762"
        assert cs.apa({"title": "Notes"}) == "Notes. (n.d.)."
        two = cs.apa({"title": "X", "authors": ["Ada Lovelace", "Charles Babbage"], "year": 2020})
        assert two.startswith("Lovelace, A., & Babbage, C. (2020).")
        many = cs.apa({"title": "Big", "authors": [f"Person{i} Author" for i in range(25)], "year": 2020})
        assert "Author, P., . . . Author, P. (2020)." in many and many.count("Author, P.") == 20

    def test_names_keys_and_export_order(self):
        assert cs.split_name("Ludwig van Beethoven") == ("van Beethoven", "Ludwig")
        assert cs.initials("Jean-Paul") == "J.-P."
        assert cs.doi_url("doi: 10.1/x") == "https://doi.org/10.1/x" and cs.doi_url("nope") is None
        text = cs.export([VASWANI, LECUN, {**LECUN, "title": "Deep nets again"}], "bibtex")
        assert text.index("lecun2015deep,") < text.index("lecun2015deepa,") < text.index("vaswani2017attention,")
        assert cs.export([VASWANI, LECUN], "apa").startswith("LeCun")
        with pytest.raises(ValueError):
            cs.export([], "mla")


# --- API fixtures ---------------------------------------------------------------------------------------------


@pytest.fixture
def db(monkeypatch):
    fake = FakeFirestore()
    monkeypatch.setattr(firebase_service, "db", fake)
    monkeypatch.setattr(research_service, "_upload", lambda path, data, mime: None)
    monkeypatch.setattr(material_service, "download_url", lambda m: f"https://signed.example/{m.storage_path}")
    monkeypatch.setattr(deadline_service, "_now", lambda: datetime(2026, 9, 28, tzinfo=timezone.utc))
    clear_rate_limits()
    fake.collection("users").document("u1").set({"email": "u1@uni.edu", "auth_provider": "email", "is_active": True, "student_profile": {"degree_level": "MASTER"}})
    fake.collection("users").document("u2").set({"email": "u2@uni.edu", "auth_provider": "email", "is_active": True, "student_profile": {"degree_level": "MASTER"}})
    return fake


def headers(app, uid="u1"):
    token = jwt.encode({"user_id": uid, "exp": datetime.utcnow() + timedelta(hours=1)}, app.config["JWT_SECRET_KEY"], algorithm="HS256")
    return {"Authorization": f"Bearer {token}"}


def call(client, app, method, url, body=None, uid="u1"):
    res = getattr(client, method)(url, headers=headers(app, uid), json=body)
    return res.status_code, (res.get_json() or {})


def upload(client, app, content=b"Attention is all you need. We propose the Transformer.", name="vaswani_2017.txt", project_id=None, uid="u1"):
    data = {"file": (io.BytesIO(content), name)}
    if project_id:
        data["project_id"] = project_id
    res = client.post("/api/research/papers", headers=headers(app, uid), data=data, content_type="multipart/form-data")
    return res.status_code, res.get_json()


ANALYSIS = rw.PaperAnalysis(
    details=rw.PaperDetails(title="Attention Is All You Need", authors=["Ashish Vaswani", "Noam Shazeer"], year=2017, venue="NeurIPS", doi="10.48550/arXiv.1706.03762", type="CONFERENCE"),
    summary=rw.PaperSummary(tldr="Transformers replace recurrence with attention.", research_question="Can attention alone model sequences?", methods="Encoder-decoder with multi-head attention", data="WMT 2014", findings=["New BLEU state of the art", "Faster training"], limitations=["Quadratic cost in length"], keywords=["attention"]),
)


class TestProjectsAndPapers:
    def test_project_paper_upload_analyse_and_download(self, app, client, db, monkeypatch):
        status, project = call(client, app, "post", "/api/research/projects", {"title": "Efficient transformers thesis", "kind": "THESIS", "due_date": "2027-04-30"})
        assert status == 201
        pid = project["data"]["id"]
        status, paper = upload(client, app, project_id=pid)
        assert status == 201 and paper["data"]["status"] == "NEW" and paper["data"]["title"] == "vaswani 2017"
        paper_id = paper["data"]["id"]
        assert "Transformer" in db.docs("paper_texts")[paper_id]["text"]
        assert db.docs("papers")[paper_id]["storage_path"].startswith("users/u1/research/papers/")

        seen = {}
        monkeypatch.setattr(rw, "default_analyse", lambda prompt: seen.setdefault("prompt", prompt) and ANALYSIS)
        status, analysed = call(client, app, "post", f"/api/research/papers/{paper_id}/analyze")
        d = analysed["data"]
        assert status == 200 and d["status"] == "ANALYSED" and d["title"] == "Attention Is All You Need"
        assert d["authors"] == ["Ashish Vaswani", "Noam Shazeer"] and d["year"] == 2017 and d["type"] == "CONFERENCE"
        assert d["summary"]["findings"] == ["New BLEU state of the art", "Faster training"] and d["citation"] == "Vaswani & Shazeer (2017)"
        assert "We propose the Transformer" in seen["prompt"]

        # The student's own details win on re-analysis
        call(client, app, "put", f"/api/research/papers/{paper_id}", {"title": "My title", "year": 2018})
        d = call(client, app, "post", f"/api/research/papers/{paper_id}/analyze")[1]["data"]
        assert d["title"] == "My title" and d["year"] == 2018

        assert call(client, app, "get", f"/api/research/papers/{paper_id}/download")[1]["data"]["url"].startswith("https://signed.example/users/u1/research/")
        listed = call(client, app, "get", "/api/research/projects")[1]["data"]
        assert listed[0]["paper_count"] == 1

    def test_manual_paper_validation_and_no_text(self, app, client, db):
        assert call(client, app, "post", "/api/research/papers", {"title": "T", "authors": ["A B"], "year": 2020, "type": "ARTICLE", "doi": "10.1/x"})[0] == 201
        for bad in [{"authors": ["A"]}, {"title": "T", "year": "2020"}, {"title": "T", "type": "BLOG"}, {"title": "T", "doi": "abc"}, {"title": "T", "url": "ftp://x"}, {"title": "T", "authors": "A B"}]:
            assert call(client, app, "post", "/api/research/papers", bad)[0] == 400, bad
        manual = call(client, app, "post", "/api/research/papers", {"title": "Offline book", "type": "BOOK"})[1]["data"]
        status, err = call(client, app, "post", f"/api/research/papers/{manual['id']}/analyze")
        assert status == 409 and "No text" in err["error"]
        assert upload(client, app, content=b"x", name="slides.pptx")[0] == 400
        assert upload(client, app, content=b"", name="empty.txt")[0] == 400

    def test_analysis_failure_is_friendly(self, app, client, db, monkeypatch):
        paper_id = upload(client, app)[1]["data"]["id"]

        def boom(prompt):
            raise RuntimeError("API key not valid")

        monkeypatch.setattr(rw, "default_analyse", boom)
        status, err = call(client, app, "post", f"/api/research/papers/{paper_id}/analyze")
        assert status == 502 and "key" not in err["error"]

    def test_ownership(self, app, client, db):
        pid = call(client, app, "post", "/api/research/projects", {"title": "Mine"})[1]["data"]["id"]
        paper_id = upload(client, app)[1]["data"]["id"]
        for method, url in [("get", f"/api/research/projects/{pid}"), ("get", f"/api/research/papers/{paper_id}"), ("post", f"/api/research/papers/{paper_id}/analyze"),
                            ("get", f"/api/research/papers/{paper_id}/download"), ("get", f"/api/research/projects/{pid}/milestones"), ("delete", f"/api/research/papers/{paper_id}")]:
            assert call(client, app, method, url, uid="u2")[0] == 404, url
        assert call(client, app, "post", "/api/research/papers", {"title": "x", "project_id": pid}, uid="u2")[0] == 404
        assert call(client, app, "get", "/api/research/papers", uid="u2")[1]["data"] == []


class TestCompareAndExport:
    def make(self, db, pid, title, authors, year, summary=True):
        ref = db.collection("papers").document()
        ref.set({"user_id": "u1", "project_id": pid, "title": title, "authors": authors, "year": year, "type": "ARTICLE", "status": "ANALYSED" if summary else "NEW",
                 "summary": {"research_question": f"Q {title}", "methods": "M", "data": "D", "findings": ["F1", "F2"], "limitations": ["L"]} if summary else None, "is_deleted": False})
        return ref.id

    def test_table_synthesis_and_fallback(self, app, client, db, monkeypatch):
        a = self.make(db, None, "Paper A", ["Ada Lovelace"], 2020)
        b = self.make(db, None, "Paper B", ["Alan Turing", "Grace Hopper", "Kurt Godel"], 2021)
        seen = {}
        monkeypatch.setattr(rw, "default_synthesise", lambda p: seen.setdefault("p", p) and rw.Synthesis(themes=["Attention"], gaps=["Long inputs"], paragraph="[1] and [2] agree."))
        status, res = call(client, app, "post", "/api/research/compare", {"paper_ids": [a, b], "focus": "efficiency"})
        data = res["data"]
        assert status == 200 and [r["citation"] for r in data["rows"]] == ["Lovelace (2020)", "Turing et al. (2021)"]
        assert data["rows"][0]["findings"] == "F1; F2" and data["synthesis"]["gaps"] == ["Long inputs"] and data["note"] is None
        assert "on efficiency" in seen["p"] and "[2] Paper B" in seen["p"]

        def boom(p):
            raise RuntimeError("down")

        monkeypatch.setattr(rw, "default_synthesise", boom)
        data = call(client, app, "post", "/api/research/compare", {"paper_ids": [a, b]})[1]["data"]
        assert data["synthesis"] is None and "table is ready" in data["note"] and len(data["rows"]) == 2

    def test_compare_validation(self, app, client, db):
        a = self.make(db, None, "A", ["X Y"], 2020)
        c = self.make(db, None, "C", ["X Y"], 2020, summary=False)
        assert call(client, app, "post", "/api/research/compare", {"paper_ids": [a]})[0] == 400
        assert call(client, app, "post", "/api/research/compare", {"paper_ids": [a, a]})[0] == 400
        status, err = call(client, app, "post", "/api/research/compare", {"paper_ids": [a, c]})
        assert status == 409 and "Analyse these papers first: C" in err["error"]
        assert call(client, app, "post", "/api/research/compare", {"paper_ids": [a, "zz"]})[0] == 404

    def test_export_by_project_and_ids(self, app, client, db):
        pid = call(client, app, "post", "/api/research/projects", {"title": "P"})[1]["data"]["id"]
        a = self.make(db, pid, "Alpha", ["Ada Lovelace"], 2020)
        self.make(db, None, "Beta", ["Alan Turing"], 2021)
        data = call(client, app, "get", f"/api/research/export?format=apa&project_id={pid}")[1]["data"]
        assert data["count"] == 1 and data["text"].startswith("Lovelace, A. (2020). Alpha.")
        data = call(client, app, "get", "/api/research/export?format=bibtex")[1]["data"]
        assert data["count"] == 2 and "@article{lovelace2020alpha," in data["text"]
        assert call(client, app, "get", f"/api/research/export?format=bibtex&paper_ids={a}")[1]["data"]["count"] == 1
        assert call(client, app, "get", "/api/research/export?format=mla")[0] == 400


class TestWritingFeedback:
    TEXT = ("Transformers have changed natural language processing. In this review I argue that efficient attention is the key open problem. "
            "Many papers show improvements but they are not compared fairly. This essay will look at three families of methods and their trade-offs. " * 2)

    def test_feedback_is_guarded(self, app, client, db, monkeypatch):
        seen = {}
        fake = rw.WritingFeedback(
            overall="Clear aim, thin evidence.",
            strengths=["Clear thesis statement"],
            issues=[
                rw.WritingIssue(quote="they are not compared fairly", problem="Unsupported claim", suggestion="Cite the benchmarks that show this", category="EVIDENCE"),
                rw.WritingIssue(quote="A completely rewritten sentence that is not in the text", problem="Style", suggestion="x" * 1000, category="style"),
            ],
        )
        monkeypatch.setattr(rw, "default_writing", lambda p: seen.setdefault("p", p) and fake)
        status, res = call(client, app, "post", "/api/research/writing-feedback", {"text": self.TEXT, "kind": "LIT_REVIEW"})
        data = res["data"]
        assert status == 200 and data["kind"] == "LIT_REVIEW"
        assert data["issues"][0]["quote"] == "they are not compared fairly"
        assert data["issues"][1]["quote"] == "" and len(data["issues"][1]["suggestion"]) <= rw.MAX_SUGGESTION_CHARS + 1 and data["issues"][1]["category"] == "STYLE"
        assert "Never rewrite" in seen["p"] and "Master's student's literature review" in seen["p"]
        assert "rewritten" not in data and "rewrite" not in data

    def test_validation_and_failure(self, app, client, db, monkeypatch):
        assert call(client, app, "post", "/api/research/writing-feedback", {"text": "too short", "kind": "ESSAY"})[0] == 400
        assert call(client, app, "post", "/api/research/writing-feedback", {"text": self.TEXT, "kind": "POEM"})[0] == 400
        assert call(client, app, "post", "/api/research/writing-feedback", {"text": "x" * 60_001, "kind": "ESSAY"})[0] == 400

        def boom(p):
            raise RuntimeError("quota")

        monkeypatch.setattr(rw, "default_writing", boom)
        assert call(client, app, "post", "/api/research/writing-feedback", {"text": self.TEXT, "kind": "ESSAY"})[0] == 502


class TestMilestones:
    def test_template_spread_and_replace(self, app, client, db):
        pid = call(client, app, "post", "/api/research/projects", {"title": "Thesis", "kind": "THESIS"})[1]["data"]["id"]
        assert call(client, app, "post", f"/api/research/projects/{pid}/milestones/suggest", {})[0] == 400  # no due date yet
        status, res = call(client, app, "post", f"/api/research/projects/{pid}/milestones/suggest", {"due_date": "2027-04-30", "start_date": "2026-09-30"})
        ms = res["data"]
        assert status == 201 and len(ms) == 8 and ms[0]["title"] == "Research proposal approved" and ms[-1]["due_at"] == "2027-04-30"
        assert ms[0]["due_at"] == (date(2026, 9, 30) + timedelta(days=round(0.10 * 212))).isoformat()
        assert call(client, app, "get", f"/api/research/projects/{pid}")[1]["data"]["due_date"] == "2027-04-30"

        own = call(client, app, "post", f"/api/research/projects/{pid}/milestones", {"title": "Ethics approval", "due_at": "2026-11-01"})[1]["data"]
        done = call(client, app, "put", f"/api/research/milestones/{ms[0]['id']}", {"status": "DONE"})[1]["data"]
        assert done["status"] == "DONE"
        call(client, app, "post", f"/api/research/projects/{pid}/milestones/suggest", {"start_date": "2026-10-15"})
        titles = [m["title"] for m in call(client, app, "get", f"/api/research/projects/{pid}/milestones")[1]["data"]]
        assert "Ethics approval" in titles and titles.count("Research proposal approved") == 2  # the DONE one stays, a new TODO one added
        assert len(titles) == 1 + 8 + 1
        assert call(client, app, "put", f"/api/research/milestones/{own['id']}", {"status": "LATE"})[0] == 400
        assert call(client, app, "delete", f"/api/research/milestones/{own['id']}")[0] == 200

    def test_short_timeline_and_kinds(self, app, client, db):
        pid = call(client, app, "post", "/api/research/projects", {"title": "Essay", "kind": "COURSEWORK", "due_date": "2026-10-02"})[1]["data"]["id"]
        assert call(client, app, "post", f"/api/research/projects/{pid}/milestones/suggest", {})[0] == 400  # under a week
        assert call(client, app, "post", "/api/research/projects", {"title": "X", "kind": "PHD"})[0] == 400
        assert [t for t, _ in research_service.template_dates("PROJECT", date(2026, 1, 1), date(2026, 1, 11))][0] == "Scope agreed with supervisor"


class TestLectureSummary:
    def test_summary_saved_on_material(self, app, client, db, monkeypatch):
        cid = call(client, app, "post", "/api/courses", {"name": "Operating Systems", "code": "COMP-3300"})[1]["data"]["id"]
        db.collection("course_materials").document("m1").set({"user_id": "u1", "course_id": cid, "title": "Week 5 slides", "kind": "SLIDES", "ingestion_status": "READY", "is_deleted": False})
        db.collection("material_chunks").document("c0").set({"user_id": "u1", "course_id": cid, "material_id": "m1", "text": "Paging splits memory into frames.", "chunk_index": 0})
        seen = {}
        monkeypatch.setattr(rw, "default_lecture", lambda p: seen.setdefault("p", p) and rw.LectureSummary(overview="Paging.", key_points=["Frames", " "], terms=[{"term": "TLB", "definition": "Cache of page table entries"}, {"definition": "no term"}], exam_questions=["Why use a TLB?"]))
        status, res = call(client, app, "post", "/api/materials/m1/summary")
        s = res["data"]["summary"]
        assert status == 200 and s["key_points"] == ["Frames"] and s["terms"] == [{"term": "TLB", "definition": "Cache of page table entries"}]
        assert "Paging splits memory" in seen["p"] and "Operating Systems" in seen["p"]
        assert db.docs("course_materials")["m1"]["summary"]["overview"] == "Paging."
        detail = call(client, app, "get", "/api/materials/m1")[1]["data"]
        assert detail["summary"]["exam_questions"] == ["Why use a TLB?"]
        assert call(client, app, "post", "/api/materials/m1/summary", uid="u2")[0] == 404
        db.collection("course_materials").document("m1").update({"ingestion_status": "PROCESSING"})
        assert call(client, app, "post", "/api/materials/m1/summary")[0] == 409
