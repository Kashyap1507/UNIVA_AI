package com.kashyap.wizlyai.ui.viewmodel

import com.kashyap.wizlyai.data.repository.CourseRepository
import com.kashyap.wizlyai.network.models.Course
import com.kashyap.wizlyai.network.models.CourseConcept
import com.kashyap.wizlyai.network.models.CourseMaterial
import com.kashyap.wizlyai.network.models.CourseModule
import com.kashyap.wizlyai.network.models.CreateCourseRequest
import com.kashyap.wizlyai.ui.state.UiState
import com.kashyap.wizlyai.util.PickedFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Course list (Courses screen and the Home "Your courses" section). */
class CoursesViewModel(
    private val viewModelScope: CoroutineScope,
    private val repository: CourseRepository = CourseRepository(),
) {
    private val _courses = MutableStateFlow<UiState<List<Course>>>(UiState.Idle)
    val courses: StateFlow<UiState<List<Course>>> = _courses.asStateFlow()

    /** True when the list shown came from the offline cache. */
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun load() {
        viewModelScope.launch {
            if (_courses.value !is UiState.Success) _courses.value = UiState.Loading
            repository.getCourses().fold(
                onSuccess = { (list, fromCache) ->
                    _courses.value = UiState.Success(list.filterNot { it.isArchived } + list.filter { it.isArchived })
                    _isOffline.value = fromCache
                },
                onFailure = { _courses.value = UiState.Error(it.message ?: "Couldn't load your courses", it) },
            )
        }
    }

    fun createCourse(request: CreateCourseRequest, onCreated: (Course) -> Unit = {}) {
        viewModelScope.launch {
            repository.createCourse(request).fold(
                onSuccess = {
                    load()
                    onCreated(it)
                },
                onFailure = { _message.value = it.message ?: "Couldn't create the course" },
            )
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

/** One course: outline (modules + concepts) and materials with upload/status. */
class CourseDetailViewModel(
    private val courseId: String,
    private val viewModelScope: CoroutineScope,
    private val repository: CourseRepository = CourseRepository(),
    private val workRepository: com.kashyap.wizlyai.data.repository.WorkRepository = com.kashyap.wizlyai.data.repository.WorkRepository(),
    private val researchRepository: com.kashyap.wizlyai.data.repository.ResearchRepository = com.kashyap.wizlyai.data.repository.ResearchRepository(),
    private val pollIntervalMs: Long = 3_000,
) {
    private val _course = MutableStateFlow<UiState<Course>>(UiState.Idle)
    val course: StateFlow<UiState<Course>> = _course.asStateFlow()

    private val _materials = MutableStateFlow<UiState<List<CourseMaterial>>>(UiState.Idle)
    val materials: StateFlow<UiState<List<CourseMaterial>>> = _materials.asStateFlow()

    private val _modules = MutableStateFlow<List<CourseModule>>(emptyList())
    val modules: StateFlow<List<CourseModule>> = _modules.asStateFlow()

    private val _concepts = MutableStateFlow<List<CourseConcept>>(emptyList())
    val concepts: StateFlow<List<CourseConcept>> = _concepts.asStateFlow()

    /** Past "Check my work" results for this course. */
    private val _submissions = MutableStateFlow<List<com.kashyap.wizlyai.network.models.WorkSubmission>>(emptyList())
    val submissions: StateFlow<List<com.kashyap.wizlyai.network.models.WorkSubmission>> = _submissions.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var pollJob: Job? = null

    fun load() {
        viewModelScope.launch {
            if (_course.value !is UiState.Success) _course.value = UiState.Loading
            repository.getCourse(courseId).fold(
                onSuccess = { _course.value = UiState.Success(it) },
                onFailure = { if (_course.value !is UiState.Success) _course.value = UiState.Error(it.message ?: "Couldn't load the course", it) },
            )
            refreshMaterials()
            refreshOutline()
            workRepository.listSubmissions(courseId).onSuccess { _submissions.value = it }
            startPollingIfNeeded()
        }
    }

    private suspend fun refreshMaterials() {
        if (_materials.value !is UiState.Success) _materials.value = UiState.Loading
        repository.getMaterials(courseId).fold(
            onSuccess = { (list, _) -> _materials.value = UiState.Success(list) },
            onFailure = { if (_materials.value !is UiState.Success) _materials.value = UiState.Error(it.message ?: "Couldn't load materials", it) },
        )
    }

    private suspend fun refreshOutline() {
        repository.getModules(courseId).onSuccess { _modules.value = it }
        repository.getConcepts(courseId).onSuccess { _concepts.value = it }
    }

    private fun materialsBusy(): Boolean = (materials.value as? UiState.Success)?.data?.any { it.isInProgress } == true

    private fun outlineBusy(): Boolean = (course.value as? UiState.Success)?.data?.isGeneratingOutline == true

    private fun needsPolling(): Boolean = materialsBusy() || outlineBusy()

    private fun startPollingIfNeeded() {
        if (pollJob?.isActive == true || !needsPolling()) return
        pollJob =
            viewModelScope.launch {
                // The server rebuilds the outline right after a file finishes processing,
                // so keep polling a couple of ticks to catch that run starting.
                var graceTicks = 0
                while (isActive && (needsPolling() || graceTicks > 0)) {
                    delay(pollIntervalMs)
                    val wasGenerating = outlineBusy()
                    val wasIngesting = materialsBusy()
                    refreshMaterials()
                    repository.getCourse(courseId).onSuccess { _course.value = UiState.Success(it) }
                    graceTicks = if (wasIngesting && !materialsBusy()) 2 else (graceTicks - 1).coerceAtLeast(0)
                    if (wasGenerating && !outlineBusy()) refreshOutline()
                }
            }
    }

    fun upload(file: PickedFile) {
        viewModelScope.launch {
            _isUploading.value = true
            repository.uploadMaterial(courseId, file).fold(
                onSuccess = { material ->
                    val current = (materials.value as? UiState.Success)?.data.orEmpty()
                    _materials.value = UiState.Success(listOf(material) + current.filterNot { it.id == material.id })
                    _message.value = "Uploaded ${material.title}. Processing..."
                    startPollingIfNeeded()
                },
                onFailure = { _message.value = it.message ?: "Upload failed" },
            )
            _isUploading.value = false
        }
    }

    fun deleteMaterial(materialId: String) {
        viewModelScope.launch {
            repository.deleteMaterial(materialId).fold(
                onSuccess = {
                    val current = (materials.value as? UiState.Success)?.data.orEmpty()
                    _materials.value = UiState.Success(current.filterNot { it.id == materialId })
                },
                onFailure = { _message.value = it.message ?: "Couldn't delete the file" },
            )
        }
    }

    /** Lecture summary dialog: the material and its summary (null while it's being made). */
    private val _summaryView = MutableStateFlow<Pair<CourseMaterial, com.kashyap.wizlyai.network.models.MaterialSummary?>?>(null)
    val summaryView: StateFlow<Pair<CourseMaterial, com.kashyap.wizlyai.network.models.MaterialSummary?>?> = _summaryView.asStateFlow()

    fun summarise(material: CourseMaterial) {
        material.summary?.let {
            _summaryView.value = material to it
            return
        }
        _summaryView.value = material to null
        viewModelScope.launch {
            researchRepository.summariseMaterial(material.id).fold(
                onSuccess = { result ->
                    val current = (materials.value as? UiState.Success)?.data.orEmpty()
                    _materials.value = UiState.Success(current.map { if (it.id == material.id) it.copy(summary = result.summary) else it })
                    if (_summaryView.value?.first?.id == material.id) _summaryView.value = material to result.summary
                },
                onFailure = {
                    _summaryView.value = null
                    _message.value = it.message ?: "Couldn't summarise this file"
                },
            )
        }
    }

    fun closeSummary() {
        _summaryView.value = null
    }

    fun retryMaterial(materialId: String) {
        viewModelScope.launch {
            repository.reingestMaterial(materialId).fold(
                onSuccess = {
                    refreshMaterials()
                    startPollingIfNeeded()
                },
                onFailure = { _message.value = it.message ?: "Couldn't retry processing" },
            )
        }
    }

    fun generateOutline() {
        viewModelScope.launch {
            repository.generateStructure(courseId).fold(
                onSuccess = {
                    (course.value as? UiState.Success)?.data?.let {
                        _course.value = UiState.Success(it.copy(structureStatus = "PROCESSING", structureError = null))
                    }
                    startPollingIfNeeded()
                },
                onFailure = { _message.value = it.message ?: "Couldn't start outline generation" },
            )
        }
    }

    fun deleteCourse(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteCourse(courseId).fold(
                onSuccess = { onDeleted() },
                onFailure = { _message.value = it.message ?: "Couldn't delete the course" },
            )
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    fun stop() {
        pollJob?.cancel()
    }
}
