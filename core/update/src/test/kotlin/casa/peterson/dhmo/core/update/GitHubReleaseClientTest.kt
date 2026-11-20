package casa.peterson.dhmo.core.update

import casa.peterson.dhmo.core.data.repository.UpdateFailure
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test

class GitHubReleaseClientTest {
    private val server = MockWebServer()
    private lateinit var client: GitHubReleaseClient

    @Before
    fun setUp() {
        server.start()
        client = GitHubReleaseClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, UpdateEndpoints(server.url("/")))
    }

    @After
    fun tearDown() = server.close()

    private fun respond(body: String, code: Int = 200) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private val one = """{"tag_name":"v1","name":"One","body":"hi","draft":false,"prerelease":false,"published_at":"2026-11-14T12:00:00Z","unknown":1,
        "assets":[{"name":"a.apk","size":5,"digest":"sha256:ff","browser_download_url":"https://x/a.apk"}]}"""

    @Test
    fun `fetch lists releases without credentials`() = runTest {
        respond("[$one]")
        val result = client.fetch("owner/name") as ReleaseFetch.Found
        val request = server.takeRequest()
        assertThat(request.url.encodedPath).isEqualTo("/repos/owner/name/releases")
        assertThat(request.url.queryParameter("per_page")).isEqualTo("30")
        assertThat(request.headers["User-Agent"]).startsWith("DihydrogenMonoxide")
        assertThat(request.headers["Authorization"]).isNull()
        assertThat(result.releases.single().assets.single().digest).isEqualTo("sha256:ff")
        assertThat(result.releases.single().publishedAtMs).isEqualTo(1_794_657_600_000)
    }

    @Test
    fun `errors map to failures`() = runTest {
        respond("rate limited", code = 403)
        assertThat(client.fetch("owner/name")).isEqualTo(ReleaseFetch.Failed(UpdateFailure.HTTP))
        respond("not json")
        assertThat(client.fetch("owner/name")).isEqualTo(ReleaseFetch.Failed(UpdateFailure.PARSE))
        assertThat(client.fetch("no-slash")).isEqualTo(ReleaseFetch.Failed(UpdateFailure.PARSE))
        server.close()
        assertThat(client.fetch("owner/name")).isEqualTo(ReleaseFetch.Failed(UpdateFailure.NETWORK))
    }
}
