# AI framework

Gemini workflows for the Wizly AI study assistant (university students). They are built
with LangGraph, Instructor and Pydantic.

## Layout
- `workflows/`: one module per feature. Each module has its prompts inline, written for
  university level.
  - `document_ingestion.py`: read uploaded course material (PDF, slides, images) into text and chunks.
  - `course_structure_generation.py`: modules, lectures, concepts and assessments from a syllabus.
  - `tutor_chat.py`: grounded tutor answers with citations from the course knowledge base.
  - `work_feedback.py`: "Check my work". Extract, evaluate (hint-first), then verify.
  - `flashcard_generation.py`: cards from course material.
  - `exam_generation.py`: practice sets and mock exams, plus marking of written answers.
  - `research_workflows.py`: paper analysis, literature synthesis, writing feedback and lecture summaries.
- `utils/model_router.py`: picks the model per task. Flash is the default; pro is used for
  `work_feedback` and `work_feedback_verify`. Any task can be overridden with `AI_MODEL_<TASK>`.
- `utils/gemini_client.py`, `utils/embedding_client.py`: Gemini clients (key: `GOOGLE_GEMINI_API_KEY`).
- `utils/base_workflow.py`, `utils/langsmith_config.py`: the LangGraph base class and LangSmith tracing.
- `prompts/prompt_manager.py`, `prompts/experiment_tracker.py`: optional LangSmith Hub prompts and A/B tests.

## Logging and cost
Every call is logged to `ai_workflow_logs` through `app/services/ai_workflow_logger.py`, with the
task as `workflow_type`, plus `user_id` and `course_id`. Token usage is metered in
`app/utils/usage_meter.py` and rolled up per student per day in `ai_usage_daily`. The admin
panel shows both (AI workflows, AI costs).

## Academic integrity
Workflows never write graded assignments or essays. Work feedback is hint-first, and full worked
solutions are only given for material the student marked as practice.
