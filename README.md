# Univa AI: demo guide

A runnable copy of Univa AI where **all of the app and backend code is split into one folder per member**.

```
UNIVA_AI/
├── App/                                  Kotlin Multiplatform app (Android + iOS)
│   ├── Karthick/  Akshat/  Shashang/  Kashyap/  Zehra/
│   │     common/    shared Kotlin (screens, view models, repositories, API models)
│   │     android/   Android-specific code
│   │     ios/       iOS-specific code
│   └── androidApp/ iosApp/ shared/ gradle/   build setup only (no feature code)
└── Server/                               Flask backend
    ├── Karthick/  Akshat/  Shashang/  Kashyap/  Zehra/
    │     api/  services/  ai_framework/ ...   the real backend code
    ├── app/                              loaders only (see below)
    └── main.py  requirements.txt  pytest.ini
```

**How it stays runnable**
- **App:** Gradle compiles every `App/<Member>/common|android|ios` folder (see `sourceSets` in `App/shared/build.gradle.kts`).
  Package names are unchanged, so the files open normally in Android Studio.
- **Server:** each file under `Server/app/` is a short loader that runs the member's file as that module.
  Imports and behaviour are the same as before. Edit the member file, never the loader.
  After an edit, restart the server, because auto-reload only watches the loaders.
- `androidApp/`, `iosApp/` (SwiftUI host + Pods), the Gradle files and `main.py` stay where the build tools expect them.

## Who owns what

| Member | Research idea | App (`App/<Member>/`) | Backend (`Server/<Member>/`) |
|---|---|---|---|
| **Karthick** | Data pipeline + fine-tuned T5 | Scan history (documents list, viewer, past feedback), local database, sync, offline queue, design system (theme + shared components) | Firestore models, Firebase storage, sync notifications, work and documents API, migration/seed `scripts/` |
| **Akshat** | LLM grader with prompt + JSON output | App shell and navigation, login/sign-up, onboarding, profile, settings, course list, API client, notifications, Android launcher, iOS entry point | Flask app setup (`__init__.py`, `config.py`, `api/routes.py`), auth, profile, courses, prompt manager, AI course-structure generation as typed JSON |
| **Shashang** | Similar-answer RAG (embeddings + vector index) | Course materials upload, file picking, research tools, deadlines + syllabus/calendar import, study groups | Material extraction and ingestion, embeddings, uploads, research tools, deadlines, study groups |
| **Kashyap** | Course-content retriever (C1–C4) | Tutor chat with citations, camera scanning and Check my work, edge detection and image processing | Course-content retrieval, citations, tutor, work feedback, model router, Gemini client, AI logging and LangSmith |
| **Zehra** | Evaluation + analysis | Today dashboard, practice and mock exams, flashcards + spaced repetition, analytics, **all app tests** (`tests/`) | Progress, practice and exam grading, flashcards + SRS, analytics, **all backend tests** (`tests/`), **admin panel** (`admin-panel/`) |

### Demo order (about 10 minutes)
1. **Akshat:** sign up, set up the profile, create a course
2. **Shashang:** upload slides/notes to the course and explain how they are indexed
3. **Kashyap:** ask the tutor a question and open the cited source, then run Check my work
4. **Karthick:** open the scan history and a past result; explain how data is stored and synced
5. **Zehra:** show the dashboard and a graded practice set; show the test suite and admin panel

## Setup

### 1. Secrets (not included in this folder)
Ask Kashyap for these two files **privately**, and never commit or re-share them:
- `Server/.env`: copy `Server/.env.example` and fill it in. You can use your own free Gemini key from aistudio.google.com.
- `Server/firebase-service-account.json`: the Firebase admin key for project `wizly-ai-2026`.

### 2. Backend (Python 3.12)
```bash
cd Server
python3 -m venv venv
source venv/bin/activate            # Windows: venv\Scripts\activate
pip install -r requirements.txt
python main.py                      # serves on http://localhost:5000
pytest -q                           # optional: runs Zehra/tests (344 tests)
```
In development the email login code is printed in this terminal.

### 3. Android app
Open the `App/` folder in Android Studio (it creates `local.properties` with your SDK path) and run **androidApp**.
- Emulator: talks to `10.0.2.2:5000` automatically.
- Real phone: same Wi-Fi as the laptop running the backend; in the app's **Developer Settings**, set the server to that laptop's LAN IP.

### 4. iOS app (Mac only)
Open **`App/iosApp/iosApp.xcworkspace`** (the workspace, not the `.xcodeproj`) in Xcode and run on a simulator.
The simulator uses `localhost:5000`. It has no camera, so use the gallery button on the scan screen.

### 5. Admin panel (optional)
```bash
cd Server/Zehra/admin-panel && npm install && npm run dev
```
