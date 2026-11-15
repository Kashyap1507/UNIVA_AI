package com.kashyap.wizlyai.data.repository

import com.kashyap.wizlyai.data.local.LocalJson
import com.kashyap.wizlyai.data.local.LocalJsonStore
import com.kashyap.wizlyai.network.ApiClient
import com.kashyap.wizlyai.network.JsonConfig
import com.kashyap.wizlyai.network.NetworkConstants
import com.kashyap.wizlyai.network.models.Envelope
import com.kashyap.wizlyai.network.models.Flashcard
import com.kashyap.wizlyai.network.models.FlashcardEditRequest
import com.kashyap.wizlyai.network.models.FlashcardReviewRequest
import com.kashyap.wizlyai.network.models.FlashcardSummary
import com.kashyap.wizlyai.network.models.GenerateFlashcardsRequest
import com.kashyap.wizlyai.network.models.PendingReview
import com.kashyap.wizlyai.session.UserSessionManager
import com.kashyap.wizlyai.srs.Sm2
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlin.random.Random

/**
 * Flashcards with offline review.
 *
 * Cards the app has seen are kept on the device. A review is scheduled locally
 * with [Sm2] straight away, queued, and sent to the server in order; the server
 * applies each queued review once (client_review_id) at the time it happened.
 */
