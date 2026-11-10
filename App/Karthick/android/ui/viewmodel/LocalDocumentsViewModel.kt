package com.kashyap.wizlyai.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.kashyap.wizlyai.data.models.Document
import com.kashyap.wizlyai.data.repository.LocalDocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Android-specific ViewModel for Documents screen with Paging 3 support
 * Uses local Room database with pagination
 */
class LocalDocumentsViewModel(context: Context) : ViewModel() {
    private val localRepository = LocalDocumentRepository(context)

    // Sync status
    private val _pendingSyncCount = MutableStateFlow(0)
    val pendingSyncCount: StateFlow<Int> = _pendingSyncCount.asStateFlow()

    // Storage usage
    private val _storageUsed = MutableStateFlow(0L)
    val storageUsed: StateFlow<Long> = _storageUsed.asStateFlow()

    // Current user ID for filtering
    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        observePendingSyncCount()
        loadStorageUsage()
    }

    /**
     * Get paging flow of documents for a user
     */
    fun getDocumentsPager(userId: String): Flow<PagingData<Document>> {
        _currentUserId.value = userId

        return Pager(
            config =
                PagingConfig(
                    pageSize = 20,
                    enablePlaceholders = false,
                    prefetchDistance = 5,
                ),
            pagingSourceFactory = {
                localRepository.getLocalDocumentsPagingSource(userId)
            },
        ).flow
            .map { pagingData ->
                pagingData.map { entity -> entity.toModel() }
            }
            .cachedIn(viewModelScope)
    }

    /**
     * Search documents
     */
    fun searchDocuments(
        userId: String,
        query: String,
    ) {
        _searchQuery.value = query
        viewModelScope.launch {
            localRepository.searchLocalDocuments(userId, query).fold(
                onSuccess = { /* Results handled by search UI */ },
                onFailure = { /* Handle error */ },
            )
        }
    }

    /**
     * Delete document
     */
    fun deleteDocument(documentId: String) {
        viewModelScope.launch {
            localRepository.deleteDocumentLocally(documentId).fold(
                onSuccess = {
                    // Document marked as deleted, will sync when online
                },
                onFailure = { error ->
                    // Handle error
                },
            )
        }
    }

    /**
     * Get total document count for a user
     */
    suspend fun getDocumentCount(userId: String): Int {
        return localRepository.getDocumentCount(userId)
    }

    /**
     * Observe pending sync count
     */
    private fun observePendingSyncCount() {
        viewModelScope.launch {
            localRepository.getPendingSyncCountFlow().collect { count ->
                _pendingSyncCount.value = count
            }
        }
    }

    /**
     * Load storage usage
     */
    private fun loadStorageUsage() {
        viewModelScope.launch {
            _storageUsed.value = localRepository.getTotalStorageUsed()
        }
    }
}
