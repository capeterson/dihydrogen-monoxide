package casa.peterson.dhmo.core.update

import casa.peterson.dhmo.core.data.repository.UpdateFailure
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkDownloaderTest {
    @get:Rule val folder = TemporaryFolder()
    private val server = MockWebServer()
    private val downloader = ApkDownloader(OkHttpClient(), Dispatchers.IO)
    private val bytes = ByteArray(200_000) { (it % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private lateinit var target: File

    @Before
    fun setUp() {
        server.start()
        target = File(folder.root, "updates/update.apk")
    }

    @After
    fun tearDown() = server.close()

    private fun serve(body: ByteArray = bytes, code: Int = 200) {
        server.enqueue(MockResponse.Builder().code(code).body(okio.Buffer().write(body)).build())
    }

    @Test
    fun `downloads, reports progress up to 100 and keeps the file`() = runTest {
        serve()
        val progress = mutableListOf<Int?>()
        val failure = downloader.download(server.url("/a.apk").toString(), target, bytes.size.toLong(), sha.uppercase()) { progress += it }
        assertThat(failure).isNull()
        assertThat(target.readBytes()).isEqualTo(bytes)
        assertThat(progress.last()).isEqualTo(100)
    }

    @Test
    fun `a wrong checksum deletes the file`() = runTest {
        serve()
        val failure = downloader.download(server.url("/a.apk").toString(), target, bytes.size.toLong(), "00".repeat(32)) {}
        assertThat(failure).isEqualTo(UpdateFailure.CHECKSUM)
        assertThat(target.exists()).isFalse()
    }

    @Test
    fun `a size mismatch or HTTP error is a download failure`() = runTest {
        serve()
        assertThat(downloader.download(server.url("/a.apk").toString(), target, 5, null) {}).isEqualTo(UpdateFailure.DOWNLOAD)
        serve(code = 404)
        assertThat(downloader.download(server.url("/a.apk").toString(), target, 0, null) {}).isEqualTo(UpdateFailure.DOWNLOAD)
        assertThat(target.exists()).isFalse()
    }

    @Test
    fun `cancellation deletes the partially downloaded APK`() = runTest {
        serve()
        try {
            downloader.download(server.url("/a.apk").toString(), target, bytes.size.toLong(), sha) {
                throw CancellationException("cancelled")
            }
        } catch (_: CancellationException) {
            // Cancellation propagates to the caller, but must not leave an installable partial file.
        }
        assertThat(target.exists()).isFalse()
    }

    @Test
    fun `no digest and unknown size still downloads`() = runTest {
        serve()
        assertThat(downloader.download(server.url("/a.apk").toString(), target, 0, null) {}).isNull()
        assertThat(target.length()).isEqualTo(bytes.size.toLong())
    }
}
