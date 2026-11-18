# Tests

```bash
cd Wizly-Server && ./venv/bin/pytest tests/ -q
```

- `api/test_phase<N>_*.py`: one file per migration phase (courses and RAG, tutor, check my work,
  flashcards, practice, deadlines, progress, groups, research, admin, cleanup), plus
  `test_auth_api.py`. They run against the in-memory Firestore in `fakes.py` with the AI calls
  stubbed, so they need no network, Firebase project or Gemini key.
- `fixtures/`: iCal feeds (Canvas, Moodle) and the SM-2 reference vectors.
- `integration/check_gemini_models.py`: a manual script that lists the Gemini models your key
  can use. It is not collected by pytest.
