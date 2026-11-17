package com.kashyap.wizlyai.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResearchProject(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    /** THESIS, PROJECT or COURSEWORK. */
    @SerialName("kind") val kind: String = "THESIS",
    @SerialName("description") val description: String? = null,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("paper_count") val paperCount: Int = 0,
    @SerialName("milestones_done") val milestonesDone: Int = 0,
    @SerialName("milestones_total") val milestonesTotal: Int = 0,
)

@Serializable
data class ProjectInput(
    @SerialName("title") val title: String,
    @SerialName("kind") val kind: String,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("description") val description: String? = null,
)

@Serializable
data class PaperSummary(
    @SerialName("tldr") val tldr: String = "",
    @SerialName("research_question") val researchQuestion: String = "",
    @SerialName("methods") val methods: String = "",
    @SerialName("data") val data: String = "",
    @SerialName("findings") val findings: List<String> = emptyList(),
    @SerialName("limitations") val limitations: List<String> = emptyList(),
    @SerialName("contributions") val contributions: String = "",
    @SerialName("keywords") val keywords: List<String> = emptyList(),
)

@Serializable
data class Paper(
    @SerialName("id") val id: String,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("authors") val authors: List<String> = emptyList(),
    @SerialName("year") val year: Int? = null,
    @SerialName("venue") val venue: String? = null,
    @SerialName("doi") val doi: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("type") val type: String = "ARTICLE",
    @SerialName("notes") val notes: String? = null,
    @SerialName("summary") val summary: PaperSummary? = null,
    /** NEW, ANALYSED or NO_TEXT. */
    @SerialName("status") val status: String = "NEW",
    @SerialName("source") val source: String = "MANUAL",
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("has_text") val hasText: Boolean = false,
    @SerialName("citation") val citation: String? = null,
) {
    val isAnalysed: Boolean get() = summary != null
}

@Serializable
data class PaperInput(
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("authors") val authors: List<String>? = null,
    @SerialName("year") val year: Int? = null,
    @SerialName("venue") val venue: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("doi") val doi: String? = null,
)

@Serializable
data class ReviewRow(
    @SerialName("number") val number: Int,
    @SerialName("paper_id") val paperId: String? = null,
    @SerialName("citation") val citation: String,
    @SerialName("title") val title: String? = null,
    @SerialName("research_question") val researchQuestion: String = "",
    @SerialName("methods") val methods: String = "",
    @SerialName("data") val data: String = "",
    @SerialName("findings") val findings: String = "",
    @SerialName("limitations") val limitations: String = "",
)

@Serializable
data class Synthesis(
    @SerialName("themes") val themes: List<String> = emptyList(),
    @SerialName("agreements") val agreements: List<String> = emptyList(),
    @SerialName("disagreements") val disagreements: List<String> = emptyList(),
    @SerialName("gaps") val gaps: List<String> = emptyList(),
    @SerialName("paragraph") val paragraph: String = "",
)

@Serializable
data class CompareResult(
    @SerialName("rows") val rows: List<ReviewRow> = emptyList(),
    @SerialName("synthesis") val synthesis: Synthesis? = null,
    @SerialName("note") val note: String? = null,
)

@Serializable
data class CompareRequest(
    @SerialName("paper_ids") val paperIds: List<String>,
    @SerialName("focus") val focus: String? = null,
)

@Serializable
data class ExportResult(
    @SerialName("format") val format: String,
    @SerialName("count") val count: Int = 0,
    @SerialName("text") val text: String = "",
)

@Serializable
data class WritingIssue(
    @SerialName("quote") val quote: String = "",
    @SerialName("problem") val problem: String = "",
    @SerialName("suggestion") val suggestion: String = "",
    @SerialName("category") val category: String = "CLARITY",
)

@Serializable
data class WritingFeedbackResult(
    @SerialName("kind") val kind: String,
    @SerialName("overall") val overall: String = "",
    @SerialName("strengths") val strengths: List<String> = emptyList(),
    @SerialName("issues") val issues: List<WritingIssue> = emptyList(),
    @SerialName("structure_notes") val structureNotes: List<String> = emptyList(),
)

@Serializable
data class WritingFeedbackRequest(
    @SerialName("text") val text: String,
    @SerialName("kind") val kind: String,
)

@Serializable
data class Milestone(
    @SerialName("id") val id: String,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("title") val title: String,
    @SerialName("due_at") val dueAt: String? = null,
    /** TODO, IN_PROGRESS or DONE. */
    @SerialName("status") val status: String = "TODO",
    @SerialName("notes") val notes: String? = null,
    @SerialName("template") val template: Boolean = false,
)

@Serializable
data class MilestoneInput(
    @SerialName("title") val title: String? = null,
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("status") val status: String? = null,
)

@Serializable
data class SuggestMilestonesRequest(
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("start_date") val startDate: String? = null,
)

@Serializable
data class LectureTerm(
    @SerialName("term") val term: String,
    @SerialName("definition") val definition: String = "",
)

@Serializable
data class MaterialSummary(
    @SerialName("overview") val overview: String = "",
    @SerialName("key_points") val keyPoints: List<String> = emptyList(),
    @SerialName("terms") val terms: List<LectureTerm> = emptyList(),
    @SerialName("exam_questions") val examQuestions: List<String> = emptyList(),
)

@Serializable
data class MaterialSummaryResponse(
    @SerialName("material_id") val materialId: String,
    @SerialName("title") val title: String? = null,
    @SerialName("summary") val summary: MaterialSummary,
)

enum class WritingKind(val apiName: String, val label: String) {
    ABSTRACT("ABSTRACT", "Abstract"),
    INTRODUCTION("INTRODUCTION", "Introduction"),
    LIT_REVIEW("LIT_REVIEW", "Literature review"),
    METHODS("METHODS", "Methods"),
    RESULTS("RESULTS", "Results"),
    DISCUSSION("DISCUSSION", "Discussion"),
    ESSAY("ESSAY", "Essay"),
    OTHER("OTHER", "Other"),
}
