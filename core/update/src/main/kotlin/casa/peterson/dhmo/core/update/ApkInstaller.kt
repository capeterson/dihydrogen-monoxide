package casa.peterson.dhmo.core.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import casa.peterson.dhmo.core.data.repository.InstallResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

internal fun interface ApkInstaller {
    fun install(apk: File): InstallResult
}

/**
 * Opens the system package installer on the verified APK. Android always asks the user to confirm; before the first
 * install it also needs "install unknown apps" switched on for this app, so that page is opened instead when it is off.
 */
internal class SystemApkInstaller @Inject constructor(@ApplicationContext private val context: Context) : ApkInstaller {
    override fun install(apk: File): InstallResult = try {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (launch(settings)) InstallResult.NEEDS_PERMISSION else InstallResult.NOTHING_TO_INSTALL
        } else {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            if (launch(view)) InstallResult.STARTED else InstallResult.NOTHING_TO_INSTALL
        }
    } catch (_: SecurityException) {
        InstallResult.NOTHING_TO_INSTALL
    } catch (_: IllegalArgumentException) {
        InstallResult.NOTHING_TO_INSTALL
    }

    private fun launch(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
