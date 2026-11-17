package casa.peterson.dhmo.feature.settings.export

import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

internal class FakeDocumentFiles(private val labels: Map<String, String> = emptyMap()) : DocumentFiles {
    val written = mutableMapOf<Uri, ByteArrayOutputStream>()
    val readable = mutableMapOf<Uri, ByteArray>()
    var failWrites = false

    fun text(uri: Uri): String = written.getValue(uri).toString(Charsets.UTF_8.name())

    override fun openOutput(uri: Uri): OutputStream {
        if (failWrites) throw IOException("disk full")
        return ByteArrayOutputStream().also { written[uri] = it }
    }

    override fun read(uri: Uri, maxBytes: Int): ByteArray = readable[uri] ?: throw IOException("missing $uri")

    override fun appLabel(packageName: String): String? = labels[packageName]
}
