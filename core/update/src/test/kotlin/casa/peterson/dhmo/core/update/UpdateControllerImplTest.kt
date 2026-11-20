package casa.peterson.dhmo.core.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.data.repository.InstallResult
import casa.peterson.dhmo.core.data.repository.UpdateFailure
import casa.peterson.dhmo.core.data.repository.UpdateStatus
import casa.peterson.dhmo.core.testing.FakeDeviceStateRepository
import casa.peterson.dhmo.core.testing.MutableClock
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateControllerImplTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val server = MockWebServer()
    private val deviceState = FakeDeviceStateRepository()
    private val clock = MutableClock(Instant.parse("2026-11-20T00:00:00Z"))
    private var signatureOk = true
    private var installed: File? = null
    private lateinit var controller: UpdateControllerImpl

    private val apkBytes = ByteArray(1000) { it.toByte() }

    private fun releaseJson(tag: String = "debug-13-c2f0aea") = """{"tag_name":"$tag","name":"Two","prerelease":true,
        "published_at":"2026-11-18T00:00:00Z",
        "assets":[{"name":"dhmo-app-$tag.apk","size":1000,"browser_download_url":"${server.url("/dl/a.apk")}"}]}"""

    @Before
    fun setUp() {
        server.start()
        val okHttp = OkHttpClient()
        controller = UpdateControllerImpl(
            context,
            deviceState,
            GitHubReleaseClient(okHttp, Json { ignoreUnknownKeys = true }, UpdateEndpoints(server.url("/"))),
            ApkDownloader(okHttp, Dispatchers.IO),
            { signatureOk },
            { apk ->
                installed = apk
                InstallResult.STARTED
            },
            UpdateBuildInfo(
                "o/r",
                "v1.0.0",
                Instant.parse("2026-11-10T00:00:00Z").toEpochMilli(),
                isDebugBuild = true,
                requireHttps = false,
            ),
            clock,
        )
    }

    @After
    fun tearDown() {
        server.close()
        File(context.cacheDir, "updates").deleteRecursively()
    }

    private fun json(body: String) = server.enqueue(MockResponse.Builder().body(body).build())

    @Test
    fun `nothing touches the network until the user opts in`() = runTest {
        controller.checkIfStale()
        controller.checkNow()
        controller.download()
        assertThat(server.requestCount).isEqualTo(0)
        assertThat(controller.status.value).isEqualTo(UpdateStatus.Idle)
        assertThat(deviceState.current().updates.enabled).isFalse()
    }

    @Test
    fun `opting in checks straight away and offers the newer release`() = runTest {
        json("[${releaseJson()}]")
        assertThat(server.requestCount).isEqualTo(0)
        controller.setEnabled(true)
        val status = controller.status.value as UpdateStatus.Available
        assertThat(status.update.tag).isEqualTo("debug-13-c2f0aea")
        assertThat(deviceState.current().updates.lastCheckAtMs).isEqualTo(clock.millis())
    }

    @Test
    fun `download then install hands the verified file to the installer`() = runTest {
        json("[${releaseJson()}]")
        controller.setEnabled(true)
        server.enqueue(MockResponse.Builder().body(okio.Buffer().write(apkBytes)).build())
        controller.download()
        assertThat(controller.status.value).isInstanceOf(UpdateStatus.ReadyToInstall::class.java)
        assertThat(controller.install()).isEqualTo(InstallResult.STARTED)
        assertThat(installed?.readBytes()).isEqualTo(apkBytes)
    }

    @Test
    fun `an APK signed with another key is deleted and never installed`() = runTest {
        json("[${releaseJson()}]")
        controller.setEnabled(true)
        signatureOk = false
        server.enqueue(MockResponse.Builder().body(okio.Buffer().write(apkBytes)).build())
        controller.download()
        assertThat(controller.status.value).isEqualTo(UpdateStatus.Failed(UpdateFailure.WRONG_SIGNATURE))
        assertThat(controller.install()).isEqualTo(InstallResult.NOTHING_TO_INSTALL)
        assertThat(File(context.cacheDir, "updates/update.apk").exists()).isFalse()
    }

    @Test
    fun `skipping a version hides it until a newer one appears`() = runTest {
        json("[${releaseJson()}]")
        controller.setEnabled(true)
        controller.dismiss()
        assertThat(deviceState.current().updates.dismissedTag).isEqualTo("debug-13-c2f0aea")
        json("[${releaseJson()}]")
        controller.checkNow()
        assertThat(controller.status.value).isEqualTo(UpdateStatus.UpToDate)
    }

    @Test
    fun `a failed check reports why and is retried on the next start`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).build())
        controller.setEnabled(true)
        assertThat(controller.status.value).isEqualTo(UpdateStatus.Failed(UpdateFailure.HTTP))
        assertThat(deviceState.current().updates.lastCheckFailed).isTrue()
    }

    @Test
    fun `a failed refresh cannot download a stale offer`() = runTest {
        json("[${releaseJson()}]")
        controller.setEnabled(true)
        server.enqueue(MockResponse.Builder().code(500).build())
        controller.checkNow()
        controller.download()
        assertThat(controller.status.value).isEqualTo(UpdateStatus.Failed(UpdateFailure.HTTP))
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `production builds report unsupported without contacting GitHub`() = runTest {
        val http = OkHttpClient()
        val production = UpdateControllerImpl(
            context,
            deviceState,
            GitHubReleaseClient(http, Json { ignoreUnknownKeys = true }, UpdateEndpoints(server.url("/"))),
            ApkDownloader(http, Dispatchers.IO),
            { true },
            { InstallResult.STARTED },
            UpdateBuildInfo("o/r", "", 0, isDebugBuild = false),
            clock,
        )
        production.setEnabled(true)
        assertThat(production.status.value).isEqualTo(UpdateStatus.Failed(UpdateFailure.UNSUPPORTED))
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `turning updates off clears the offer and the cached file`() = runTest {
        json("[${releaseJson()}]")
        controller.setEnabled(true)
        controller.setEnabled(false)
        assertThat(controller.status.value).isEqualTo(UpdateStatus.Idle)
        assertThat(deviceState.current().updates.enabled).isFalse()
    }
}
