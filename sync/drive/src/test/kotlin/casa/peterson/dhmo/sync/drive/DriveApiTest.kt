package casa.peterson.dhmo.sync.drive

import casa.peterson.dhmo.core.backup.BackupError
import casa.peterson.dhmo.core.backup.BackupException
import casa.peterson.dhmo.sync.drive.api.DriveApi
import casa.peterson.dhmo.sync.drive.api.DriveEndpoints
import casa.peterson.dhmo.sync.drive.api.DriveTokenSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test

class DriveApiTest {
    private val server = MockWebServer()
    private val tokens = FakeTokens()
    private lateinit var api: DriveApi

    private class FakeTokens : DriveTokenSource {
        var issued = 0
        val invalidated = mutableListOf<String>()
        var available = true

        override suspend fun token(): String? = if (available) "token-${++issued}" else null

        override suspend fun invalidate(token: String) {
            invalidated += token
        }
    }

    @Before
    fun setUp() {
        server.start()
        api = DriveApi(OkHttpClient(), Json { ignoreUnknownKeys = true }, DriveEndpoints(server.url("/drive/v3/"), server.url("/upload/drive/v3/files")), tokens)
    }

    @After
    fun tearDown() = server.close()

    private fun respond(body: String = "", code: Int = 200) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private suspend fun failure(block: suspend () -> Unit): BackupError =
        (runCatching { block() }.exceptionOrNull() as BackupException).error

    @Test
    fun `list asks for the app data folder with the documented query`() = runTest {
        respond("""{"files":[{"id":"1","name":"hydration-backup-v1-20261115T030000Z.json.gz","size":"1234","createdTime":"2026-11-15T03:00:01.000Z","appProperties":{"entryCount":"42"}}]}""")
        val files = api.list()
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.url.encodedPath).isEqualTo("/drive/v3/files")
        assertThat(request.url.queryParameter("spaces")).isEqualTo("appDataFolder")
        assertThat(request.url.queryParameter("q")).isEqualTo("name contains 'hydration-backup-' and trashed = false")
        assertThat(request.url.queryParameter("orderBy")).isEqualTo("createdTime desc")
        assertThat(request.url.queryParameter("pageSize")).isEqualTo("100")
        assertThat(request.url.queryParameter("fields")).contains("appProperties")
        assertThat(request.headers["Authorization"]).isEqualTo("Bearer token-1")
        assertThat(files.single().sizeBytes).isEqualTo(1234)
        assertThat(files.single().appProperties).containsEntry("entryCount", "42")
    }

    @Test
    fun `list follows next page tokens`() = runTest {
        respond("""{"nextPageToken":"abc","files":[{"id":"1","name":"a"}]}""")
        respond("""{"files":[{"id":"2","name":"b"}]}""")
        assertThat(api.list().map { it.id }).containsExactly("1", "2").inOrder()
        server.takeRequest()
        assertThat(server.takeRequest().url.queryParameter("pageToken")).isEqualTo("abc")
    }

    @Test
    fun `upload is a multipart related post with metadata then bytes`() = runTest {
        respond("""{"id":"new","name":"x","size":"3"}""")
        val file = api.upload("hydration-backup-v1-20261115T030000Z.json.gz", byteArrayOf(1, 2, 3), "application/gzip", mapOf("schemaVersion" to "1"))
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.url.encodedPath).isEqualTo("/upload/drive/v3/files")
        assertThat(request.url.queryParameter("uploadType")).isEqualTo("multipart")
        assertThat(request.headers["Content-Type"]).startsWith("multipart/related; boundary=")
        val body = request.body!!.utf8()
        assertThat(body).contains("Content-Type: application/json; charset=UTF-8")
        assertThat(body).contains(""""parents":["appDataFolder"]""")
        assertThat(body).contains(""""name":"hydration-backup-v1-20261115T030000Z.json.gz"""")
        assertThat(body).contains(""""appProperties":{"schemaVersion":"1"}""")
        assertThat(body).contains("Content-Type: application/gzip")
        assertThat(file.id).isEqualTo("new")
    }

    @Test
    fun `download asks for the media of one file`() = runTest {
        respond("payload")
        assertThat(api.download("f1").toString(Charsets.UTF_8)).isEqualTo("payload")
        val request = server.takeRequest()
        assertThat(request.url.encodedPath).isEqualTo("/drive/v3/files/f1")
        assertThat(request.url.queryParameter("alt")).isEqualTo("media")
    }

    @Test
    fun `delete expects 204 and treats 404 as done`() = runTest {
        respond(code = 204)
        api.delete("f1")
        assertThat(server.takeRequest().method).isEqualTo("DELETE")
        respond(code = 404)
        api.delete("gone")
    }

    @Test
    fun `about reads quota and the account email`() = runTest {
        respond("""{"storageQuota":{"limit":"100","usage":"40"},"user":{"emailAddress":"a@b.c","displayName":"A"}}""")
        val about = api.about()
        assertThat(about.usageBytes).isEqualTo(40)
        assertThat(about.limitBytes).isEqualTo(100)
        assertThat(about.email).isEqualTo("a@b.c")
        assertThat(server.takeRequest().url.queryParameter("fields")).isEqualTo("storageQuota(limit,usage),user(emailAddress,displayName)")
    }

    @Test
    fun `a 401 refreshes the token and retries exactly once`() = runTest {
        respond(code = 401)
        respond("""{"files":[]}""")
        api.list()
        assertThat(tokens.invalidated).containsExactly("token-1")
        server.takeRequest()
        assertThat(server.takeRequest().headers["Authorization"]).isEqualTo("Bearer token-2")
    }

    @Test
    fun `a second 401 is an auth failure`() = runTest {
        respond(code = 401)
        respond(code = 401)
        assertThat(failure { api.list() }).isEqualTo(BackupError.AUTH)
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `quota, server errors and rate limits map to their errors`() = runTest {
        respond("""{"error":{"errors":[{"reason":"storageQuotaExceeded"}]}}""", code = 403)
        assertThat(failure { api.upload("n", byteArrayOf(), "application/gzip", emptyMap()) }).isEqualTo(BackupError.QUOTA)
        respond(code = 503)
        assertThat(failure { api.list() }).isEqualTo(BackupError.NETWORK)
        respond(code = 429)
        assertThat(failure { api.list() }).isEqualTo(BackupError.NETWORK)
    }

    @Test
    fun `no authorisation is an auth failure and no request is made`() = runTest {
        tokens.available = false
        assertThat(failure { api.list() }).isEqualTo(BackupError.AUTH)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `an unreachable server is a network failure`() = runTest {
        server.close()
        assertThat(failure { api.list() }).isEqualTo(BackupError.NETWORK)
    }
}
