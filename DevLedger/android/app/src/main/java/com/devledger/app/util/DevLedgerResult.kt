package com.devledger.app.util

import java.io.IOException

/**
 * Lightweight result wrapper used across the repository layer so ViewModels can react
 * to success/failure without throwing across coroutine boundaries, and so offline
 * failures (no connectivity) are distinguishable from genuine API/server errors.
 */
sealed class DevLedgerResult<out T> {
    data class Success<T>(val data: T) : DevLedgerResult<T>()
    data class Error(val message: String, val isOffline: Boolean = false) : DevLedgerResult<Nothing>()

    companion object {
        inline fun <T> safeCall(block: () -> T): DevLedgerResult<T> = try {
            Success(block())
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (io: IOException) {
            Error(io.message ?: "Network unavailable — action queued for sync.", isOffline = true)
        } catch (e: Exception) {
            Error(e.message ?: "Unexpected error")
        }
    }
}

inline fun <T> DevLedgerResult<T>.onSuccess(action: (T) -> Unit): DevLedgerResult<T> {
    if (this is DevLedgerResult.Success) action(data)
    return this
}

inline fun <T> DevLedgerResult<T>.onError(action: (String, Boolean) -> Unit): DevLedgerResult<T> {
    if (this is DevLedgerResult.Error) action(message, isOffline)
    return this
}
