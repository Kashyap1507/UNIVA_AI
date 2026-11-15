package com.kashyap.wizlyai.ui.viewmodel

import com.kashyap.wizlyai.data.repository.PracticeRepository
import com.kashyap.wizlyai.network.models.GeneratePracticeRequest
import com.kashyap.wizlyai.network.models.PracticeAnswer
import com.kashyap.wizlyai.network.models.PracticeAttempt
import com.kashyap.wizlyai.network.models.PracticeKind
import com.kashyap.wizlyai.network.models.ExamQuestion
import com.kashyap.wizlyai.network.models.PracticeSet
import com.kashyap.wizlyai.practice.ExamTimer
import com.kashyap.wizlyai.ui.state.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonPrimitive

/** The Practice tab of one course: sets, generation (polled while PROCESSING) and past attempts. */
class CoursePracticeViewModel(
    private val courseId: String,
    private val viewModelScope: CoroutineScope,
    private val repository: PracticeRepository = PracticeRepository(),
    private val pollIntervalMs: Long = 3_000,
) {
    private val _sets = MutableStateFlow<UiState<List<PracticeSet>>>(UiState.Idle)
    val sets: StateFlow<UiState<List<PracticeSet>>> = _sets.asStateFlow()

    private val _attempts = MutableStateFlow<List<PracticeAttempt>>(emptyList())
    val attempts: StateFlow<List<PracticeAttempt>> = _attempts.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var pollJob: Job? = null

    fun load() {
        viewModelScope.launch {
            if (_sets.value !is UiState.Success) _sets.value = UiState.Loading
            refresh()
            repository.listAttempts(courseId).onSuccess { _attempts.value = it }
        }
    }

    private suspend fun refresh() {
        repository.listSets(courseId).fold(
            onSuccess = {
                _sets.value = UiState.Success(it)
                pollIfProcessing()
            },
            onFailure = { if (_sets.value !is UiState.Success) _sets.value = UiState.Error(it.message ?: "Couldn't load practice sets", it) },
        )
    }

    private fun pollIfProcessing() {
        val busy = (sets.value as? UiState.Success)?.data?.any { it.isProcessing } == true
        if (!busy || pollJob?.isActive == true) return
        pollJob =
            viewModelScope.launch {
                repeat(MAX_POLLS) {
                    delay(pollIntervalMs)
                    if (!isActive) return@launch
                    val before = (sets.value as? UiState.Success)?.data.orEmpty().filter { it.isProcessing }.map { it.id }.toSet()
                    repository.listSets(courseId).onSuccess { list ->
                        _sets.value = UiState.Success(list)
                        list.filter { it.id in before && !it.isProcessing }.forEach { done ->
                            _message.value = if (done.isReady) "${done.practiceKind.label} ready: ${done.questionCount} questions" else done.error ?: "Couldn't create this set"
                        }
                    }
                    if ((sets.value as? UiState.Success)?.data?.none { it.isProcessing } == true) return@launch
                }
            }
    }

    fun generate(
        kind: PracticeKind,
        count: Int?,
        moduleIds: List<String>,
        timeLimitMin: Int?,
    ) {
        viewModelScope.launch {
            val request =
                GeneratePracticeRequest(
                    kind = kind.apiName,
                    count = count,
                    moduleIds = moduleIds.takeIf { it.isNotEmpty() },
                    timeLimitMin = timeLimitMin.takeIf { kind == PracticeKind.MOCK_EXAM },
                )
            repository.generate(courseId, request).fold(
                onSuccess = { created ->
                    val current = (sets.value as? UiState.Success)?.data.orEmpty()
                    _sets.value = UiState.Success(listOf(created) + current.filterNot { it.id == created.id })
                    pollIfProcessing()
                },
                onFailure = { _message.value = it.message ?: "Couldn't start generating" },
            )
        }
    }

    fun delete(setId: String) {
        viewModelScope.launch {
            repository.deleteSet(setId).fold(
                onSuccess = { _sets.value = UiState.Success((sets.value as? UiState.Success)?.data.orEmpty().filterNot { it.id == setId }) },
                onFailure = { _message.value = it.message ?: "Couldn't delete the set" },
            )
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun stop() {
        pollJob?.cancel()
    }

    companion object {
        private const val MAX_POLLS = 60
    }
}

data class ExamSession(
    val attemptId: String,
    val title: String,
    val kind: PracticeKind,
    val questions: List<ExamQuestion>,
    /** Option index (Int) or text (String) per question id. */
    val answers: Map<String, Any> = emptyMap(),
    val index: Int = 0,
    val deadline: Instant? = null,
    val submitting: Boolean = false,
    val error: String? = null,
) {
    val current: ExamQuestion? get() = questions.getOrNull(index)
    val answeredCount: Int get() = questions.count { isAnswered(it.id) }
    val unanswered: Int get() = questions.size - answeredCount

    fun isAnswered(id: String): Boolean =
        when (val a = answers[id]) {
            null -> false
            is String -> a.isNotBlank()
            else -> true
        }
}

sealed class ExamState {
    object Loading : ExamState()

    data class Taking(val session: ExamSession) : ExamState()

    data class Result(val attempt: PracticeAttempt, val regrading: Boolean = false, val notice: String? = null) : ExamState()

    data class Failed(val message: String) : ExamState()
}

/** Taking one attempt, then showing its result. */
class ExamViewModel(
    private val viewModelScope: CoroutineScope,
    private val repository: PracticeRepository = PracticeRepository(),
    private val clock: Clock = Clock.System,
) {
    private val _state = MutableStateFlow<ExamState>(ExamState.Loading)
    val state: StateFlow<ExamState> = _state.asStateFlow()

    fun start(
        setId: String,
        title: String,
    ) {
        viewModelScope.launch {
            _state.value = ExamState.Loading
            repository.startAttempt(setId).fold(
                onSuccess = { attempt ->
                    val deadline =
                        ExamTimer.localDeadline(
                            attempt.startedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
                            attempt.deadlineAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
                            clock.now(),
                        )
                    _state.value =
                        ExamState.Taking(
                            ExamSession(
                                attemptId = attempt.id,
                                title = attempt.title ?: title,
                                kind = PracticeKind.from(attempt.kind),
                                questions = attempt.questions.orEmpty(),
                                deadline = deadline,
                            ),
                        )
                },
                onFailure = { _state.value = ExamState.Failed(it.message ?: "Couldn't start") },
            )
        }
    }

    fun openResult(attemptId: String) {
        viewModelScope.launch {
            _state.value = ExamState.Loading
            repository.getAttempt(attemptId).fold(
                onSuccess = { _state.value = ExamState.Result(it) },
                onFailure = { _state.value = ExamState.Failed(it.message ?: "Couldn't load the result") },
            )
        }
    }

    private fun update(block: (ExamSession) -> ExamSession) {
        val s = (state.value as? ExamState.Taking)?.session ?: return
        if (s.submitting) return
        _state.value = ExamState.Taking(block(s))
    }

    fun choose(
        questionId: String,
        option: Int,
    ) = update { it.copy(answers = it.answers + (questionId to option)) }

    fun write(
        questionId: String,
        text: String,
    ) = update { it.copy(answers = it.answers + (questionId to text.take(MAX_ANSWER_CHARS))) }

    fun goTo(index: Int) = update { it.copy(index = index.coerceIn(0, (it.questions.size - 1).coerceAtLeast(0))) }

    /** Called every second by the screen; submits once when time runs out. */
    fun tick() {
        val s = (state.value as? ExamState.Taking)?.session ?: return
        if (!s.submitting && ExamTimer.isUp(s.deadline, clock.now())) submit()
    }

    fun submit() {
        val s = (state.value as? ExamState.Taking)?.session ?: return
        if (s.submitting) return
        _state.value = ExamState.Taking(s.copy(submitting = true, error = null))
        viewModelScope.launch {
            repository.submit(s.attemptId, toAnswers(s)).fold(
                onSuccess = { _state.value = ExamState.Result(it) },
                onFailure = { _state.value = ExamState.Taking(s.copy(submitting = false, error = it.message ?: "Couldn't submit. Try again.")) },
            )
        }
    }

    fun regrade() {
        val result = state.value as? ExamState.Result ?: return
        _state.value = result.copy(regrading = true)
        viewModelScope.launch {
            repository.regrade(result.attempt.id).fold(
                onSuccess = {
                    _state.value = ExamState.Result(it, notice = if (it.ungraded > 0) REGRADE_LATER else null)
                },
                onFailure = { _state.value = result.copy(regrading = false, notice = it.message ?: REGRADE_LATER) },
            )
        }
    }

    companion object {
        const val MAX_ANSWER_CHARS = 20_000
        const val REGRADE_LATER = "Marking isn't available right now. Try again later."

        fun toAnswers(session: ExamSession): List<PracticeAnswer> =
            session.questions.mapNotNull { q ->
                when (val a = session.answers[q.id]) {
                    is Int -> PracticeAnswer(q.id, JsonPrimitive(a))
                    is String -> a.trim().takeIf { it.isNotEmpty() }?.let { PracticeAnswer(q.id, JsonPrimitive(it)) }
                    else -> null
                }
            }
    }
}
