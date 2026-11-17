package com.kashyap.wizlyai.ui.viewmodel

import com.kashyap.wizlyai.calendar.CalendarProvider
import com.kashyap.wizlyai.calendar.CalendarType
import com.kashyap.wizlyai.data.repository.CourseRepository
import com.kashyap.wizlyai.data.repository.DeadlineRepository
import com.kashyap.wizlyai.deadlines.DeadlineLogic
import com.kashyap.wizlyai.network.models.Course
import com.kashyap.wizlyai.network.models.Deadline
import com.kashyap.wizlyai.network.models.DeadlineInput
import com.kashyap.wizlyai.network.models.DeadlineUpdate
import com.kashyap.wizlyai.network.models.GeneratePlanRequest
import com.kashyap.wizlyai.network.models.IcalImportResult
import com.kashyap.wizlyai.network.models.StudyPlan
import com.kashyap.wizlyai.network.models.SyllabusCandidate
import com.kashyap.wizlyai.ui.state.UiState
import com.kashyap.wizlyai.util.PickedFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** A preview the student confirms before anything is saved (syllabus or device calendar). */
data class DeadlinePreview(
    val title: String,
    val source: String,
    val items: List<DeadlineInput>,
    /** Indexes that start ticked. */
    val selected: Set<Int>,
    /** Why an item starts unticked, by index ("Already saved"). */
    val notes: Map<Int, String> = emptyMap(),
)