class FlashcardRepository(
    private val client: HttpClient = ApiClient.httpClient,
    private val baseUrl: () -> String = { NetworkConstants.getApiUrl() },
    private val store: () -> LocalJsonStore = { LocalJson.store },
    private val userId: () -> String? = { UserSessionManager.instance.userId },
    private val clock: Clock = Clock.System,
) {
    private val json = JsonConfig.json
    private val cardsSerializer = ListSerializer(Flashcard.serializer())
    private val queueSerializer = ListSerializer(PendingReview.serializer())

    // --- server calls --------------------------------------------------------------------

    /** Due cards (one course or all) and whether they came from the device cache. */
    suspend fun dueCards(courseId: String? = null): Result<Pair<List<Flashcard>, Boolean>> {
        syncPending()
        val remote =
            call(cardsSerializer) {
                client.get("${baseUrl()}/flashcards") {
                    parameter("due", "true")
                    courseId?.let { parameter("course_id", it) }
                }
            }
        remote.onSuccess { cards ->
            // Cards no longer due from the server's view keep their cached state
            updateCache { cached -> merge(cached, cards) }
            return Result.success(cards to false)
        }
        val error = remote.exceptionOrNull()
        if (error is CourseApiException && error.status in 400..499) return Result.failure(error)
        val now = clock.now()
        val cached = loadCards().filter { (courseId == null || it.courseId == courseId) && it.isDue(now) }
        return if (cached.isNotEmpty() || loadCards().isNotEmpty()) {
            Result.success(cached.sortedBy { it.srs.dueAt } to true)
        } else {
            Result.failure(error ?: CourseApiException("Couldn't load your cards"))
        }
    }

    /** Every card in a course (for the Cards tab), with cache fallback. */
    suspend fun courseCards(courseId: String): Result<Pair<List<Flashcard>, Boolean>> {
        val remote = call(cardsSerializer) { client.get("${baseUrl()}/flashcards") { parameter("course_id", courseId) } }
        remote.onSuccess { cards ->
            // Keep local scheduling for cards with reviews still waiting to sync
            val waiting = loadQueue().map { it.cardId }.toSet()
            val cached = loadCards().associateBy { it.id }
            val merged = cards.map { if (it.id in waiting) cached[it.id] ?: it else it }
            updateCache { all -> all.filterNot { it.courseId == courseId } + merged }
            return Result.success(merged to false)
        }
        val error = remote.exceptionOrNull()
        if (error is CourseApiException && error.status in 400..499) return Result.failure(error)
        val cached = loadCards().filter { it.courseId == courseId }
        return if (cached.isNotEmpty()) Result.success(cached to true) else Result.failure(error ?: CourseApiException("Couldn't load cards"))
    }

    suspend fun summary(): Result<FlashcardSummary> {
        syncPending()
        val remote = call(FlashcardSummary.serializer()) { client.get("${baseUrl()}/flashcards/summary") }
        if (remote.isSuccess) return remote
        val now = clock.now()
        val due = loadCards().filter { it.isDue(now) }
        if (due.isEmpty()) return remote
        return Result.success(FlashcardSummary(due.size, due.groupingBy { it.courseId }.eachCount()))
    }

    suspend fun generate(
        courseId: String,
        count: Int,
        conceptId: String? = null,
    ): Result<List<Flashcard>> =
        call(cardsSerializer) {
            client.post("${baseUrl()}/courses/$courseId/flashcards/generate") {
                contentType(ContentType.Application.Json)
                setBody(GenerateFlashcardsRequest(count, conceptId))
                timeout {
                    requestTimeoutMillis = GENERATE_TIMEOUT_MS
                    socketTimeoutMillis = GENERATE_TIMEOUT_MS
                }
            }
        }.onSuccess { cards -> updateCache { it + cards } }

    suspend fun create(
        courseId: String,
        front: String,
        back: String,
    ): Result<Flashcard> =
        call(Flashcard.serializer()) {
            client.post("${baseUrl()}/courses/$courseId/flashcards") {
                contentType(ContentType.Application.Json)
                setBody(FlashcardEditRequest(front = front, back = back))
            }
        }.onSuccess { card -> updateCache { it + card } }

    suspend fun update(
        cardId: String,
        edit: FlashcardEditRequest,
    ): Result<Flashcard> =
        call(Flashcard.serializer()) {
            client.put("${baseUrl()}/flashcards/$cardId") {
                contentType(ContentType.Application.Json)
                setBody(edit)
            }
        }.onSuccess { card -> updateCache { all -> all.map { if (it.id == card.id) card.copy(srs = it.srs) else it } } }

    suspend fun delete(cardId: String): Result<Unit> =
        call(JsonObject.serializer(), allowEmptyData = true) { client.delete("${baseUrl()}/flashcards/$cardId") }
            .map { updateCache { all -> all.filterNot { it.id == cardId } } }

    // --- reviews -----------------------------------------------------------------------

    /**
     * Schedule the review on the device, queue it and try to send it.
     * Always succeeds locally; the returned card has its new due date.
     */
    suspend fun review(
        card: Flashcard,
        grade: Int,
    ): Flashcard {
        val now = clock.now()
        val next = Sm2.next(card.srs.state, grade)
        val updated =
            card.copy(
                srs =
                    card.srs.copy(
                        ease = next.ease,
                        intervalDays = next.intervalDays,
                        reps = next.reps,
                        lapses = next.lapses,
                        dueAt = Sm2.dueAfter(now, next).toString(),
                        lastReviewedAt = now.toString(),
                    ),
            )
        mutex.withLock {
            val review = PendingReview(card.id, grade, now.toString(), newReviewId(now))
            io {
                saveQueue(loadQueueUnlocked() + review)
                saveCardsUnlocked(loadCardsUnlocked().filterNot { it.id == card.id } + updated)
            }
        }
        syncPending()
        return updated
    }

    /** Reviews still waiting to reach the server. */
    suspend fun pendingCount(): Int = loadQueue().size

    /**
     * Send queued reviews in order. Stops at the first network failure so order
     * is kept; a review the server rejects (card deleted, bad data) is dropped.
     * Returns how many were sent.
     */
    suspend fun syncPending(): Int =
        syncMutex.withLock {
            var sent = 0
            while (true) {
                val next = loadQueue().firstOrNull() ?: break
                val result =
                    call(Flashcard.serializer()) {
                        client.post("${baseUrl()}/flashcards/${next.cardId}/review") {
                            contentType(ContentType.Application.Json)
                            setBody(FlashcardReviewRequest(next.grade, next.reviewedAt, next.clientReviewId))
                        }
                    }
                val error = result.exceptionOrNull()
                if (error != null && !(error is CourseApiException && error.status in 400..499)) break
                mutex.withLock {
                    io {
                        saveQueue(loadQueueUnlocked().filterNot { it.clientReviewId == next.clientReviewId })
                        result.getOrNull()?.let { card ->
                            // The server's schedule wins, unless more local reviews of this card are queued
                            if (loadQueueUnlocked().none { it.cardId == card.id }) {
                                saveCardsUnlocked(loadCardsUnlocked().map { if (it.id == card.id) card else it })
                            }
                        }
                    }
                }
                if (error == null) sent++
            }
            sent
        }

    /** Forget cached cards and queued reviews (logout). */
    suspend fun clearLocal() {
        val uid = userId() ?: return
        mutex.withLock {
            io {
                store().delete(cardsFile(uid))
                store().delete(queueFile(uid))
            }
        }
    }

    // --- local storage --------------------------------------------------------------------

    private fun cardsFile(uid: String) = LocalJson.safeName("flashcards", uid)

    private fun queueFile(uid: String) = LocalJson.safeName("flashcard_queue", uid)

    private suspend fun loadCards(): List<Flashcard> = mutex.withLock { io { loadCardsUnlocked() } }

    private suspend fun loadQueue(): List<PendingReview> = mutex.withLock { io { loadQueueUnlocked() } }

    private fun loadCardsUnlocked(): List<Flashcard> = decode(userId()?.let { store().read(cardsFile(it)) }, cardsSerializer)

    private fun loadQueueUnlocked(): List<PendingReview> = decode(userId()?.let { store().read(queueFile(it)) }, queueSerializer)

    private fun saveCardsUnlocked(cards: List<Flashcard>) {
        val uid = userId() ?: return
        // Newest copy of each card wins; keep the cache bounded
        val unique = cards.associateBy { it.id }.values.toList().takeLast(MAX_CACHED_CARDS)
        store().write(cardsFile(uid), json.encodeToString(cardsSerializer, unique))
    }

    private fun saveQueue(queue: List<PendingReview>) {
        val uid = userId() ?: return
        store().write(queueFile(uid), json.encodeToString(queueSerializer, queue))
    }

    private suspend fun updateCache(transform: (List<Flashcard>) -> List<Flashcard>) =
        mutex.withLock { io { saveCardsUnlocked(transform(loadCardsUnlocked())) } }

    private fun merge(
        cached: List<Flashcard>,
        fresh: List<Flashcard>,
    ): List<Flashcard> {
        val waiting = decode(userId()?.let { store().read(queueFile(it)) }, queueSerializer).map { it.cardId }.toSet()
        val keep = fresh.filterNot { it.id in waiting }
        return cached.filterNot { c -> keep.any { it.id == c.id } } + keep
    }

    private fun <T> decode(
        text: String?,
        serializer: KSerializer<List<T>>,
    ): List<T> =
        text?.let {
            try {
                json.decodeFromString(serializer, it)
            } catch (e: Exception) {
                println("⚠️ FlashcardRepository: dropping unreadable cache: ${e.message}")
                emptyList()
            }
        } ?: emptyList()

    private fun newReviewId(now: Instant): String = "r${now.toEpochMilliseconds()}-${Random.nextLong(0, Long.MAX_VALUE).toString(36)}"

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.Default) { block() }

    // --- network helper --------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> call(
        serializer: KSerializer<T>,
        allowEmptyData: Boolean = false,
        request: suspend () -> HttpResponse,
    ): Result<T> =
        try {
            val response = request()
            val envelope =
                try {
                    json.decodeFromString(Envelope.serializer(serializer), response.bodyAsText())
                } catch (e: Exception) {
                    return Result.failure(CourseApiException("Request failed (${response.status.value})", response.status.value))
                }
            val data = envelope.data ?: if (allowEmptyData && envelope.success) JsonObject(emptyMap()) as T else null
            if (!response.status.isSuccess() || !envelope.success || data == null) {
                Result.failure(CourseApiException(envelope.error ?: "Request failed (${response.status.value})", response.status.value))
            } else {
                Result.success(data)
            }
        } catch (e: Exception) {
            println("⚠️ FlashcardRepository: ${e.message}")
            Result.failure(CourseApiException("Can't reach the server. Check your connection and try again."))
        }

    companion object {
        const val GENERATE_TIMEOUT_MS = 120_000L
        private const val MAX_CACHED_CARDS = 2_000

        // Shared by every repository instance so the queue is never sent twice at once
        private val mutex = Mutex()
        private val syncMutex = Mutex()
    }
}
