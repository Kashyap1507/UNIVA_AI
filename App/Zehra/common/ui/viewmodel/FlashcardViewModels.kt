package com.kashyap.wizlyai.ui.viewmodel

import com.kashyap.wizlyai.data.repository.FlashcardRepository
import com.kashyap.wizlyai.network.models.Flashcard
import com.kashyap.wizlyai.network.models.FlashcardEditRequest
import com.kashyap.wizlyai.srs.ReviewGrade
import com.kashyap.wizlyai.srs.Sm2
import com.kashyap.wizlyai.ui.state.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** The Cards tab of one course: list, generate, add, edit, delete. */
class CourseFlashcardsViewModel(
    private val courseId: String,
    private val viewModelScope: CoroutineScope,
    private val repository: FlashcardRepository = FlashcardRepository(),
) {
    private val _cards = MutableStateFlow<UiState<List<Flashcard>>>(UiState.Idle)
    val cards: StateFlow<UiState<List<Flashcard>>> = _cards.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val dueCount: Int
        get() = (cards.value as? UiState.Success)?.data?.count { it.isDue(Clock.System.now()) } ?: 0

    fun load() {
        viewModelScope.launch {
            if (_cards.value !is UiState.Success) _cards.value = UiState.Loading
            repository.courseCards(courseId).fold(
                onSuccess = { (list, fromCache) ->
                    _cards.value = UiState.Success(list.sortedByDescending { it.createdAt })
                    _isOffline.value = fromCache
                },
                onFailure = { if (_cards.value !is UiState.Success) _cards.value = UiState.Error(it.message ?: "Couldn't load cards", it) },
            )
        }
    }

    fun generate(
        count: Int,
        conceptId: String? = null,
    ) {
        if (_isGenerating.value) return
        viewModelScope.launch {
            _isGenerating.value = true
            repository.generate(courseId, count, conceptId).fold(
                onSuccess = {
                    _message.value =
                        when (it.size) {
                            0 -> "No new cards: these materials are already covered. Upload more or pick a concept."
                            1 -> "Added 1 card"
                            else -> "Added ${it.size} cards"
                        }
                    load()
                },
                onFailure = { _message.value = it.message ?: "Couldn't create cards" },
            )
            _isGenerating.value = false
        }
    }

    fun add(
        front: String,
        back: String,
    ) {
        viewModelScope.launch {
            repository.create(courseId, front.trim(), back.trim()).fold(
                onSuccess = { load() },
                onFailure = { _message.value = it.message ?: "Couldn't add the card" },
            )
        }
    }

    fun edit(
        cardId: String,
        front: String,
        back: String,
        suspended: Boolean,
    ) {
        viewModelScope.launch {
            repository.update(cardId, FlashcardEditRequest(front.trim(), back.trim(), suspended)).fold(
                onSuccess = { load() },
                onFailure = { _message.value = it.message ?: "Couldn't save the card" },
            )
        }
    }

    fun delete(cardId: String) {
        viewModelScope.launch {
            repository.delete(cardId).fold(
                onSuccess = {
                    val current = (cards.value as? UiState.Success)?.data.orEmpty()
                    _cards.value = UiState.Success(current.filterNot { it.id == cardId })
                },
                onFailure = { _message.value = it.message ?: "Couldn't delete the card" },
            )
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

data class ReviewSession(
    val queue: List<Flashcard>,
    val position: Int = 0,
    val revealed: Boolean = false,
    val reviewed: Int = 0,
    val again: Int = 0,
    val offline: Boolean = false,
) {
    val current: Flashcard? get() = queue.getOrNull(position)
    val isFinished: Boolean get() = position >= queue.size
    val remaining: Int get() = (queue.size - position).coerceAtLeast(0)

    /** Interval label under each button, e.g. Good → "6 days". */
    fun preview(grade: ReviewGrade): String =
        current?.let { Sm2.describeInterval(Sm2.next(it.srs.state, grade.grade).intervalDays) }.orEmpty()
}

/** One study session over due cards (a course, or every course). */
class FlashcardReviewViewModel(
    private val viewModelScope: CoroutineScope,
    private val repository: FlashcardRepository = FlashcardRepository(),
) {
    private val _state = MutableStateFlow<UiState<ReviewSession>>(UiState.Idle)
    val state: StateFlow<UiState<ReviewSession>> = _state.asStateFlow()

    private val _pending = MutableStateFlow(0)

    /** Reviews answered offline that haven't reached the server yet. */
    val pending: StateFlow<Int> = _pending.asStateFlow()

    fun start(courseId: String?) {
        viewModelScope.launch {
            _state.value = UiState.Loading
            repository.dueCards(courseId).fold(
                onSuccess = { (cards, fromCache) ->
                    _state.value = UiState.Success(ReviewSession(queue = cards.take(SESSION_LIMIT), offline = fromCache))
                },
                onFailure = { _state.value = UiState.Error(it.message ?: "Couldn't load your cards", it) },
            )
            _pending.value = repository.pendingCount()
        }
    }

    fun reveal() {
        val session = (state.value as? UiState.Success)?.data ?: return
        _state.value = UiState.Success(session.copy(revealed = true))
    }

    fun grade(grade: ReviewGrade) {
        val session = (state.value as? UiState.Success)?.data ?: return
        val card = session.current ?: return
        if (!session.revealed) return
        // Move on at once; saving happens in the background
        val requeue = grade == ReviewGrade.AGAIN
        _state.value =
            UiState.Success(
                session.copy(
                    // "Again" cards come back at the end of this session (not rescheduled twice)
                    queue = if (requeue) session.queue + card.copy(srs = card.srs.copy(reps = 0)) else session.queue,
                    position = session.position + 1,
                    revealed = false,
                    reviewed = session.reviewed + 1,
                    again = session.again + if (requeue) 1 else 0,
                ),
            )
        // A card seen earlier in this session was already scheduled; this is just relearning
        if (session.queue.take(session.position).any { it.id == card.id }) return
        viewModelScope.launch {
            repository.review(card, grade.grade)
            _pending.value = repository.pendingCount()
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            repository.syncPending()
            _pending.value = repository.pendingCount()
        }
    }

    companion object {
        const val SESSION_LIMIT = 100
    }
}
