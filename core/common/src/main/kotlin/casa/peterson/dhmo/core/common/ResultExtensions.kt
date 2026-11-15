package casa.peterson.dhmo.core.common

import kotlin.coroutines.cancellation.CancellationException

/** Like [runCatching] for suspending code, but never swallows coroutine cancellation. */
suspend inline fun <T> suspendRunCatching(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
    Result.failure(error)
}
