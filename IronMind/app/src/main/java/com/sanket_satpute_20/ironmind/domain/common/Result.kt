package com.sanket_satpute_20.ironmind.domain.common

/**
 * A sealed class representing the outcome of a domain operation.
 * Used instead of throwing exceptions for predictable control flow.
 */
sealed class Result<out T, out E> {
    data class Success<out T>(val data: T) : Result<T, Nothing>()
    data class Failure<out E>(val error: E) : Result<Nothing, E>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    fun errorOrNull(): E? = when (this) {
        is Success -> null
        is Failure -> error
    }

    inline fun <R> map(transform: (T) -> R): Result<R, E> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> Failure(error)
    }

    inline fun <R> mapError(transform: (E) -> R): Result<T, R> = when (this) {
        is Success -> Success(data)
        is Failure -> Failure(transform(error))
    }

    inline fun <R> fold(onSuccess: (T) -> R, onFailure: (E) -> R): R = when (this) {
        is Success -> onSuccess(data)
        is Failure -> onFailure(error)
    }
}
