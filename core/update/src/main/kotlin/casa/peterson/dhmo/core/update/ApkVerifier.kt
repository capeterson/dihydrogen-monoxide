package casa.peterson.dhmo.core.update

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/** Decides whether a downloaded APK would be accepted as an in-place update of this app. */
internal fun interface ApkVerifier {
    fun isUpdateForThisApp(apk: File): Boolean
}

/**
 * Same package name and same signing certificate as the installed app. Android enforces this at install time too, but
 * checking first lets the app say "signed with a different key" instead of showing a bare installer error, and means a
 * file that is not ours is never handed to the installer.
 */
internal class SignatureApkVerifier @Inject constructor(@ApplicationContext private val context: Context) : ApkVerifier {
    override fun isUpdateForThisApp(apk: File): Boolean = try {
        val packages = context.packageManager
        val archive = packages.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
        val installed = packages.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val archiveSigners = archive?.signingInfo?.apkContentsSigners?.toSet().orEmpty()
        val installedSigners = installed.signingInfo?.apkContentsSigners?.toSet().orEmpty()
        archive?.packageName == context.packageName && archive.longVersionCode >= installed.longVersionCode &&
            archiveSigners.isNotEmpty() && archiveSigners == installedSigners
    } catch (_: RuntimeException) {
        false
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
