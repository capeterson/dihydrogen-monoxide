package casa.peterson.dhmo.core.update

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class ReleaseSelectorTest {
    private val build = UpdateBuildInfo("o/r", "debug-5-abc1234", Instant.parse("2026-11-10T00:00:00Z").toEpochMilli(), true)
    private val tag = "debug-13-c2f0aea"
    private val name = "dhmo-app-$tag.apk"
    private val url = "https://github.com/o/r/releases/download/$tag/$name"
    private val asset = GitHubAsset(name, 100, "sha256:${"ab".repeat(32)}", url)
    private val release = GitHubRelease(tag, prerelease = true, publishedAt = "2026-11-14T12:00:00Z", assets = listOf(asset))

    @Test
    fun `selects the published debug phone asset`() {
        val picked = ReleaseSelector.select(listOf(release), build)
        assertThat(picked?.update?.tag).isEqualTo(tag)
        assertThat(picked?.sha256).isEqualTo("ab".repeat(32))
        assertThat(ReleaseSelector.select(listOf(release), build.copy(isDebugBuild = false))).isNull()
    }

    @Test
    fun `rejects drafts other releases and the running build`() {
        assertThat(ReleaseSelector.select(listOf(release.copy(draft = true)), build)).isNull()
        assertThat(ReleaseSelector.select(listOf(release.copy(prerelease = false)), build)).isNull()
        assertThat(ReleaseSelector.select(listOf(release.copy(tagName = "v1.0")), build)).isNull()
        assertThat(ReleaseSelector.select(listOf(release), build.copy(buildTag = tag))).isNull()
        assertThat(ReleaseSelector.select(listOf(release), build.copy(buildTag = "", baselineMs = Long.MAX_VALUE))).isNull()
    }

    @Test
    fun `tagged builds compare run numbers rather than publication or installation time`() {
        val olderTag = "debug-9-abcdef0"
        val older = release.copy(
            tagName = olderTag,
            publishedAt = "2026-11-18T12:00:00Z",
            assets = listOf(
                asset.copy(
                    name = "dhmo-app-$olderTag.apk",
                    downloadUrl = "https://github.com/o/r/releases/download/$olderTag/dhmo-app-$olderTag.apk",
                ),
            ),
        )
        val picked = ReleaseSelector.select(listOf(older, release), build.copy(baselineMs = Long.MAX_VALUE))
        assertThat(picked?.update?.tag).isEqualTo(tag)
        assertThat(ReleaseSelector.select(listOf(older), build.copy(buildTag = "debug-10-abcdef0"))).isNull()
    }

    @Test
    fun `rejects unrelated ambiguous and malformed assets`() {
        assertThat(ReleaseSelector.select(listOf(release.copy(assets = listOf(asset.copy(name = "dhmo-wear-$tag.apk")))), build)).isNull()
        assertThat(ReleaseSelector.select(listOf(release.copy(assets = listOf(asset, asset))), build)).isNull()
        assertThat(ReleaseSelector.select(listOf(release.copy(assets = listOf(asset.copy(digest = "sha256:bad")))), build)).isNull()
        assertThat(ReleaseSelector.select(listOf(release.copy(assets = listOf(asset.copy(size = 0)))), build)).isNull()
    }

    @Test
    fun `rejects URLs outside the advertised GitHub release`() {
        listOf(
            "http://github.com/o/r/releases/download/$tag/$name",
            "https://evil.example/o/r/releases/download/$tag/$name",
            "https://github.com/o/r/releases/download/debug-1-abcdef0/$name",
            "$url?token=secret",
        ).forEach { invalid ->
            assertThat(ReleaseSelector.select(listOf(release.copy(assets = listOf(asset.copy(downloadUrl = invalid)))), build)).isNull()
        }
    }
}
