package com.kashyap.wizlyai.ui.viewmodel

import com.kashyap.wizlyai.data.repository.DeadlineRepository
import com.kashyap.wizlyai.data.repository.ProgressRepository
import com.kashyap.wizlyai.network.models.CourseProgress
import com.kashyap.wizlyai.network.models.CourseProgressDetail
import com.kashyap.wizlyai.network.models.GradeEntry
import com.kashyap.wizlyai.network.models.GradeInput
import com.kashyap.wizlyai.network.models.GradesOverview
import com.kashyap.wizlyai.network.models.TodayData
import com.kashyap.wizlyai.network.models.TodayTask
import com.kashyap.wizlyai.ui.state.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

private fun localToday(): String = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()

/** The Today tab. */
class TodayViewModel(
    private val viewModelScope: CoroutineScope,
    private val repository: ProgressRepository = ProgressRepository(),
    private val deadlines: DeadlineRepository = DeadlineRepository(),
) {
    private val _today = MutableStateFlow<UiState<TodayData>>(UiState.Idle)
    val today: StateFlow<UiState<TodayData>> = _today.asStateFlow()

    fun load() {
        viewModelScope.launch {
            if (_today.value !is UiState.Success) _today.value = UiState.Loading
            repository.today(localToday()).fold(
                onSuccess = { _today.value = UiState.Success(it) },
                onFailure = { if (_today.value !is UiState.Success) _today.value = UiState.Error(it.message ?: "Couldn't load today", it) },
            )
        }
    }

    fun setDone(
        task: TodayTask,
        done: Boolean,
    ) {
        val current = (today.value as? UiState.Success)?.data ?: return
        _today.value = UiState.Success(current.copy(planTasks = current.planTasks.map { if (it.id == task.id && it.planId == task.planId) it.copy(done = done) else it }))
        viewModelScope.launch {
            deadlines.setTaskDone(task.planId, task.id, done).fold(
                // Ticking a task also counts toward the streak, so refresh
                onSuccess = { load() },
                onFailure = { _today.value = UiState.Success(current) },
            )
        }
    }
}

/** Progress per course for the Courses tab. */
class CoursesProgressViewModel(
    private val viewModelScope: CoroutineScope,
    private val repository: ProgressRepository = ProgressRepository(),
) {
    private val _progress = MutableStateFlow<Map<String, CourseProgress>>(emptyMap())
    val progress: StateFlow<Map<String, CourseProgress>> = _progress.asStateFlow()

    fun load() {
        viewModelScope.launch {
            repository.courses(localToday()).onSuccess { list -> _progress.value = list.associateBy { it.courseId } }
        }
    }
}

/** The Progress tab of one course. */
class CourseProgressViewModel(
    private val courseId: String,
    private val viewModelScope: CoroutineScope,
    private val repository: ProgressRepository = ProgressRepository(),
) {
    private val _detail = MutableStateFlow<UiState<CourseProgressDetail>>(UiState.Idle)
    val detail: StateFlow<UiState<CourseProgressDetail>> = _detail.asStateFlow()

    fun load() {
        viewModelScope.launch {
            if (_detail.value !is UiState.Success) _detail.value = UiState.Loading
            repository.course(courseId, localToday()).fold(
                onSuccess = { _detail.value = UiState.Success(it) },
                onFailure = { if (_detail.value !is UiState.Success) _detail.value = UiState.Error(it.message ?: "Couldn't load progress", it) },
            )
        }
    }
}

/** Grade tracker across courses (or one course). */
class GradesViewModel(
    private val viewModelScope: CoroutineScope,
    private val courseId: String? = null,
    private val repository: ProgressRepository = ProgressRepository(),
) {
    private val _grades = MutableStateFlow<UiState<GradesOverview>>(UiState.Idle)
    val grades: StateFlow<UiState<GradesOverview>> = _grades.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun load() {
        viewModelScope.launch {
            if (_grades.value !is UiState.Success) _grades.value = UiState.Loading
            repository.grades(courseId).fold(
                onSuccess = { _grades.value = UiState.Success(it) },
                onFailure = { if (_grades.value !is UiState.Success) _grades.value = UiState.Error(it.message ?: "Couldn't load grades", it) },
            )
        }
    }

    fun save(
        existing: GradeEntry?,
        input: GradeInput,
    ) {
        viewModelScope.launch {
            val result = if (existing == null) repository.addGrade(input) else repository.updateGrade(existing.id, input)
            result.fold(onSuccess = { load() }, onFailure = { _message.value = it.message ?: "Couldn't save the grade" })
        }
    }

    fun delete(entry: GradeEntry) {
        viewModelScope.launch {
            repository.deleteGrade(entry.id).fold(onSuccess = { load() }, onFailure = { _message.value = it.message ?: "Couldn't delete the grade" })
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
