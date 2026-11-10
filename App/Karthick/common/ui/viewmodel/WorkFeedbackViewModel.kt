package com.kashyap.wizlyai.ui.viewmodel

import com.kashyap.wizlyai.data.models.DocumentType
import com.kashyap.wizlyai.data.repository.DocumentRepository
import com.kashyap.wizlyai.data.repository.LocalDocumentStorage
import com.kashyap.wizlyai.data.repository.PageUploadData
import com.kashyap.wizlyai.data.repository.WorkRepository
import com.kashyap.wizlyai.network.models.WorkStreamEvent
import com.kashyap.wizlyai.network.models.WorkSubmission
import com.kashyap.wizlyai.util.fileReader
import com.kashyap.wizlyai.vision.EdgeDetector
import com.kashyap.wizlyai.vision.ImageSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class WorkFeedbackState {
    object Idle : WorkFeedbackState()

    object Uploading : WorkFeedbackState()

    /** Server-side step: loading, extract, evaluate, verify, format. */
    data class Checking(val step: String?) : WorkFeedbackState()

    data class Ready(val submission: WorkSubmission) : WorkFeedbackState()

    data class Failed(val message: String) : WorkFeedbackState()
}

/** "Check my work": upload the scan, create a submission, follow its progress. */
class WorkFeedbackViewModel(
    private val viewModelScope: CoroutineScope,
    private val workRepository: WorkRepository = WorkRepository(),
    private val documentRepository: DocumentRepository = DocumentRepository(),
    private val localStorage: LocalDocumentStorage? = null,
) {
    private val _state = MutableStateFlow<WorkFeedbackState>(WorkFeedbackState.Idle)
    val state: StateFlow<WorkFeedbackState> = _state.asStateFlow()

    private var job: Job? = null
    private var lastRequest: (() -> Unit)? = null

    /** Scan already uploaded for this check, so "Try again" doesn't upload it twice. */
    private var uploadedDocumentId: String? = null

    fun start(
        courseId: String,
        isPractice: Boolean,
        imagePaths: List<String>,
        title: String,
    ) {
        lastRequest = { recheck(courseId, isPractice) ?: start(courseId, isPractice, imagePaths, title) }
        uploadedDocumentId = null
        job?.cancel()
        job =
            viewModelScope.launch {
                _state.value = WorkFeedbackState.Uploading
                val pages =
                    try {
                        val edges = EdgeDetector()
                        imagePaths.mapIndexed { index, path ->
                            val bytes = fileReader.readBytes(path) ?: throw IllegalStateException("Couldn't read page ${index + 1}")
                            val size = runCatching { edges.getImageSize(path) }.getOrDefault(ImageSize(0, 0))
                            PageUploadData(imageData = bytes, pageNumber = index + 1, width = size.width, height = size.height)
                        }
                    } catch (e: Exception) {
                        _state.value = WorkFeedbackState.Failed(e.message ?: "Couldn't read the scanned pages")
                        return@launch
                    }

                val document =
                    documentRepository.uploadScan(pages = pages, title = title, type = DocumentType.HOMEWORK, courseId = courseId)
                        .getOrElse {
                            _state.value = WorkFeedbackState.Failed("Upload failed. Check your connection and try again.")
                            return@launch
                        }
                uploadedDocumentId = document.id
                // Keep a copy in the scans library (already on the server, so nothing is queued for upload)
                if (document.userId.isNotEmpty()) {
                    localStorage?.saveUploadedScan(document.id, document.userId, title, DocumentType.HOMEWORK, imagePaths)
                        ?.onFailure { println("⚠️ WorkFeedbackViewModel: scan not kept locally: ${it.message}") }
                }

                val submission =
                    workRepository.createSubmission(document.id, courseId, isPractice).getOrElse {
                        _state.value = WorkFeedbackState.Failed(it.message ?: "Couldn't start the check")
                        return@launch
                    }
                follow(submission.id)
            }
    }

    /** Check the already-uploaded scan again; null if nothing was uploaded yet. */
    private fun recheck(
        courseId: String,
        isPractice: Boolean,
    ): Unit? {
        val documentId = uploadedDocumentId ?: return null
        job?.cancel()
        job =
            viewModelScope.launch {
                _state.value = WorkFeedbackState.Checking("queued")
                workRepository.createSubmission(documentId, courseId, isPractice).fold(
                    onSuccess = { follow(it.id) },
                    onFailure = { _state.value = WorkFeedbackState.Failed(it.message ?: "Couldn't start the check") },
                )
            }
        return Unit
    }

    /** Re-open an existing submission (e.g. from history). */
    fun open(submissionId: String) {
        lastRequest = { open(submissionId) }
        job?.cancel()
        job =
            viewModelScope.launch {
                _state.value = WorkFeedbackState.Checking(null)
                workRepository.getSubmission(submissionId).fold(
                    onSuccess = { sub ->
                        when (sub.status) {
                            "READY" -> _state.value = WorkFeedbackState.Ready(sub)
                            "FAILED" -> _state.value = WorkFeedbackState.Failed(sub.error ?: "Couldn't check this work.")
                            else -> follow(sub.id)
                        }
                    },
                    onFailure = { _state.value = WorkFeedbackState.Failed(it.message ?: "Couldn't load this check") },
                )
            }
    }

    private suspend fun follow(submissionId: String) {
        _state.value = WorkFeedbackState.Checking("queued")
        var streamFailed: String? = null
        workRepository.stream(submissionId).collect { event ->
            when (event) {
                is WorkStreamEvent.Step -> _state.value = WorkFeedbackState.Checking(event.step)
                is WorkStreamEvent.Done -> _state.value = WorkFeedbackState.Ready(event.submission)
                is WorkStreamEvent.Failed -> streamFailed = event.message
            }
        }
        val failure = streamFailed ?: return
        // The check keeps running on the server; poll a few times before giving up
        repeat(POLL_ATTEMPTS) {
            delay(POLL_INTERVAL_MS)
            val sub = workRepository.getSubmission(submissionId).getOrNull() ?: return@repeat
            when (sub.status) {
                "READY" -> {
                    _state.value = WorkFeedbackState.Ready(sub)
                    return
                }
                "FAILED" -> {
                    _state.value = WorkFeedbackState.Failed(sub.error ?: failure)
                    return
                }
                else -> _state.value = WorkFeedbackState.Checking(sub.step)
            }
        }
        _state.value = WorkFeedbackState.Failed(failure)
    }

    fun retry() {
        lastRequest?.invoke()
    }

    fun reset() {
        job?.cancel()
        _state.value = WorkFeedbackState.Idle
    }

    companion object {
        private const val POLL_ATTEMPTS = 40
        private const val POLL_INTERVAL_MS = 3_000L
    }
}
