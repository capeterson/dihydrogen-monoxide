package casa.peterson.dhmo.feature.settings.export

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject

/** Storage Access Framework documents and installed-app labels, behind a seam so the view models are JVM-testable. */
internal interface DocumentFiles {
    /** Opens [uri] (from `ACTION_CREATE_DOCUMENT`) for writing, truncating any existing content. */
    fun openOutput(uri: Uri): OutputStream

    /** Reads [uri] (from `ACTION_OPEN_DOCUMENT`); throws [IOException] when it is larger than [maxBytes]. */
    fun read(uri: Uri, maxBytes: Int): ByteArray

    /** The user-visible name of an installed app, or null when it is not installed. */
    fun appLabel(packageName: String): String?
}

internal class ContentResolverDocumentFiles @Inject constructor(@ApplicationContext private val context: Context) : DocumentFiles {
    override fun openOutput(uri: Uri): OutputStream =
        context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open $uri for writing")

    override fun read(uri: Uri, maxBytes: Int): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
        return input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var count = stream.read(buffer)
            while (count != -1) {
                if (out.size() + count > maxBytes) throw IOException("File too large")
                out.write(buffer, 0, count)
                count = stream.read(buffer)
            }
            out.toByteArray()
        }
    }

    override fun appLabel(packageName: String): String? = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DocumentFilesModule {
    @Binds
    abstract fun documentFiles(impl: ContentResolverDocumentFiles): DocumentFiles
}
