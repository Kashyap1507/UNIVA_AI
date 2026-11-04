package com.kashyap.wizlyai.ui.viewmodel

import com.kashyap.wizlyai.data.repository.TutorRepository
import com.kashyap.wizlyai.network.models.TutorMessage
import com.kashyap.wizlyai.network.models.TutorSession
import com.kashyap.wizlyai.network.models.TutorStreamEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Answer being streamed in right now. */
data class StreamingAnswer(
    val text: String = "",
    val grounded: Boolean? = null,
)

/** Tutor chat for one course. */
class TutorViewModel(
    private val courseId: String,
    private val viewModelScope: CoroutineScope,
    private val repository: TutorRepository = TutorRepository(),
) {
    private val _sessions = MutableStateFlow<List<TutorSession>>(emptyList())
    val sessions: StateFlow<List<TutorSession>> = _sessions.asStateFlow()

    private val _currentSession = MutableStateFlow<TutorSession?>(null)
    val currentSession: StateFlow<TutorSession?> = _currentSession.asStateFlow()

    private val _messages = MutableStateFlow<List<TutorMessage>>(emptyList())
    val messages: StateFlow<List<TutorMessage>> = _messages.asStateFlow()

    private val _streaming = MutableStateFlow<StreamingAnswer?>(null)
    val streaming: StateFlow<StreamingAnswer?> = _streaming.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Question to put back in the input box after a failed send. */
    private val _restoredDraft = MutableStateFlow<String?>(null)
    val restoredDraft: StateFlow<String?> = _restoredDraft.asStateFlow()

    private var askJob: Job? = null

    val isAnswering: Boolean get() = askJob?.isActive == true

    /** Open the most recent conversation, or start with an empty one. */
    fun start() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.listSessions(courseId).fold(
                onSuccess = { list ->
                    _sessions.value = list
                    list.firstOrNull()?.let { openSession(it.id) } ?: run { _isLoading.value = false }
                },
                onFailure = {
                    _error.value = it.message
                    _isLoading.value = false
                },
            )
        }
    }

    fun openSession(sessionId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getSession(sessionId).fold(
                onSuccess = { detail ->
                    _currentSession.value = detail.session
                    _messages.value = detail.messages
                },
                onFailure = { _error.value = it.message },
            )
            _isLoading.value = false
        }
    }

    /** Clear the screen; a session is created on the first question. */
    fun newConversation() {
        if (isAnswering) return
        _currentSession.value = null
        _messages.value = emptyList()
        _streaming.value = null
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId).fold(
                onSuccess = {
                    _sessions.value = _sessions.value.filterNot { it.id == sessionId }
                    if (_currentSession.value?.id == sessionId) newConversation()
                },
                onFailure = { _error.value = it.message },
            )
        }
    }

    fun ask(question: String) {
        val text = question.trim()
        if (text.isEmpty() || isAnswering) return
        askJob =
            viewModelScope.launch {
                val session =
                    _currentSession.value ?: repository.createSession(courseId).getOrElse {
                        _error.value = it.message
                        _restoredDraft.value = text
                        return@launch
                    }.also {
                        _currentSession.value = it
                        _sessions.value = listOf(it) + _sessions.value
                    }

                val pending = TutorMessage(id = "pending-${_messages.value.size}", role = "user", content = text)
                _messages.value = _messages.value + pending
                _streaming.value = StreamingAnswer()

                repository.ask(session.id, text).collect { event ->
                    when (event) {
                        is TutorStreamEvent.Started -> {
                            event.userMessageId?.let { id ->
                                _messages.value = _messages.value.map { if (it === pending) it.copy(id = id) else it }
                            }
                        }
                        is TutorStreamEvent.Sources ->
                            _streaming.value = (_streaming.value ?: StreamingAnswer()).copy(grounded = event.grounded)
                        is TutorStreamEvent.Delta ->
                            _streaming.value = (_streaming.value ?: StreamingAnswer()).let { it.copy(text = it.text + event.text) }
                        is TutorStreamEvent.Done -> {
                            _messages.value = _messages.value + event.payload.message
                            _streaming.value = null
                            event.payload.session?.title?.let { title ->
                                val updated = session.copy(title = title, messageCount = _messages.value.size)
                                _currentSession.value = updated
                                _sessions.value = listOf(updated) + _sessions.value.filterNot { it.id == updated.id }
                            }
                        }
                        is TutorStreamEvent.Failed -> {
                            _streaming.value = null
                            _error.value = event.message
                            // Drop the question if the server never received it; either way put it
                            // back in the input box so the student can resend with one tap
                            if (_messages.value.lastOrNull()?.id?.startsWith("pending-") == true) {
                                _messages.value = _messages.value.dropLast(1)
                            }
                            _restoredDraft.value = text
                        }
                    }
                }
            }
    }

    fun clearError() {
        _error.value = null
    }

    fun consumeRestoredDraft(): String? = _restoredDraft.value.also { _restoredDraft.value = null }
}
