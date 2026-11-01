"""
Per-task Gemini model routing.

Flash is the default; pro is reserved for work feedback and its verification
step. Any task can be overridden with an env var named AI_MODEL_<TASK>, e.g.
AI_MODEL_TUTOR_CHAT=gemini-2.5-pro.
"""

import os

FLASH = "gemini-2.5-flash"
PRO = "gemini-2.5-pro"

TASK_MODELS = {
    "tutor_chat": FLASH,
    "flashcard_generation": FLASH,
    "lecture_summary": FLASH,
    "document_ingestion": FLASH,
    "course_structure_generation": FLASH,
    "exam_generation": FLASH,
    "exam_grading": FLASH,
    "syllabus_extraction": FLASH,
    "paper_analysis": FLASH,
    "literature_synthesis": FLASH,
    "writing_feedback": FLASH,
    "work_feedback_extract": FLASH,
    "work_feedback": PRO,
    "work_feedback_verify": PRO,
}


def model_for(task: str) -> str:
    """Model name for a task; unknown tasks get the flash default."""
    override = os.getenv(f"AI_MODEL_{task.upper()}")
    if override:
        return override
    return TASK_MODELS.get(task, FLASH)


def client_for(task: str):
    """Cached Gemini client for the task's model."""
    from app.ai_framework.utils.gemini_client import GeminiClientFactory

    return GeminiClientFactory.get_client(model_for(task))
