package casa.peterson.dhmo.core.update

import casa.peterson.dhmo.core.data.repository.UpdateFailure
import casa.peterson.dhmo.core.network.awaitResult
import java.io.IOException
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Where the GitHub REST API lives. Tests point it at a `MockWebServer`. */
internal data class UpdateEndpoints(val api: HttpUrl = "https://api.github.com/".toHttpUrl())

internal sealed interface ReleaseFetch {
    data class Found(val releases: List<GitHubRelease>) : ReleaseFetch

    data class Failed(val reason: UpdateFailure) : ReleaseFetch
}

/**
 * Reads releases from the GitHub REST API without credentials. The request carries nothing about the user or the
 * device beyond a fixed User-Agent.
 */
internal class GitHubReleaseClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val endpoints: UpdateEndpoints,
) {
    /** Lists recent releases; the selector accepts only compatible debug pre-releases. */
    suspend fun fetch(repo: String): ReleaseFetch {
        val (owner, name) = repo.split('/', limit = 2).takeIf { it.size == 2 } ?: return ReleaseFetch.Failed(UpdateFailure.PARSE)
        val url = endpoints.api.newBuilder().addPathSegment("repos").addPathSegment(owner).addPathSegment(name)
            .addPathSegment("releases").addQueryParameter("per_page", PAGE_SIZE.toString()).build()
        val request = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .build()
        return try {
            val result = client.newCall(request).awaitResult()
            if (!result.successful) {
                ReleaseFetch.Failed(UpdateFailure.HTTP)
            } else {
                ReleaseFetch.Found(json.decodeFromString(ListSerializer(GitHubRelease.serializer()), result.text))
            }
        } catch (_: IOException) {
            ReleaseFetch.Failed(UpdateFailure.NETWORK)
        } catch (_: SerializationException) {
            ReleaseFetch.Failed(UpdateFailure.PARSE)
        }
    }

    companion object {
        const val USER_AGENT = "DihydrogenMonoxide (Android)"
        const val PAGE_SIZE = 30
        private const val API_VERSION = "2022-11-28"
    }
}
