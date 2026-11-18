"""
Phase 2 tests: courses, materials, extraction/chunking, ingestion, retrieval
and course structure generation. Firestore, Storage, Gemini and embeddings
are faked.
"""

import io
from datetime import datetime, timedelta

import jwt
import pytest

from app.ai_framework.workflows import course_structure_generation as csg
from app.ai_framework.workflows.document_ingestion import IngestionDeps, ingest_material
from app.models.course import MATERIAL_CHUNKS, CourseMaterial
from app.services import firebase_service, material_service, retrieval_service
from app.services.material_extraction import (
    PDF_MIME,
    PPTX_MIME,
    ExtractionError,
    Segment,
    chunk_segments,
    extract_segments,
    mime_for_filename,
    parse_ocr_pages,
)
from app.utils.decorators import clear_rate_limits
from tests.fakes import FakeFirestore

# --- helpers ------------------------------------------------------------------------

VOCAB = ["bayes", "probability", "gradient", "descent", "matrix", "eigenvalue", "graph", "tree"]


def fake_embed_text(text: str):
    """Deterministic bag-of-words vector over a tiny vocabulary (plus a bias term)."""
    lower = text.lower()
    return [float(lower.count(w)) for w in VOCAB] + [0.01]


def fake_embed(texts):
    return [fake_embed_text(t) for t in texts]


