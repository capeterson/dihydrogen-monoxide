package casa.peterson.dhmo.core.update

import android.content.Context
import android.content.pm.ApplicationInfo
import casa.peterson.dhmo.core.data.repository.UpdateController
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class UpdateModule {
    @Binds
    @Singleton
    abstract fun controller(impl: UpdateControllerImpl): UpdateController

    @Binds
    abstract fun verifier(impl: SignatureApkVerifier): ApkVerifier

    @Binds
    abstract fun installer(impl: SystemApkInstaller): ApkInstaller

    companion object {
        @Provides
        fun endpoints(): UpdateEndpoints = UpdateEndpoints()

        @Provides
        @Singleton
        fun buildInfo(@ApplicationContext context: Context): UpdateBuildInfo {
            val installedAtMs = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
            }.getOrDefault(0L)
            return UpdateBuildInfo(
                repo = BuildConfig.UPDATE_REPO,
                buildTag = BuildConfig.BUILD_TAG,
                baselineMs = BuildConfig.BUILD_TIME_MS.takeIf { it > 0 } ?: installedAtMs,
                isDebugBuild = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            )
        }
    }
}
