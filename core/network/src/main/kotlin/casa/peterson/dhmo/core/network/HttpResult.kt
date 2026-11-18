package casa.peterson.dhmo.core.network

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/** A status code and the whole body, read before the response is closed. */
data class HttpResult(val code: Int, val body: ByteArray) {
    val text: String get() = body.toString(Charsets.UTF_8)
    val successful: Boolean get() = code in SUCCESS_RANGE

    private companion object {
        val SUCCESS_RANGE = 200..299
    }
}

/** Runs [call] without blocking a thread. Cancelling the coroutine cancels the call; network failures throw [IOException]. */
suspend fun Call.awaitResult(): HttpResult = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)

            override fun onResponse(call: Call, response: Response) {
                try {
                    continuation.resume(response.use { HttpResult(it.code, it.body.bytes()) })
                } catch (e: IOException) {
                    continuation.resumeWithException(e)
                }
            }
        },
    )
}
