package com.kashyap.wizlyai.ui.viewmodel

import com.kashyap.wizlyai.data.repository.ResearchRepository
import com.kashyap.wizlyai.network.models.CompareRequest
import com.kashyap.wizlyai.network.models.CompareResult
import com.kashyap.wizlyai.network.models.ExportResult
import com.kashyap.wizlyai.network.models.Milestone
import com.kashyap.wizlyai.network.models.MilestoneInput
import com.kashyap.wizlyai.network.models.Paper
import com.kashyap.wizlyai.network.models.PaperInput
import com.kashyap.wizlyai.network.models.ProjectInput
import com.kashyap.wizlyai.network.models.ResearchProject
import com.kashyap.wizlyai.network.models.SuggestMilestonesRequest
import com.kashyap.wizlyai.network.models.WritingFeedbackRequest
import com.kashyap.wizlyai.network.models.WritingFeedbackResult
import com.kashyap.wizlyai.network.models.WritingKind
import com.kashyap.wizlyai.ui.state.UiState
import com.kashyap.wizlyai.util.PickedFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** "Last, First" or "First Last" names, one per line or separated by semicolons. */
fun parseAuthors(text: String): List<String> =
    text.split('\n', ';').map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotEmpty() }

/** The Research tab: projects. */
class ResearchHomeViewModel(
    private val viewModelScope: CoroutineScope,
    private val repository: ResearchRepository = ResearchRepository(),
) {
    private val _projects = MutableStateFlow<UiState<List<ResearchProject>>>(UiState.Idle)
    val projects: StateFlow<UiState<List<ResearchProject>>> = _projects.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun load() {
        viewModelScope.launch {
            if (_projects.value !is UiState.Success) _projects.value = UiState.Loading
            repository.projects().fold(
                onSuccess = { _projects.value = UiState.Success(it) },
                onFailure = { if (_projects.value !is UiState.Success) _projects.value = UiState.Error(it.message ?: "Couldn't load projects", it) },
            )
        }
    }

    fun create(
        input: ProjectInput,
        onCreated: (ResearchProject) -> Unit,
    ) {
        viewModelScope.launch {
            repository.createProject(input).fold(onSuccess = {
                load()
                onCreated(it)
            }, onFailure = { _message.value = it.message ?: "Couldn't create the project" })
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

/** One project: papers (upload, add, analyse, select, export) and milestones. */
class ProjectViewModel(
    private val projectId: String,
    private val viewModelScope: CoroutineScope,
    private val repository: ResearchRepository = ResearchRepository(),
) {
    private val _project = MutableStateFlow<UiState<ResearchProject>>(UiState.Idle)
    val project: StateFlow<UiState<ResearchProject>> = _project.asStateFlow()

    private val _papers = MutableStateFlow<List<Paper>>(emptyList())
    val papers: StateFlow<List<Paper>> = _papers.asStateFlow()

    private val _milestones = MutableStateFlow<List<Milestone>>(emptyList())
    val milestones: StateFlow<List<Milestone>> = _milestones.asStateFlow()

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    /** Paper ids being analysed or uploaded right now. */
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy: StateFlow<Set<String>> = _busy.asStateFlow()

    private val _export = MutableStateFlow<ExportResult?>(null)
    val export: StateFlow<ExportResult?> = _export.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun load() {
        viewModelScope.launch {
            if (_project.value !is UiState.Success) _project.value = UiState.Loading
            repository.project(projectId).fold(
                onSuccess = { _project.value = UiState.Success(it) },
                onFailure = { _project.value = UiState.Error(it.message ?: "Couldn't open the project", it) },
            )
            repository.papers(projectId).onSuccess { _papers.value = it }
            repository.milestones(projectId).onSuccess { _milestones.value = it }
        }
    }

    private fun replace(paper: Paper) {
        _papers.value = _papers.value.map { if (it.id == paper.id) paper else it }
    }

    fun upload(file: PickedFile) {
        viewModelScope.launch {
            _busy.value = _busy.value + UPLOADING
            repository.uploadPaper(file, projectId).fold(
                onSuccess = { paper ->
                    _papers.value = _papers.value + paper
                    _message.value = if (paper.status == "NO_TEXT") "Uploaded. No text found in this PDF, so fill in the details yourself." else "Uploaded ${paper.fileName ?: "the paper"}. Tap Analyse to summarise it."
                },
                onFailure = { _message.value = it.message ?: "Upload failed" },
            )
            _busy.value = _busy.value - UPLOADING
        }
    }

    fun add(input: PaperInput) {
        viewModelScope.launch {
            repository.addPaper(input.copy(projectId = projectId)).fold(
                onSuccess = { _papers.value = _papers.value + it },
                onFailure = { _message.value = it.message ?: "Couldn't add the paper" },
            )
        }
    }

    fun analyse(paper: Paper) {
        viewModelScope.launch {
            _busy.value = _busy.value + paper.id
            repository.analyse(paper.id).fold(onSuccess = { replace(it) }, onFailure = { _message.value = it.message ?: "Couldn't analyse the paper" })
            _busy.value = _busy.value - paper.id
        }
    }

    fun toggle(paperId: String) {
        _selected.value = if (paperId in _selected.value) _selected.value - paperId else _selected.value + paperId
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    fun exportAs(format: String) {
        viewModelScope.launch {
            repository.export(format, projectId).fold(onSuccess = { _export.value = it }, onFailure = { _message.value = it.message ?: "Couldn't export" })
        }
    }

    fun closeExport() {
        _export.value = null
    }

    fun suggestMilestones(dueDate: String?) {
        viewModelScope.launch {
            repository.suggestMilestones(projectId, SuggestMilestonesRequest(dueDate = dueDate)).fold(
                onSuccess = {
                    _message.value = "Added ${it.size} milestones"
                    load()
                },
                onFailure = { _message.value = it.message ?: "Couldn't make a plan" },
            )
        }
    }

    fun addMilestone(
        title: String,
        dueAt: String,
    ) {
        viewModelScope.launch {
            repository.addMilestone(projectId, MilestoneInput(title = title.trim(), dueAt = dueAt)).fold(
                onSuccess = { m -> _milestones.value = (_milestones.value + m).sortedBy { it.dueAt ?: "9999" } },
                onFailure = { _message.value = it.message ?: "Couldn't add the milestone" },
            )
        }
    }

    /** TODO → IN_PROGRESS → DONE → TODO */
    fun cycle(milestone: Milestone) {
        val next = nextStatus(milestone.status)
        _milestones.value = _milestones.value.map { if (it.id == milestone.id) it.copy(status = next) else it }
        viewModelScope.launch {
            repository.updateMilestone(milestone.id, MilestoneInput(status = next)).onFailure {
                _milestones.value = _milestones.value.map { if (it.id == milestone.id) milestone else it }
                _message.value = it.message ?: "Couldn't update the milestone"
            }
        }
    }

    fun deleteMilestone(milestone: Milestone) {
        viewModelScope.launch {
            repository.deleteMilestone(milestone.id).fold(
                onSuccess = { _milestones.value = _milestones.value.filterNot { it.id == milestone.id } },
                onFailure = { _message.value = it.message ?: "Couldn't delete the milestone" },
            )
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        const val UPLOADING = "__upload__"

        fun nextStatus(status: String): String =
            when (status) {
                "TODO" -> "IN_PROGRESS"
                "IN_PROGRESS" -> "DONE"
                else -> "TODO"
            }
    }
}

/** One paper's details and summary. */
class PaperViewModel(
    private val paperId: String,
    private val viewModelScope: CoroutineScope,
    private val repository: ResearchRepository = ResearchRepository(),
) {
    private val _paper = MutableStateFlow<UiState<Paper>>(UiState.Idle)
    val paper: StateFlow<UiState<Paper>> = _paper.asStateFlow()

    private val _analysing = MutableStateFlow(false)
    val analysing: StateFlow<Boolean> = _analysing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun load() {
        viewModelScope.launch {
            if (_paper.value !is UiState.Success) _paper.value = UiState.Loading
            repository.paper(paperId).fold(onSuccess = { _paper.value = UiState.Success(it) }, onFailure = { _paper.value = UiState.Error(it.message ?: "Couldn't open the paper", it) })
        }
    }

    fun analyse() {
        viewModelScope.launch {
            _analysing.value = true
            repository.analyse(paperId).fold(onSuccess = { _paper.value = UiState.Success(it) }, onFailure = { _message.value = it.message ?: "Couldn't analyse the paper" })
            _analysing.value = false
        }
    }

    fun save(input: PaperInput) {
        viewModelScope.launch {
            repository.updatePaper(paperId, input).fold(onSuccess = { _paper.value = UiState.Success(it) }, onFailure = { _message.value = it.message ?: "Couldn't save" })
        }
    }

    fun open(openUrl: (String) -> Unit) {
        viewModelScope.launch {
            repository.paperDownload(paperId).fold(onSuccess = { openUrl(it.url) }, onFailure = { _message.value = it.message ?: "Couldn't open the file" })
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deletePaper(paperId).fold(onSuccess = { onDeleted() }, onFailure = { _message.value = it.message ?: "Couldn't delete" })
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

/** Literature review table and synthesis for chosen papers. */
class CompareViewModel(
    private val viewModelScope: CoroutineScope,
    private val repository: ResearchRepository = ResearchRepository(),
) {
    private val _result = MutableStateFlow<UiState<CompareResult>>(UiState.Idle)
    val result: StateFlow<UiState<CompareResult>> = _result.asStateFlow()

    fun run(
        paperIds: List<String>,
        focus: String?,
    ) {
        viewModelScope.launch {
            _result.value = UiState.Loading
            repository.compare(CompareRequest(paperIds, focus?.trim()?.ifBlank { null })).fold(
                onSuccess = { _result.value = UiState.Success(it) },
                onFailure = { _result.value = UiState.Error(it.message ?: "Couldn't compare these papers", it) },
            )
        }
    }
}

/** Writing feedback on the student's own text. */
class WritingFeedbackViewModel(
    private val viewModelScope: CoroutineScope,
    private val repository: ResearchRepository = ResearchRepository(),
) {
    private val _result = MutableStateFlow<UiState<WritingFeedbackResult>>(UiState.Idle)
    val result: StateFlow<UiState<WritingFeedbackResult>> = _result.asStateFlow()

    fun submit(
        text: String,
        kind: WritingKind,
    ) {
        viewModelScope.launch {
            _result.value = UiState.Loading
            repository.writingFeedback(WritingFeedbackRequest(text, kind.apiName)).fold(
                onSuccess = { _result.value = UiState.Success(it) },
                onFailure = { _result.value = UiState.Error(it.message ?: "Couldn't review your writing", it) },
            )
        }
    }

    fun reset() {
        _result.value = UiState.Idle
    }

    companion object {
        const val MIN_CHARS = 200
        const val MAX_CHARS = 60_000
    }
}
