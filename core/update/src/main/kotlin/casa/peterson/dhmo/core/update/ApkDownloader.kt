package casa.peterson.dhmo.core.update

import casa.peterson.dhmo.core.common.DhmoDispatcher
import casa.peterson.dhmo.core.common.Dispatcher
import casa.peterson.dhmo.core.data.repository.UpdateFailure
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Streams an APK to disk, reporting progress, and checks its size and (when GitHub supplies one) SHA-256. */
internal class ApkDownloader @Inject constructor(
    client: OkHttpClient,
    @Dispatcher(DhmoDispatcher.IO) private val io: CoroutineDispatcher,
) {
    /** Release downloads redirect to GitHub's CDN over https; never follow a redirect that downgrades to http. */
    private val client = client.newBuilder().followSslRedirects(false).build()

    /** Null on success, else why it failed (a partial file is deleted). [onProgress] gets 0..100, or null if the size is unknown. */
    suspend fun download(url: String, target: File, expectedSize: Long, sha256: String?, onProgress: (Int?) -> Unit): UpdateFailure? =
        withContext(io) {
            target.parentFile?.mkdirs()
            var success = false
            try {
                val failure = fetch(url, target, expectedSize, sha256, onProgress)
                success = failure == null
                failure
            } catch (_: IOException) {
                UpdateFailure.DOWNLOAD
            } finally {
                if (!success) target.delete()
            }
        }

    private suspend fun fetch(url: String, target: File, expectedSize: Long, sha256: String?, onProgress: (Int?) -> Unit): UpdateFailure? {
        val request = Request.Builder().url(url).header("User-Agent", GitHubReleaseClient.USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return UpdateFailure.DOWNLOAD
            val total = response.body.contentLength().takeIf { it > 0 } ?: expectedSize.takeIf { it > 0 }
            val digest = MessageDigest.getInstance("SHA-256")
            val written = response.body.byteStream().use {
                copy(it, target, digest, expectedSize) { bytes -> onProgress(percent(bytes, total)) }
            }
            return verify(written, expectedSize, digest.digest(), sha256)
        }
    }

    private suspend fun copy(input: InputStream, target: File, digest: MessageDigest, expectedSize: Long, onBytes: (Long) -> Unit): Long {
        var written = 0L
        val buffer = ByteArray(BUFFER_BYTES)
        target.outputStream().use { output ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                if (expectedSize > 0 && read > expectedSize - written) throw IOException("APK exceeds advertised size")
                output.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                written += read
                onBytes(written)
            }
        }
        return written
    }

    private fun verify(written: Long, expectedSize: Long, digest: ByteArray, sha256: String?): UpdateFailure? = when {
        expectedSize > 0 && written != expectedSize -> UpdateFailure.DOWNLOAD
        sha256 != null && !digest.joinToString("") { "%02x".format(it) }.equals(sha256, ignoreCase = true) -> UpdateFailure.CHECKSUM
        else -> null
    }

    private fun percent(written: Long, total: Long?): Int? = total?.let { (written * PERCENT / it).coerceIn(0L, PERCENT).toInt() }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
        const val PERCENT = 100L
    }
}
