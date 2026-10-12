package com.kashyap.wizlyai.ui.state

/**
 * Represents the UI state for asynchronous operations
 */
sealed class UiState<out T> {
    /**
     * Initial/Idle state before any operation
     */
    object Idle : UiState<Nothing>()

    /**
     * Loading state while operation is in progress
     */
    object Loading : UiState<Nothing>()

    /**
     * Success state with data
     */
    data class Success<T>(val data: T) : UiState<T>()

    /**
     * Error state with error message and optional throwable
     */
    data class Error(
        val message: String,
        val throwable: Throwable? = null,
    ) : UiState<Nothing>()
}

/**
 * Extension functions for UiState
 */
fun <T> UiState<T>.isLoading(): Boolean = this is UiState.Loading

fun <T> UiState<T>.isSuccess(): Boolean = this is UiState.Success

fun <T> UiState<T>.isError(): Boolean = this is UiState.Error

fun <T> UiState<T>.isIdle(): Boolean = this is UiState.Idle

fun <T> UiState<T>.getDataOrNull(): T? =
    when (this) {
        is UiState.Success -> data
        else -> null
    }

fun <T> UiState<T>.getErrorOrNull(): String? =
    when (this) {
        is UiState.Error -> message
        else -> null
    }
