package casa.peterson.dhmo.core.update

import casa.peterson.dhmo.core.data.repository.AvailableUpdate
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** What this running build is, so the selector can tell "newer" and "mine" from "other". */
internal data class UpdateBuildInfo(
    /** `owner/name` on GitHub. */
    val repo: String,
    /** The tag this build was published under; empty for a local build. */
    val buildTag: String,
    /** When the build was made, or when it was installed if the build did not record that. */
    val baselineMs: Long,
    /** Only debuggable builds can use this updater. */
    val isDebugBuild: Boolean,
    /** Only tests (which download from a local MockWebServer) turn this off. */
    val requireHttps: Boolean = true,
)

/** An offered release together with the asset to download. */
internal data class Candidate(val update: AvailableUpdate, val downloadUrl: String, val sha256: String?)

internal object ReleaseSelector {
    private const val SHA256_PREFIX = "sha256:"
    private const val HTTPS_PORT = 443

    /** Published CI builds compare run numbers; local builds use build/install time as a conservative baseline. */
    fun select(releases: List<GitHubRelease>, build: UpdateBuildInfo): Candidate? {
        if (!build.isDebugBuild) return null
        val currentRun = runNumber(build.buildTag)
        return releases.asSequence()
            .filter { !it.draft && it.prerelease }
            .mapNotNull { release ->
                val run = runNumber(release.tagName) ?: return@mapNotNull null
                val published = release.publishedAtMs ?: return@mapNotNull null
                if (currentRun?.let { run <= it } ?: (published <= build.baselineMs)) {
                    return@mapNotNull null
                }
                Triple(release, published, run)
            }
            .sortedByDescending { it.third }
            .mapNotNull { (release, published, _) -> candidate(release, published, build) }
            .firstOrNull()
    }

    private fun candidate(release: GitHubRelease, publishedMs: Long, build: UpdateBuildInfo): Candidate? {
        val asset = release.assets.filter {
            it.name == "dhmo-app-${release.tagName}.apk" && it.size > 0 &&
                validUrl(it, release, build) && (it.digest == null || SHA256.matches(it.digest))
        }.singleOrNull() ?: return null
        val update = AvailableUpdate(
            tag = release.tagName,
            name = release.name?.takeIf { it.isNotBlank() } ?: release.tagName,
            notes = release.body.orEmpty().trim(),
            publishedAtMs = publishedMs,
            apkName = asset.name,
            apkSizeBytes = asset.size,
        )
        return Candidate(
            update,
            asset.downloadUrl,
            asset.digest?.removePrefix(SHA256_PREFIX),
        )
    }

    private fun validUrl(asset: GitHubAsset, release: GitHubRelease, build: UpdateBuildInfo): Boolean {
        val url = asset.downloadUrl.toHttpUrlOrNull() ?: return false
        val repo = build.repo.split('/')
        return !build.requireHttps || (
            repo.size == 2 && url.scheme == "https" && url.host == "github.com" &&
                url.username.isEmpty() && url.password.isEmpty() && url.port == HTTPS_PORT &&
                url.pathSegments == listOf(repo[0], repo[1], "releases", "download", release.tagName, asset.name) &&
                url.query == null && url.fragment == null
            )
    }

    private fun runNumber(tag: String): Long? = DEBUG_TAG.matchEntire(tag)?.groupValues?.get(1)?.toLongOrNull()

    private val DEBUG_TAG = Regex("debug-([0-9]+)-[0-9a-fA-F]{7,40}")
    private val SHA256 = Regex("sha256:[0-9a-fA-F]{64}")
}