/** The Deadlines tab: list, add/edit/complete, iCal and device calendar import, plan creation. */
class DeadlinesViewModel(
    private val viewModelScope: CoroutineScope,
    private val repository: DeadlineRepository = DeadlineRepository(),
    private val courseRepository: CourseRepository = CourseRepository(),
) {
    private val _deadlines = MutableStateFlow<UiState<List<Deadline>>>(UiState.Idle)
    val deadlines: StateFlow<UiState<List<Deadline>>> = _deadlines.asStateFlow()

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    private val _filter = MutableStateFlow<String?>(null)
    val filter: StateFlow<String?> = _filter.asStateFlow()

    private val _preview = MutableStateFlow<DeadlinePreview?>(null)
    val preview: StateFlow<DeadlinePreview?> = _preview.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun load() {
        viewModelScope.launch {
            if (_deadlines.value !is UiState.Success) _deadlines.value = UiState.Loading
            courseRepository.getCourses().onSuccess { (list, _) -> _courses.value = list.filterNot { it.isArchived } }
            refresh()
        }
    }

    private suspend fun refresh() {
        repository.list().fold(
            onSuccess = { _deadlines.value = UiState.Success(it) },
            onFailure = { if (_deadlines.value !is UiState.Success) _deadlines.value = UiState.Error(it.message ?: "Couldn't load deadlines", it) },
        )
    }

    fun setFilter(courseId: String?) {
        _filter.value = courseId
    }

    private fun replace(updated: Deadline) {
        val current = (deadlines.value as? UiState.Success)?.data.orEmpty()
        _deadlines.value = UiState.Success(current.map { if (it.id == updated.id) updated else it })
    }

    fun setCompleted(
        deadline: Deadline,
        completed: Boolean,
    ) {
        replace(deadline.copy(completed = completed))
        viewModelScope.launch {
            repository.update(deadline.id, DeadlineUpdate(completed = completed)).fold(
                onSuccess = { replace(it) },
                onFailure = {
                    replace(deadline)
                    _message.value = it.message ?: "Couldn't update the deadline"
                },
            )
        }
    }

    fun save(
        existing: Deadline?,
        input: DeadlineInput,
    ) {
        viewModelScope.launch {
            val result =
                if (existing == null) {
                    repository.create(input)
                } else {
                    repository.update(
                        existing.id,
                        DeadlineUpdate(title = input.title, type = input.type, dueAt = input.dueAt, courseId = input.courseId ?: "", weightPct = input.weightPct),
                    )
                }
            result.fold(onSuccess = { refresh() }, onFailure = { _message.value = it.message ?: "Couldn't save the deadline" })
        }
    }

    fun delete(deadline: Deadline) {
        viewModelScope.launch {
            repository.delete(deadline.id).fold(
                onSuccess = { _deadlines.value = UiState.Success((deadlines.value as? UiState.Success)?.data.orEmpty().filterNot { it.id == deadline.id }) },
                onFailure = { _message.value = it.message ?: "Couldn't delete the deadline" },
            )
        }
    }

    private fun importDone(result: IcalImportResult) {
        _message.value =
            when {
                result.total == 0 -> "No upcoming deadlines found in that calendar"
                else ->
                    buildString {
                        append("Imported ${result.created} new")
                        if (result.updated > 0) append(", updated ${result.updated}")
                        if (result.unmatched > 0) append(" · ${result.unmatched} not linked to a course")
                    }
            }
    }

    fun importFile(file: PickedFile) {
        viewModelScope.launch {
            _busy.value = true
            repository.importIcalFile(file).fold(onSuccess = {
                importDone(it)
                refresh()
            }, onFailure = { _message.value = it.message ?: "Couldn't import that file" })
            _busy.value = false
        }
    }

    fun importUrl(url: String) {
        viewModelScope.launch {
            _busy.value = true
            repository.importIcalUrl(url.trim()).fold(onSuccess = {
                importDone(it)
                refresh()
            }, onFailure = { _message.value = it.message ?: "Couldn't import that calendar" })
            _busy.value = false
        }
    }

    /** Read study-related events from the device calendars (existing CalendarProvider) into a preview. */
    fun previewDeviceCalendar(provider: CalendarProvider) {
        viewModelScope.launch {
            _busy.value = true
            try {
                if (!provider.hasPermission() && !provider.requestPermission()) {
                    _message.value = "Allow calendar access to import events"
                    return@launch
                }
                val zone = TimeZone.currentSystemDefault()
                val today = Clock.System.todayIn(zone)
                val calendars = provider.getDeviceCalendars(CalendarType.ALL)
                val events = provider.getEvents(calendars.map { it.id }, today.toString(), DeadlineLogic.addDays(today, 180).toString(), filterStudyRelated = true)
                val items = DeadlineLogic.newDeviceEvents(events, (deadlines.value as? UiState.Success)?.data.orEmpty(), today, zone)
                if (items.isEmpty()) {
                    _message.value = "No new study events found in your device calendars"
                } else {
                    _preview.value = DeadlinePreview("From your calendar", "DEVICE", items, items.indices.toSet())
                }
            } catch (e: Exception) {
                _message.value = "Couldn't read your calendars"
            } finally {
                _busy.value = false
            }
        }
    }

    fun toggle(index: Int) {
        val p = _preview.value ?: return
        _preview.value = p.copy(selected = if (index in p.selected) p.selected - index else p.selected + index)
    }

    fun setPreviewCourse(courseId: String?) {
        val p = _preview.value ?: return
        _preview.value = p.copy(items = p.items.map { it.copy(courseId = courseId) })
    }

    fun savePreview() {
        val p = _preview.value ?: return
        val chosen = p.items.filterIndexed { i, _ -> i in p.selected }
        _preview.value = null
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            repository.createBulk(p.source, chosen).fold(
                onSuccess = {
                    _message.value = "Saved ${it.size} deadline${if (it.size == 1) "" else "s"}"
                    refresh()
                },
                onFailure = { _message.value = it.message ?: "Couldn't save the deadlines" },
            )
        }
    }

    fun dismissPreview() {
        _preview.value = null
    }

    fun generatePlan(
        deadline: Deadline,
        minutesPerDay: Int,
        onReady: (StudyPlan) -> Unit,
    ) {
        viewModelScope.launch {
            _busy.value = true
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            repository.generatePlan(GeneratePlanRequest(deadlineId = deadline.id, minutesPerDay = minutesPerDay, startDate = today.toString())).fold(
                onSuccess = onReady,
                onFailure = { _message.value = it.message ?: "Couldn't make a study plan" },
            )
            _busy.value = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

/** "Find deadlines in syllabus" for one course: preview, then save the ticked ones. */
class SyllabusImportViewModel(
    private val courseId: String,
    private val viewModelScope: CoroutineScope,
    private val repository: DeadlineRepository = DeadlineRepository(),
) {
    private val _preview = MutableStateFlow<DeadlinePreview?>(null)
    val preview: StateFlow<DeadlinePreview?> = _preview.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun extract() {
        viewModelScope.launch {
            _busy.value = true
            repository.extractSyllabus(courseId).fold(
                onSuccess = { result ->
                    if (result.candidates.isEmpty()) {
                        _message.value = "No dated deadlines found in ${result.materialTitle ?: "the syllabus"}"
                    } else {
                        _preview.value = toPreview(result.materialTitle, result.candidates)
                    }
                },
                onFailure = { _message.value = it.message ?: "Couldn't read the syllabus" },
            )
            _busy.value = false
        }
    }

    fun toggle(index: Int) {
        val p = _preview.value ?: return
        _preview.value = p.copy(selected = if (index in p.selected) p.selected - index else p.selected + index)
    }

    fun save() {
        val p = _preview.value ?: return
        val chosen = p.items.filterIndexed { i, _ -> i in p.selected }
        _preview.value = null
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            repository.createBulk("SYLLABUS", chosen).fold(
                onSuccess = { _message.value = "Saved ${it.size} deadline${if (it.size == 1) "" else "s"}. See them in the Deadlines tab." },
                onFailure = { _message.value = it.message ?: "Couldn't save the deadlines" },
            )
        }
    }

    fun dismiss() {
        _preview.value = null
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        /** Items with a date that aren't already saved start ticked. */
        fun toPreview(
            materialTitle: String?,
            candidates: List<SyllabusCandidate>,
        ): DeadlinePreview {
            val notes = mutableMapOf<Int, String>()
            candidates.forEachIndexed { i, c ->
                when {
                    c.duplicateOf != null -> notes[i] = "Already saved"
                    c.dueAt == null -> notes[i] = "No date in the syllabus"
                }
            }
            val inputs = candidates.map { it.toInput() }
            return DeadlinePreview(
                title = "From ${materialTitle ?: "the syllabus"}",
                source = "SYLLABUS",
                items = inputs,
                selected = candidates.indices.filter { it !in notes }.toSet(),
                notes = notes,
            )
        }
    }
}

/** One exam study plan: days and tasks with done/undone. */
class StudyPlanViewModel(
    private val viewModelScope: CoroutineScope,
    private val repository: DeadlineRepository = DeadlineRepository(),
) {
    private val _plan = MutableStateFlow<UiState<StudyPlan>>(UiState.Idle)
    val plan: StateFlow<UiState<StudyPlan>> = _plan.asStateFlow()

    fun load(planId: String) {
        viewModelScope.launch {
            if (_plan.value !is UiState.Success) _plan.value = UiState.Loading
            repository.plan(planId).fold(
                onSuccess = { _plan.value = UiState.Success(it) },
                onFailure = { _plan.value = UiState.Error(it.message ?: "Couldn't load the plan", it) },
            )
        }
    }

    fun setDone(
        taskId: String,
        done: Boolean,
    ) {
        val current = (plan.value as? UiState.Success)?.data ?: return
        val optimistic =
            current.copy(
                days = current.days?.map { d -> d.copy(tasks = d.tasks.map { if (it.id == taskId) it.copy(done = done) else it }) },
                tasksDone = current.tasksDone + if (done) 1 else -1,
            )
        _plan.value = UiState.Success(optimistic)
        viewModelScope.launch {
            repository.setTaskDone(current.id, taskId, done).fold(
                onSuccess = { _plan.value = UiState.Success(it) },
                onFailure = { _plan.value = UiState.Success(current) },
            )
        }
    }

    companion object {
        fun todayIndex(
            plan: StudyPlan,
            today: LocalDate,
        ): Int = plan.days.orEmpty().indexOfFirst { it.date == today.toString() }
    }
}
