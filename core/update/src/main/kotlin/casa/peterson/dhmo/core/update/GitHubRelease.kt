package casa.peterson.dhmo.core.update

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The parts of GitHub's release JSON the updater reads. Unknown fields are ignored. */
@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
) {
    val publishedAtMs: Long? get() = publishedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
}

@Serializable
internal data class GitHubAsset(
    val name: String,
    val size: Long = 0,
    /** `sha256:<hex>`; GitHub fills this in for assets uploaded since mid-2025. */
    val digest: String? = null,
    @SerialName("browser_download_url") val downloadUrl: String,
)
