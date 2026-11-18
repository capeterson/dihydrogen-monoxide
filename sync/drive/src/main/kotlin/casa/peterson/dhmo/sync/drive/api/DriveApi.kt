package casa.peterson.dhmo.sync.drive.api

import casa.peterson.dhmo.core.backup.BackupError
import casa.peterson.dhmo.core.backup.BackupException
import casa.peterson.dhmo.core.network.HttpResult
import casa.peterson.dhmo.core.network.awaitResult
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Where Google serves the REST API and the upload endpoint. Tests point both at a `MockWebServer`. */
data class DriveEndpoints(
    val api: HttpUrl = "https://www.googleapis.com/drive/v3/".toHttpUrl(),
    val upload: HttpUrl = "https://www.googleapis.com/upload/drive/v3/files".toHttpUrl(),
)

/** Hands out access tokens. A token that was rejected is passed back so a fresh one can be requested. */
interface DriveTokenSource {
    suspend fun token(): String?

    suspend fun invalidate(token: String)
}

data class DriveFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val createdAt: Instant?,
    val appProperties: Map<String, String>,
)

data class DriveAbout(val usageBytes: Long?, val limitBytes: Long?, val email: String?)

@Serializable
private data class FileDto(
    val id: String,
    val name: String = "",
    val size: String? = null,
    val createdTime: String? = null,
    val appProperties: Map<String, String> = emptyMap(),
)

@Serializable
private data class ListDto(val nextPageToken: String? = null, val files: List<FileDto> = emptyList())

@Serializable
private data class AboutDto(val storageQuota: Quota? = null, val user: User? = null) {
    @Serializable
    data class Quota(val limit: String? = null, val usage: String? = null)

    @Serializable
    data class User(@SerialName("emailAddress") val emailAddress: String? = null)
}

/**
 * A thin Drive v3 client over the `appDataFolder` (TECHNICAL_PLAN §11.6). Errors become [BackupException]:
 * 401 re-authorises once then `AUTH`, quota 403 is `QUOTA`, 429 and 5xx and I/O failures are `NETWORK`.
 */
class DriveApi @Inject internal constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val endpoints: DriveEndpoints,
    private val tokens: DriveTokenSource,
) {
    suspend fun list(): List<DriveFile> {
        val out = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val url = endpoints.api.newBuilder().addPathSegment("files")
                .addQueryParameter("spaces", "appDataFolder")
                .addQueryParameter("q", "name contains 'hydration-backup-' and trashed = false")
                .addQueryParameter("orderBy", "createdTime desc")
                .addQueryParameter("pageSize", "100")
                .addQueryParameter("fields", "nextPageToken,files(id,name,size,createdTime,modifiedTime,md5Checksum,appProperties)")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val page = parse(ListDto.serializer(), send { it.url(url).get() })
            out += page.files.map { it.toFile() }
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return out
    }

    suspend fun upload(name: String, bytes: ByteArray, mimeType: String, appProperties: Map<String, String>): DriveFile {
        val metadata: JsonObject = buildJsonObject {
            put("name", name)
            put("parents", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("appDataFolder")) })
            put("mimeType", mimeType)
            put("appProperties", buildJsonObject { appProperties.forEach { (k, v) -> put(k, v) } })
        }
        val url = endpoints.upload.newBuilder()
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", "id,name,size,createdTime,appProperties")
            .build()
        return parse(FileDto.serializer(), 
            send { builder ->
                val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
                    .addPart(json.encodeToString(JsonObject.serializer(), metadata).toRequestBody("application/json; charset=UTF-8".toMediaType()))
                    .addPart(bytes.toRequestBody(mimeType.toMediaType()))
                    .build()
                builder.url(url).post(body)
            },
        ).toFile()
    }

    suspend fun download(fileId: String): ByteArray {
        val url = endpoints.api.newBuilder().addPathSegment("files").addPathSegment(fileId).addQueryParameter("alt", "media").build()
        return send { it.url(url).get() }.body
    }

    /** A 404 means it is already gone, which is what the caller wanted. */
    suspend fun delete(fileId: String) {
        val url = endpoints.api.newBuilder().addPathSegment("files").addPathSegment(fileId).build()
        send(allow404 = true) { it.url(url).delete() }
    }

    suspend fun about(): DriveAbout {
        val url = endpoints.api.newBuilder().addPathSegment("about")
            .addQueryParameter("fields", "storageQuota(limit,usage),user(emailAddress,displayName)").build()
        val about = parse(AboutDto.serializer(), send { it.url(url).get() })
        return DriveAbout(about.storageQuota?.usage?.toLongOrNull(), about.storageQuota?.limit?.toLongOrNull(), about.user?.emailAddress)
    }

    private suspend fun send(allow404: Boolean = false, build: (Request.Builder) -> Request.Builder): HttpResult {
        var attempt = 0
        while (true) {
            val token = tokens.token() ?: throw BackupException(BackupError.AUTH, "No Drive authorisation")
            val request = build(Request.Builder().header("Authorization", "Bearer $token")).build()
            val result = try {
                client.newCall(request).awaitResult()
            } catch (e: IOException) {
                throw BackupException(BackupError.NETWORK, cause = e)
            }
            when {
                result.successful -> return result
                result.code == HTTP_UNAUTHORIZED && attempt == 0 -> {
                    tokens.invalidate(token)
                    attempt++
                }

                result.code == HTTP_UNAUTHORIZED -> throw BackupException(BackupError.AUTH, "Drive rejected the token twice")
                result.code == HTTP_NOT_FOUND && allow404 -> return result
                result.code == HTTP_FORBIDDEN -> throw BackupException(if (result.text.contains("storageQuotaExceeded")) BackupError.QUOTA else BackupError.AUTH)
                else -> throw BackupException(BackupError.NETWORK, "HTTP ${result.code}")
            }
        }
    }

    private fun <T> parse(serializer: KSerializer<T>, result: HttpResult): T = try {
        json.decodeFromString(serializer, result.text)
    } catch (e: SerializationException) {
        throw BackupException(BackupError.NETWORK, "Unreadable Drive response", e)
    }

    private fun FileDto.toFile() = DriveFile(
        id = id,
        name = name,
        sizeBytes = size?.toLongOrNull() ?: 0L,
        createdAt = createdTime?.let { runCatching { Instant.parse(it) }.getOrNull() },
        appProperties = appProperties,
    )

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
    }
}
