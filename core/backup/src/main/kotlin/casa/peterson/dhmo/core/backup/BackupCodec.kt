package casa.peterson.dhmo.core.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

enum class BackupContainer { PLAIN_JSON, GZIP, ENCRYPTED, UNKNOWN }

/** JSON + gzip + optional encryption container handling (TECHNICAL_PLAN §11.3). */
object BackupCodec {
    const val FORMAT = "dhmo-backup"

    @OptIn(ExperimentalSerializationApi::class)
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val prettyJson: Json = Json(json) { prettyPrint = true }

    private val magic = "DHMOENC1".toByteArray(Charsets.US_ASCII)
    private val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private const val GZIP_MAGIC_0 = 0x1F
    private const val GZIP_MAGIC_1 = 0x8B
    private const val BYTE_MASK = 0xFF

    fun encodeJson(doc: BackupDocument, pretty: Boolean = false): ByteArray =
        (if (pretty) prettyJson else json).encodeToString(BackupDocument.serializer(), doc).toByteArray(Charsets.UTF_8)

    fun encodeGzip(doc: BackupDocument): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(encodeJson(doc)) }
        return out.toByteArray()
    }

    fun detect(bytes: ByteArray): BackupContainer {
        val isGzip = bytes.size >= 2 &&
            bytes[0].toInt() and BYTE_MASK == GZIP_MAGIC_0 &&
            bytes[1].toInt() and BYTE_MASK == GZIP_MAGIC_1
        return when {
            bytes.startsWith(magic) -> BackupContainer.ENCRYPTED
            isGzip -> BackupContainer.GZIP
            startsWithBrace(bytes) -> BackupContainer.PLAIN_JSON
            else -> BackupContainer.UNKNOWN
        }
    }

    private fun startsWithBrace(bytes: ByteArray): Boolean {
        var i = if (bytes.startsWith(bom)) bom.size else 0
        while (i < bytes.size && bytes[i].toInt().toChar().isWhitespace()) i++
        return i < bytes.size && bytes[i] == '{'.code.toByte()
    }

    fun decode(bytes: ByteArray, passphrase: CharArray? = null): BackupDocument {
        val jsonBytes = when (detect(bytes)) {
            BackupContainer.ENCRYPTED -> {
                if (passphrase == null) throw BackupException(BackupError.PASSPHRASE_REQUIRED)
                gunzip(BackupCrypto.decrypt(bytes, passphrase))
            }

            BackupContainer.GZIP -> gunzip(bytes)

            BackupContainer.PLAIN_JSON -> bytes

            BackupContainer.UNKNOWN -> throw BackupException(BackupError.NOT_A_BACKUP)
        }
        return parse(jsonBytes)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && copyOfRange(0, prefix.size).contentEquals(prefix)

    private fun gunzip(bytes: ByteArray): ByteArray = try {
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
    } catch (e: IOException) {
        throw BackupException(BackupError.CORRUPT, "bad gzip data", e)
    }

    private fun parse(bytes: ByteArray): BackupDocument {
        val root = parseRoot(String(bytes, Charsets.UTF_8).removePrefix("\uFEFF"))
        val migrated = BackupMigrations.migrate(root)
        return try {
            json.decodeFromJsonElement(BackupDocument.serializer(), migrated)
        } catch (e: SerializationException) {
            throw BackupException(BackupError.CORRUPT, "document does not match schema", e)
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupError.CORRUPT, "document does not match schema", e)
        }
    }

    private fun parseRoot(text: String): JsonObject {
        val element = try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw BackupException(BackupError.CORRUPT, "invalid JSON", e)
        }
        val root = element as? JsonObject
        if (root == null || (root["format"] as? JsonPrimitive)?.content != FORMAT) {
            throw BackupException(BackupError.NOT_A_BACKUP)
        }
        return root
    }
}
