package casa.peterson.dhmo.core.update

import android.content.Context
import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import casa.peterson.dhmo.core.data.repository.InstallResult
import casa.peterson.dhmo.core.data.repository.UpdateController
import casa.peterson.dhmo.core.data.repository.UpdateFailure
import casa.peterson.dhmo.core.data.repository.UpdateStatus
import casa.peterson.dhmo.core.model.settings.UpdateSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Checks GitHub releases for a newer build and installs it, but only after the user has opted in: while
 * [UpdateSettings.enabled] is false no method here makes a request or leaves a file on disk.
 */
@Singleton
internal class UpdateControllerImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val deviceState: DeviceStateRepository,
    private val client: GitHubReleaseClient,
    private val downloader: ApkDownloader,
    private val verifier: ApkVerifier,
    private val installer: ApkInstaller,
    private val build: UpdateBuildInfo,
    private val clock: Clock,
) : UpdateController {
    private val mutableStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    override val status: StateFlow<UpdateStatus> = mutableStatus.asStateFlow()

    private val apkFile = File(context.cacheDir, "updates/update.apk")
    private val lock = Mutex()
    private var offered: Candidate? = null

    override suspend fun setEnabled(enabled: Boolean) {
        lock.withLock {
            deviceState.update { it.copy(updates = it.updates.copy(enabled = enabled, dismissedTag = null)) }
            if (!enabled) reset()
        }
        if (enabled) checkNow()
    }

    override suspend fun checkIfStale() {
        val settings = deviceState.current().updates
        val checkedRecently = settings.lastCheckAtMs?.let { clock.millis() - it in 0..STALE_AFTER_MS } == true
        // A new process starts Idle and has lost the offer, so it looks again even if the last check was recent.
        if (!settings.enabled || (checkedRecently && mutableStatus.value !is UpdateStatus.Idle)) return
        checkNow()
    }

    override suspend fun checkNow() {
        lock.withLock {
            val settings = deviceState.current().updates
            // Never replace an in-flight download or a verified APK with a fresh lookup.
            if (!settings.enabled || mutableStatus.value is UpdateStatus.Downloading ||
                mutableStatus.value is UpdateStatus.ReadyToInstall
            ) {
                return
            }
            if (!build.isDebugBuild) {
                mutableStatus.value = UpdateStatus.Failed(UpdateFailure.UNSUPPORTED)
                return
            }
            mutableStatus.value = UpdateStatus.Checking
            val fetched = try {
                client.fetch(build.repo)
            } catch (e: CancellationException) {
                offered = null
                mutableStatus.value = UpdateStatus.Idle
                throw e
            }
            when (fetched) {
                is ReleaseFetch.Failed -> {
                    offered = null
                    record(failed = true)
                    mutableStatus.value = UpdateStatus.Failed(fetched.reason)
                }

                is ReleaseFetch.Found -> {
                    record(failed = false)
                    val candidate = ReleaseSelector.select(fetched.releases, build)
                    if (candidate == null || candidate.update.tag == settings.dismissedTag) {
                        offered = null
                        mutableStatus.value = UpdateStatus.UpToDate
                    } else {
                        offered = candidate
                        mutableStatus.value = UpdateStatus.Available(candidate.update)
                    }
                }
            }
        }
    }

    override suspend fun download() {
        lock.withLock {
            val current = mutableStatus.value
            val candidate = offered?.takeIf { current is UpdateStatus.Available || current is UpdateStatus.Failed } ?: return
            if (!deviceState.current().updates.enabled) return
            val update = candidate.update
            mutableStatus.value = UpdateStatus.Downloading(update, null)
            try {
                val failure = downloader.download(candidate.downloadUrl, apkFile, update.apkSizeBytes, candidate.sha256) { percent ->
                    mutableStatus.value = UpdateStatus.Downloading(update, percent)
                } ?: verifyDownloaded()
                mutableStatus.value = if (failure == null) UpdateStatus.ReadyToInstall(update) else UpdateStatus.Failed(failure)
            } catch (e: CancellationException) {
                apkFile.delete()
                mutableStatus.value = UpdateStatus.Available(update)
                throw e
            }
        }
    }

    private fun verifyDownloaded(): UpdateFailure? = if (verifier.isUpdateForThisApp(apkFile)) {
        null
    } else {
        apkFile.delete()
        UpdateFailure.WRONG_SIGNATURE
    }

    override suspend fun dismiss() {
        lock.withLock {
            val tag = offered?.update?.tag ?: return
            deviceState.update { it.copy(updates = it.updates.copy(dismissedTag = tag)) }
            reset()
            mutableStatus.value = UpdateStatus.UpToDate
        }
    }

    override fun install(): InstallResult = if (mutableStatus.value is UpdateStatus.ReadyToInstall &&
        apkFile.exists()
    ) {
        installer.install(apkFile)
    } else {
        InstallResult.NOTHING_TO_INSTALL
    }

    private fun reset() {
        offered = null
        apkFile.delete()
        mutableStatus.value = UpdateStatus.Idle
    }

    private suspend fun record(failed: Boolean) {
        val now = clock.millis()
        deviceState.update { it.copy(updates = it.updates.copy(lastCheckAtMs = now, lastCheckFailed = failed)) }
    }

    private companion object {
        const val STALE_AFTER_MS = 60L * 60 * 1000
    }
}
