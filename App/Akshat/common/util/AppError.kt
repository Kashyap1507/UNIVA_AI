package com.kashyap.wizlyai.util

/**
 * Standardized Error Handling System (UX-021)
 *
 * Provides consistent error categorization, user-friendly messages,
 * and retry mechanisms across the app.
 */

/**
 * Error categories for standardized handling
 */
enum class ErrorCategory {
    NETWORK, // Network connectivity issues
    VALIDATION, // User input validation errors
    SERVER, // Server-side errors (5xx)
    AUTH, // Authentication/authorization errors
    UNKNOWN, // Unexpected errors
}

/**
 * Standardized application error
 */
data class AppError(
    val category: ErrorCategory,
    val title: String,
    val userMessage: String,
    val technicalMessage: String? = null,
    val isRetryable: Boolean = false,
    val statusCode: Int? = null,
) {
    companion object {
        /**
         * Create network error
         */
        fun network(
            message: String = "Unable to connect to the server. Please check your internet connection and try again.",
            technicalMessage: String? = null,
        ) = AppError(
            category = ErrorCategory.NETWORK,
            title = "Connection Error",
            userMessage = message,
            technicalMessage = technicalMessage,
            isRetryable = true,
        )

        /**
         * Create validation error
         */
        fun validation(
            message: String,
            technicalMessage: String? = null,
        ) = AppError(
            category = ErrorCategory.VALIDATION,
            title = "Invalid Input",
            userMessage = message,
            technicalMessage = technicalMessage,
            isRetryable = false,
        )

        /**
         * Create server error
         */
        fun server(
            statusCode: Int? = null,
            message: String = "Something went wrong on our end. Please try again later.",
            technicalMessage: String? = null,
        ) = AppError(
            category = ErrorCategory.SERVER,
            title = "Server Error",
            userMessage = message,
            technicalMessage = technicalMessage,
            isRetryable = true,
            statusCode = statusCode,
        )

        /**
         * Create authentication error
         */
        fun auth(
            message: String = "Your session has expired. Please sign in again.",
            technicalMessage: String? = null,
        ) = AppError(
            category = ErrorCategory.AUTH,
            title = "Authentication Required",
            userMessage = message,
            technicalMessage = technicalMessage,
            isRetryable = false,
        )

        /**
         * Create unknown error
         */
        fun unknown(
            message: String = "An unexpected error occurred. Please try again.",
            technicalMessage: String? = null,
        ) = AppError(
            category = ErrorCategory.UNKNOWN,
            title = "Error",
            userMessage = message,
            technicalMessage = technicalMessage,
            isRetryable = true,
        )

        /**
         * Create error from exception
         */
        fun fromException(
            exception: Throwable,
            context: String? = null,
        ): AppError {
            return when {
                // Network-related exceptions
                exception.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
                    exception.message?.contains("Network is unreachable", ignoreCase = true) == true ||
                    exception.message?.contains("Connection refused", ignoreCase = true) == true ||
                    exception.message?.contains("timeout", ignoreCase = true) == true -> {
                    network(technicalMessage = "${context ?: ""}: ${exception.message}")
                }

                // Auth-related exceptions
                exception.message?.contains("401", ignoreCase = true) == true ||
                    exception.message?.contains("Unauthorized", ignoreCase = true) == true ||
                    exception.message?.contains("403", ignoreCase = true) == true -> {
                    auth(technicalMessage = "${context ?: ""}: ${exception.message}")
                }

                // Server errors
                exception.message?.contains("500", ignoreCase = true) == true ||
                    exception.message?.contains("502", ignoreCase = true) == true ||
                    exception.message?.contains("503", ignoreCase = true) == true -> {
                    server(technicalMessage = "${context ?: ""}: ${exception.message}")
                }

                // Default to unknown
                else -> {
                    unknown(
                        message = "An unexpected error occurred: ${exception.message ?: "Unknown error"}",
                        technicalMessage = "${context ?: ""}: ${exception.stackTraceToString()}",
                    )
                }
            }
        }

        /**
         * Create error from HTTP status code
         */
        fun fromStatusCode(
            statusCode: Int,
            message: String? = null,
            technicalMessage: String? = null,
        ): AppError {
            return when (statusCode) {
                in 400..499 -> {
                    when (statusCode) {
                        401, 403 ->
                            auth(
                                message = message ?: "Your session has expired. Please sign in again.",
                                technicalMessage = technicalMessage,
                            )
                        400, 422 ->
                            validation(
                                message = message ?: "Invalid request. Please check your input.",
                                technicalMessage = technicalMessage,
                            )
                        404 ->
                            AppError(
                                category = ErrorCategory.SERVER,
                                title = "Not Found",
                                userMessage = message ?: "The requested resource was not found.",
                                technicalMessage = technicalMessage,
                                isRetryable = false,
                                statusCode = statusCode,
                            )
                        else ->
                            server(
                                statusCode = statusCode,
                                message = message ?: "Something went wrong. Please try again.",
                                technicalMessage = technicalMessage,
                            )
                    }
                }
                in 500..599 ->
                    server(
                        statusCode = statusCode,
                        message = message ?: "Server error. Please try again later.",
                        technicalMessage = technicalMessage,
                    )
                else ->
                    unknown(
                        message = message ?: "An unexpected error occurred.",
                        technicalMessage = technicalMessage,
                    )
            }
        }
    }
}
