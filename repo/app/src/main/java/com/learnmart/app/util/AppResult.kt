package com.learnmart.app.util

sealed interface AppResult<out T> {
    data class Success<T>(
        val data: T,
        val warnings: List<String> = emptyList()
    ) : AppResult<T>

    data class ValidationError(
        val fieldErrors: Map<String, String> = emptyMap(),
        val globalErrors: List<String> = emptyList()
    ) : AppResult<Nothing>

    data class ConflictError(
        val code: String,
        val message: String
    ) : AppResult<Nothing>

    data class PermissionError(
        val code: String = "FORBIDDEN"
    ) : AppResult<Nothing>

    data class NotFoundError(
        val code: String = "NOT_FOUND"
    ) : AppResult<Nothing>

    data class SystemError(
        val code: String,
        val message: String,
        val retryable: Boolean
    ) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data), warnings)
    is AppResult.ValidationError -> this
    is AppResult.ConflictError -> this
    is AppResult.PermissionError -> this
    is AppResult.NotFoundError -> this
    is AppResult.SystemError -> this
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(data)
    return this
}

inline fun <T> AppResult<T>.onError(action: (AppResult<Nothing>) -> Unit): AppResult<T> {
    if (this !is AppResult.Success) {
        @Suppress("UNCHECKED_CAST")
        action(this as AppResult<Nothing>)
    }
    return this
}