def make_pdf(pages):
    """Minimal valid PDF with one line of Helvetica text per page."""
    objects = ["<< /Type /Catalog /Pages 2 0 R >>", None, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"]
    kids = []
    for text in pages:
        stream = f"BT /F1 12 Tf 50 750 Td ({text}) Tj ET".encode()
        objects.append(f"<< /Length {len(stream)} >>\nstream\n{stream.decode()}\nendstream")
        content_id = len(objects)
        objects.append(
            f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
            f"/Resources << /Font << /F1 3 0 R >> >> /Contents {content_id} 0 R >>"
        )
        kids.append(f"{len(objects)} 0 R")
    objects[1] = f"<< /Type /Pages /Kids [{' '.join(kids)}] /Count {len(kids)} >>"
    out = io.BytesIO()
    out.write(b"%PDF-1.4\n")
    offsets = []
    for i, obj in enumerate(objects, start=1):
        offsets.append(out.tell())
        out.write(f"{i} 0 obj\n{obj}\nendobj\n".encode())
    xref = out.tell()
    out.write(f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode())
    for off in offsets:
        out.write(f"{off:010d} 00000 n \n".encode())
    out.write(f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode())
    return out.getvalue()


def make_pptx(slides):
    from pptx import Presentation

    deck = Presentation()
    for title, notes in slides:
        slide = deck.slides.add_slide(deck.slide_layouts[1])
        slide.shapes.title.text = title
        if notes:
            slide.notes_slide.notes_text_frame.text = notes
    buf = io.BytesIO()
    deck.save(buf)
    return buf.getvalue()


@pytest.fixture
def db(monkeypatch):
    fake = FakeFirestore()
    monkeypatch.setattr(firebase_service, "db", fake)
    clear_rate_limits()
    return fake


def make_user(db, uid="u1", level="MASTER"):
    db.collection("users").document(uid).set(
        {"email": f"{uid}@uni.edu", "auth_provider": "email", "is_active": True, "student_profile": {"degree_level": level}}
    )
    return uid


def headers(app, uid):
    token = jwt.encode({"user_id": uid, "exp": datetime.utcnow() + timedelta(hours=1)}, app.config["JWT_SECRET_KEY"], algorithm="HS256")
    return {"Authorization": f"Bearer {token}"}


def create_course(client, app, uid, **data):
    res = client.post("/api/courses", headers=headers(app, uid), json={"name": "Machine Learning", "code": "COMP-8740", **data})
    assert res.status_code == 201, res.get_json()
    return res.get_json()["data"]


# --- extraction & chunking ---------------------------------------------------------


class TestExtraction:
    def test_mime_from_extension_wins(self):
        assert mime_for_filename("slides.PPTX", "application/octet-stream") == PPTX_MIME
        assert mime_for_filename("notes.md") == "text/markdown"
        assert mime_for_filename("virus.exe", "application/x-msdownload") is None

    def test_pdf_pages(self):
        text = "Bayes theorem relates conditional probability to prior belief and evidence observed."
        segments = extract_segments(make_pdf([text, "Gradient descent minimises a loss by following the negative gradient."]), PDF_MIME)
        assert [s.page for s in segments] == [1, 2]
        assert "Bayes theorem" in segments[0].text

    def test_scanned_pdf_uses_ocr(self):
        calls = {}

        def ocr(prompt, image_data=None, mime_type=None):
            calls["mime"] = mime_type
            return "=== Page 1 ===\nHandwritten intro\n=== Page 2 ===\nEigenvalue notes"

        segments = extract_segments(make_pdf(["", ""]), PDF_MIME, ocr=ocr)
        assert calls["mime"] == PDF_MIME
        assert [(s.page, s.text) for s in segments] == [(1, "Handwritten intro"), (2, "Eigenvalue notes")]

    def test_scanned_pdf_without_ocr_fails_clearly(self):
        with pytest.raises(ExtractionError):
            extract_segments(make_pdf([""]), PDF_MIME)

    def test_pptx_slides_and_notes(self):
        segments = extract_segments(make_pptx([("Intro to graphs", "Mention trees"), ("Matrices", None)]), PPTX_MIME)
        assert [s.page for s in segments] == [1, 2]
        assert "Speaker notes: Mention trees" in segments[0].text

    def test_image_requires_ocr(self):
        with pytest.raises(ExtractionError):
            extract_segments(b"\x89PNG", "image/png")
        assert extract_segments(b"\x89PNG", "image/png", ocr=lambda *a, **k: "Board photo")[0].text == "Board photo"

    def test_parse_ocr_without_markers(self):
        assert parse_ocr_pages("just text")[0].page == 1


class TestChunking:
    def test_overlap_and_pages(self):
        segments = [Segment("a " * 700, 1), Segment("b " * 700, 2)]
        chunks = chunk_segments(segments, size=1000, overlap=200)
        assert len(chunks) >= 3
        assert chunks[0].page == 1 and chunks[-1].page_end == 2
        # consecutive chunks overlap
        assert chunks[0].text[-50:].strip() in chunks[1].text
        assert all(len(c.text) <= 1000 for c in chunks)

    def test_small_segments_merge(self):
        chunks = chunk_segments([Segment("Slide one", 1), Segment("Slide two", 2)])
        assert len(chunks) == 1 and (chunks[0].page, chunks[0].page_end) == (1, 2)

    def test_empty(self):
        assert chunk_segments([Segment("", 1)]) == []

    def test_bad_overlap(self):
        with pytest.raises(ValueError):
            chunk_segments([Segment("x", 1)], size=10, overlap=10)


# --- ingestion ----------------------------------------------------------------------


def seed_material(db, uid="u1", course_id="c1", mime="text/plain", title="Lecture 1", kind="NOTES"):
    m = CourseMaterial(user_id=uid, course_id=course_id, kind=kind, title=title, mime=mime, storage_path=f"users/{uid}/x/{title}")
    m.save(db)
    return m


class TestIngestion:
    def deps(self, db, data: bytes, embed=fake_embed):
        return IngestionDeps(download=lambda path: data, ocr=None, embed=embed, db=db)

    def test_ready_with_chunks(self, db):
        m = seed_material(db)
        text = ("Bayes rule and probability. " * 60).encode()
        result = ingest_material(m.id, self.deps(db, text))
        assert result["success"], result
        doc = db.docs("course_materials")[m.id]
        assert doc["ingestion_status"] == "READY" and doc["chunk_count"] == result["chunk_count"] > 0
        chunks = [c for c in db.docs(MATERIAL_CHUNKS).values() if c["material_id"] == m.id]
        assert len(chunks) == result["chunk_count"]
        assert {c["user_id"] for c in chunks} == {"u1"} and {c["course_id"] for c in chunks} == {"c1"}
        assert len(list(chunks[0]["embedding"])) == len(VOCAB) + 1

    def test_reingest_replaces_chunks(self, db):
        m = seed_material(db)
        ingest_material(m.id, self.deps(db, ("graph " * 800).encode()))
        first = len(db.docs(MATERIAL_CHUNKS))
        ingest_material(m.id, self.deps(db, b"tree only"))
        assert first > 1 and len(db.docs(MATERIAL_CHUNKS)) == 1

    def test_unreadable_file_fails_with_message(self, db):
        m = seed_material(db, mime=PDF_MIME)
        result = ingest_material(m.id, self.deps(db, make_pdf([""])))
        assert not result["success"]
        doc = db.docs("course_materials")[m.id]
        assert doc["ingestion_status"] == "FAILED" and "OCR" in doc["error"]

    def test_embedding_error_is_generic(self, db):
        m = seed_material(db)

        def boom(texts):
            raise RuntimeError("quota exceeded: key=secret")

        ingest_material(m.id, self.deps(db, b"some text", embed=boom))
        doc = db.docs("course_materials")[m.id]
        assert doc["ingestion_status"] == "FAILED" and "secret" not in doc["error"]


# --- retrieval ----------------------------------------------------------------------


class TestRetrieval:
    def test_scoped_to_user_and_course(self, db):
        deps = IngestionDeps(download=lambda p: p.encode(), ocr=None, embed=fake_embed, db=db)
        mine = seed_material(db, uid="u1", course_id="c1", title="bayes probability bayes")
        other_course = seed_material(db, uid="u1", course_id="c2", title="bayes bayes bayes")
        other_user = seed_material(db, uid="u2", course_id="c1", title="bayes bayes probability")
        for m in (mine, other_course, other_user):
            # download() returns the storage path, which ends with the title text
            ingest_material(m.id, deps)

        results = retrieval_service.search("u1", "c1", "bayes", k=5, embed_query=fake_embed_text, db=db)
        assert [r.material_id for r in results] == [mine.id]

    def test_orders_by_similarity(self, db):
        deps = IngestionDeps(download=lambda p: p.encode(), ocr=None, embed=fake_embed, db=db)
        a = seed_material(db, title="gradient descent gradient")
        b = seed_material(db, title="matrix eigenvalue")
        for m in (a, b):
            ingest_material(m.id, deps)
        results = retrieval_service.search("u1", "c1", "eigenvalue of a matrix", embed_query=fake_embed_text, db=db)
        assert results[0].material_id == b.id
        assert results[0].to_response()["score"] > results[1].to_response()["score"]

    def test_empty_query(self, db):
        assert retrieval_service.search("u1", "c1", "   ", embed_query=fake_embed_text, db=db) == []


# --- API ----------------------------------------------------------------------------


class TestCoursesAPI:
    def test_requires_auth(self, client, db):
        assert client.get("/api/courses").status_code == 401

    def test_semester_and_course_crud(self, app, client, db):
        uid = make_user(db)
        h = headers(app, uid)
        sem = client.post("/api/semesters", headers=h, json={"name": "Fall 2026", "start_date": "2026-09-01", "end_date": "2026-12-20", "is_current": True})
        assert sem.status_code == 201, sem.get_json()
        sem_id = sem.get_json()["data"]["id"]

        course = create_course(client, app, uid, semester_id=sem_id, exam_dates=["2026-12-10", "2026-10-15"], color="#4F46E5")
        assert course["exam_dates"] == ["2026-10-15", "2026-12-10"]
        assert db.docs("users")[uid]["student_profile.enrolled_course_ids"] == [course["id"]]

        listed = client.get(f"/api/courses?semester_id={sem_id}", headers=h).get_json()["data"]
        assert [c["id"] for c in listed] == [course["id"]]

        res = client.put(f"/api/courses/{course['id']}", headers=h, json={"status": "ARCHIVED"})
        assert res.get_json()["data"]["status"] == "ARCHIVED"
        assert client.get("/api/courses?include_archived=false", headers=h).get_json()["data"] == []

        assert client.delete(f"/api/courses/{course['id']}", headers=h).status_code == 200
        assert client.get(f"/api/courses/{course['id']}", headers=h).status_code == 404

    @pytest.mark.parametrize(
        "body",
        [{}, {"name": " "}, {"name": "X", "color": "blue"}, {"name": "X", "exam_dates": ["tomorrow"]}, {"name": "X", "status": "DONE"}],
    )
    def test_course_validation(self, app, client, db, body):
        uid = make_user(db)
        assert client.post("/api/courses", headers=headers(app, uid), json=body).status_code == 400

    def test_semester_date_order(self, app, client, db):
        uid = make_user(db)
        res = client.post("/api/semesters", headers=headers(app, uid), json={"name": "S", "start_date": "2026-12-01", "end_date": "2026-09-01"})
        assert res.status_code == 400

    def test_other_users_course_is_404(self, app, client, db):
        owner, intruder = make_user(db, "u1"), make_user(db, "u2")
        course = create_course(client, app, owner)
        h = headers(app, intruder)
        assert client.get(f"/api/courses/{course['id']}", headers=h).status_code == 404
        assert client.put(f"/api/courses/{course['id']}", headers=h, json={"name": "Mine"}).status_code == 404
        assert client.get(f"/api/courses/{course['id']}/materials", headers=h).status_code == 404
        assert client.get(f"/api/courses/{course['id']}/search?q=bayes", headers=h).status_code == 404
        assert client.post("/api/courses", headers=h, json={"name": "X", "semester_id": "not-mine"}).status_code == 404


class TestMaterialsAPI:
    @pytest.fixture
    def uploads(self, monkeypatch):
        stored, started = {}, []
        monkeypatch.setattr(material_service, "_upload_blob", lambda path, data, mime: stored.__setitem__(path, data))
        monkeypatch.setattr(material_service, "_delete_blob", lambda path: stored.pop(path, None))
        import app.ai_framework.workflows.document_ingestion as di

        monkeypatch.setattr(di, "ingest_material_async", lambda mid: started.append(mid))
        return stored, started

    def test_upload_list_status_delete(self, app, client, db, uploads):
        stored, started = uploads
        uid = make_user(db)
        course = create_course(client, app, uid)
        h = headers(app, uid)
        res = client.post(
            f"/api/courses/{course['id']}/materials",
            headers=h,
            data={"file": (io.BytesIO(b"Bayes notes"), "week1.md"), "kind": "NOTES"},
            content_type="multipart/form-data",
        )
        assert res.status_code == 201, res.get_json()
        material = res.get_json()["data"]
        assert material["mime"] == "text/markdown" and material["ingestion_status"] == "PENDING"
        assert "storage_path" not in material
        assert started == [material["id"]]
        path = db.docs("course_materials")[material["id"]]["storage_path"]
        assert path.startswith(f"users/{uid}/courses/{course['id']}/materials/{material['id']}/")
        assert stored[path] == b"Bayes notes"

        assert [m["id"] for m in client.get(f"/api/courses/{course['id']}/materials", headers=h).get_json()["data"]] == [material["id"]]
        assert client.get(f"/api/materials/{material['id']}/status", headers=h).get_json()["data"]["ingestion_status"] == "PENDING"

        db.collection(MATERIAL_CHUNKS).document("chunk1").set({"user_id": uid, "course_id": course["id"], "material_id": material["id"]})
        assert client.delete(f"/api/materials/{material['id']}", headers=h).status_code == 200
        assert db.docs(MATERIAL_CHUNKS) == {} and stored == {}
        assert client.get(f"/api/materials/{material['id']}", headers=h).status_code == 404

    def test_rejects_unsupported_and_missing(self, app, client, db, uploads):
        uid = make_user(db)
        course = create_course(client, app, uid)
        h = headers(app, uid)
        bad = client.post(f"/api/courses/{course['id']}/materials", headers=h, data={"file": (io.BytesIO(b"MZ"), "a.exe")}, content_type="multipart/form-data")
        assert bad.status_code == 400
        missing = client.post(f"/api/courses/{course['id']}/materials", headers=h, data={}, content_type="multipart/form-data")
        assert missing.status_code == 400

    def test_other_user_cannot_touch_material(self, app, client, db, uploads):
        owner, intruder = make_user(db, "u1"), make_user(db, "u2")
        course = create_course(client, app, owner)
        res = client.post(
            f"/api/courses/{course['id']}/materials",
            headers=headers(app, owner),
            data={"file": (io.BytesIO(b"x"), "a.txt")},
            content_type="multipart/form-data",
        )
        mid = res.get_json()["data"]["id"]
        h = headers(app, intruder)
        assert client.get(f"/api/materials/{mid}", headers=h).status_code == 404
        assert client.delete(f"/api/materials/{mid}", headers=h).status_code == 404
        assert client.post(f"/api/materials/{mid}/reingest", headers=h).status_code == 404

    def test_search_endpoint(self, app, client, db, monkeypatch):
        uid = make_user(db)
        course = create_course(client, app, uid)
        m = seed_material(db, uid=uid, course_id=course["id"], title="bayes probability")
        ingest_material(m.id, IngestionDeps(download=lambda p: p.encode(), ocr=None, embed=fake_embed, db=db))
        monkeypatch.setattr(retrieval_service, "_default_embed_query", fake_embed_text)
        res = client.get(f"/api/courses/{course['id']}/search?q=bayes", headers=headers(app, uid))
        assert res.status_code == 200, res.get_json()
        assert res.get_json()["data"][0]["material_id"] == m.id
        assert client.get(f"/api/courses/{course['id']}/search", headers=headers(app, uid)).status_code == 400


# --- course structure -------------------------------------------------------------------


class TestCourseStructure:
    def test_generates_modules_concepts_deadlines(self, app, client, db):
        uid = make_user(db)
        course = create_course(client, app, uid, exam_dates=["2026-10-01"])
        syllabus = seed_material(db, uid=uid, course_id=course["id"], title="Syllabus: bayes week 1", kind="SYLLABUS")
        ingest_material(syllabus.id, IngestionDeps(download=lambda p: p.encode(), ocr=None, embed=fake_embed, db=db))

        prompts = []

        def generate(prompt):
            prompts.append(prompt)
            return csg.CourseStructure(
                modules=[
                    csg.ModuleOut(title="Probability", week=1, concepts=[csg.ConceptOut(name="Bayes' theorem"), csg.ConceptOut(name="Priors")]),
                    csg.ModuleOut(title="Optimisation", week=2, concepts=[csg.ConceptOut(name="Gradient descent")]),
                ],
                deadlines=[
                    csg.DeadlineOut(title="Midterm", type="EXAM", due_date="2026-10-20", weight_pct=30),
                    csg.DeadlineOut(title="A1", type="ASSIGNMENT", due_date="not a date", weight_pct=250),
                ],
            )

        result = csg.generate_structure(uid, course["id"], generate=generate, db=db)
        assert result["success"], result
        assert "Master's" in prompts[0] and "Syllabus: bayes week 1" in prompts[0]

        h = headers(app, uid)
        modules = client.get(f"/api/courses/{course['id']}/modules", headers=h).get_json()["data"]
        assert [m["title"] for m in modules] == ["Probability", "Optimisation"]
        concepts = client.get(f"/api/courses/{course['id']}/concepts?module_id={modules[0]['id']}", headers=h).get_json()["data"]
        assert {c["name"] for c in concepts} == {"Bayes' theorem", "Priors"}

        deadlines = sorted(db.docs("deadlines").values(), key=lambda d: d["title"])
        assert [(d["title"], d["due_at"], d["weight_pct"]) for d in deadlines] == [("A1", None, None), ("Midterm", "2026-10-20", 30)]
        detail = client.get(f"/api/courses/{course['id']}", headers=h).get_json()["data"]
        assert detail["structure_status"] == "READY"
        assert detail["exam_dates"] == ["2026-10-01", "2026-10-20"]

        # Regenerating replaces the old outline and syllabus deadlines
        csg.generate_structure(uid, course["id"], generate=lambda p: csg.CourseStructure(modules=[csg.ModuleOut(title="Only")]), db=db)
        assert [m["title"] for m in client.get(f"/api/courses/{course['id']}/modules", headers=h).get_json()["data"]] == ["Only"]
        assert db.docs("deadlines") == {}

    def test_fails_without_materials(self, app, client, db):
        uid = make_user(db)
        course = create_course(client, app, uid)
        result = csg.generate_structure(uid, course["id"], generate=lambda p: None, db=db)
        assert not result["success"] and "Upload" in result["error"]
        assert db.docs("courses")[course["id"]]["structure_status"] == "FAILED"

    def test_generate_endpoint_starts_background_job(self, app, client, db, monkeypatch):
        uid = make_user(db)
        course = create_course(client, app, uid)
        submitted = []
        monkeypatch.setattr(csg._executor, "submit", lambda fn, *args: submitted.append(args))
        res = client.post(f"/api/courses/{course['id']}/structure/generate", headers=headers(app, uid))
        assert res.status_code == 202
        assert submitted == [(uid, course["id"])]
        assert db.docs("courses")[course["id"]]["structure_status"] == "PROCESSING"
        assert client.post(f"/api/courses/{course['id']}/structure/generate", headers=headers(app, uid)).status_code == 409


class TestOutlineRefresh:
    """New uploads reach the outline: fair context, file links, rebuild after ingestion."""

    def ingest(self, db, material, text: str):
        return ingest_material(material.id, IngestionDeps(download=lambda p: text.encode(), ocr=None, embed=fake_embed, db=db))

    def test_fair_shares(self):
        assert csg.fair_shares([100, 5000, 5000], 3000) == [100, 1450, 1450]
        assert csg.fair_shares([10, 20], 1000) == [10, 20]
        assert csg.fair_shares([], 1000) == []

    def test_long_first_file_does_not_crowd_out_the_second(self, app, client, db):
        uid = make_user(db)
        course = create_course(client, app, uid)
        first = seed_material(db, uid=uid, course_id=course["id"], title="Ch 01", kind="PDF")
        second = seed_material(db, uid=uid, course_id=course["id"], title="Ch 02", kind="PDF")
        self.ingest(db, first, "graph " * 3000)
        self.ingest(db, second, "tree " * 3000)
        context = csg.build_context(db, uid, course["id"], max_chars=4000)
        assert len(context) <= 4000
        assert context.count("tree") > 300 and context.count("graph") > 300

    def test_modules_link_to_their_files(self, app, client, db):
        uid = make_user(db)
        course = create_course(client, app, uid)
        first = seed_material(db, uid=uid, course_id=course["id"], title="Ch 01", kind="PDF")
        second = seed_material(db, uid=uid, course_id=course["id"], title="Ch 02", kind="PDF")
        self.ingest(db, first, "graph basics")
        self.ingest(db, second, "tree basics")
        structure = csg.CourseStructure(
            modules=[
                csg.ModuleOut(title="Graphs", source_files=["Ch 01"]),
                csg.ModuleOut(title="Trees", source_files=["ch 02 ", "Unknown file"]),
            ]
        )
        assert csg.generate_structure(uid, course["id"], generate=lambda p: structure, db=db)["success"]
        modules = client.get(f"/api/courses/{course['id']}/modules", headers=headers(app, uid)).get_json()["data"]
        assert [(m["title"], m["material_ids"]) for m in modules] == [("Graphs", [first.id]), ("Trees", [second.id])]

    def test_request_starts_a_run_or_queues_one(self, app, client, db, monkeypatch):
        uid = make_user(db)
        course = create_course(client, app, uid)
        submitted = []
        monkeypatch.setattr(csg._executor, "submit", lambda fn, *args: submitted.append(args))
        csg.request_generation(uid, course["id"], db=db)
        assert submitted == [(uid, course["id"])]
        assert db.docs("courses")[course["id"]]["structure_status"] == "PROCESSING"
        csg.request_generation(uid, course["id"], db=db)
        assert len(submitted) == 1 and db.docs("courses")[course["id"]]["structure_stale"] is True

    def test_stale_run_repeats_then_finishes(self, app, client, db, monkeypatch):
        uid = make_user(db)
        course = create_course(client, app, uid)
        self.ingest(db, seed_material(db, uid=uid, course_id=course["id"], title="Ch 01"), "graph basics")
        ref = db.collection("courses").document(course["id"])
        ref.update({"structure_status": "PROCESSING", "structure_stale": True})
        runs, real = [], csg.generate_structure

        def fake_generate(user_id, course_id, db=None, allow_rerun=False):
            runs.append(allow_rerun)
            return real(user_id, course_id, generate=lambda p: csg.CourseStructure(), db=db, allow_rerun=allow_rerun)

        monkeypatch.setattr(csg, "generate_structure", fake_generate)
        csg._run_until_fresh(uid, course["id"], db=db)
        assert runs == [True, True]
        doc = db.docs("courses")[course["id"]]
        assert doc["structure_status"] == "READY" and doc["structure_stale"] is False

    def test_outline_refreshes_only_after_successful_ingestion(self, db):
        from app.ai_framework.workflows.document_ingestion import ingest_and_refresh_outline

        refreshed = []
        ok = seed_material(db, uid="u1", course_id="c1", title="Good")
        ingest_and_refresh_outline(ok.id, IngestionDeps(download=lambda p: b"graph", ocr=None, embed=fake_embed, db=db), lambda u, c: refreshed.append((u, c)))
        bad = seed_material(db, uid="u1", course_id="c1", mime=PDF_MIME, title="Blank")
        ingest_and_refresh_outline(bad.id, IngestionDeps(download=lambda p: make_pdf([""]), ocr=None, embed=fake_embed, db=db), lambda u, c: refreshed.append((u, c)))
        assert refreshed == [("u1", "c1")]
