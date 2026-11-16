package casa.peterson.dhmo.core.backup

import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    private val full = emptyDocument(fullEntities(), AppSettings(units = VolumeUnit.L, dayStartMinutes = 240))
    private val sparse = emptyDocument()

    private fun errorOf(block: () -> Unit): BackupError = assertThrows(BackupException::class.java) { block() }.error

    @Test
    fun `compressed input cannot expand past the document limit`() {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { gzip ->
            val chunk = ByteArray(1024 * 1024) { ' '.code.toByte() }
            repeat(33) { gzip.write(chunk) }
        }
        val compressed = out.toByteArray()
        val error = assertThrows(BackupException::class.java) { BackupCodec.decode(compressed) }
        assertThat(error.error).isEqualTo(BackupError.CORRUPT)
        assertThat(error.message).contains("size limit")
        val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey("correct horse".toCharArray(), salt, 1_000)
        val encrypted = BackupCrypto.encrypt(compressed, key, salt, 1_000)
        val encryptedError = assertThrows(BackupException::class.java) {
            BackupCodec.decode(encrypted, "correct horse".toCharArray())
        }
        assertThat(encryptedError.message).contains("size limit")
    }

    @Test
    fun `oversized plain documents are rejected before parsing`() {
        val bytes = ByteArray(33 * 1024 * 1024) { ' '.code.toByte() }
        bytes[0] = '{'.code.toByte()
        val error = assertThrows(BackupException::class.java) { BackupCodec.decode(bytes) }
        assertThat(error.error).isEqualTo(BackupError.CORRUPT)
        assertThat(error.message).contains("size limit")
    }

    @Test
    fun `plain json round trips with every field set`() {
        assertThat(BackupCodec.decode(BackupCodec.encodeJson(full))).isEqualTo(full)
        assertThat(BackupCodec.decode(BackupCodec.encodeJson(full, pretty = true))).isEqualTo(full)
    }

    @Test
    fun `gzip round trips with every field set and with none`() {
        assertThat(BackupCodec.decode(BackupCodec.encodeGzip(full))).isEqualTo(full)
        assertThat(BackupCodec.decode(BackupCodec.encodeGzip(sparse))).isEqualTo(sparse)
    }

    @Test
    fun `absent optionals are omitted`() {
        val text = String(BackupCodec.encodeJson(full))
        assertThat(text).doesNotContain("null")
        assertThat(String(BackupCodec.encodeJson(sparse))).doesNotContain("\"deletedAt\"")
    }

    @Test
    fun `pretty output has line breaks and compact does not`() {
        assertThat(String(BackupCodec.encodeJson(full, pretty = true))).contains("\n")
        assertThat(String(BackupCodec.encodeJson(full))).doesNotContain("\n")
    }

    @Test
    fun `detect recognises each container`() {
        assertThat(BackupCodec.detect(BackupCodec.encodeJson(full))).isEqualTo(BackupContainer.PLAIN_JSON)
        assertThat(BackupCodec.detect("  \n{}".toByteArray())).isEqualTo(BackupContainer.PLAIN_JSON)
        assertThat(BackupCodec.detect(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), '{'.code.toByte())))
            .isEqualTo(BackupContainer.PLAIN_JSON)
        assertThat(BackupCodec.detect(BackupCodec.encodeGzip(full))).isEqualTo(BackupContainer.GZIP)
        assertThat(BackupCodec.detect("DHMOENC1rest".toByteArray())).isEqualTo(BackupContainer.ENCRYPTED)
        assertThat(BackupCodec.detect("hello".toByteArray())).isEqualTo(BackupContainer.UNKNOWN)
        assertThat(BackupCodec.detect(ByteArray(0))).isEqualTo(BackupContainer.UNKNOWN)
    }

    @Test
    fun `utf-8 bom is accepted`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + BackupCodec.encodeJson(full)
        assertThat(BackupCodec.decode(bytes)).isEqualTo(full)
    }

    @Test
    fun `unknown keys are ignored`() {
        val text = String(BackupCodec.encodeJson(sparse)).replaceFirst("{", "{\"futureField\":{\"a\":1},")
        assertThat(BackupCodec.decode(text.toByteArray())).isEqualTo(sparse)
    }

    @Test
    fun `a newer schema version is rejected`() {
        val text = String(BackupCodec.encodeJson(sparse))
            .replace("\"schemaVersion\":1,\"appVersion\"", "\"schemaVersion\":2,\"appVersion\"")
        assertThat(errorOf { BackupCodec.decode(text.toByteArray()) }).isEqualTo(BackupError.NEWER_VERSION)
    }

    @Test
    fun `non backups are rejected`() {
        assertThat(errorOf { BackupCodec.decode("hello".toByteArray()) }).isEqualTo(BackupError.NOT_A_BACKUP)
        assertThat(errorOf { BackupCodec.decode("{\"format\":\"other\",\"schemaVersion\":1}".toByteArray()) })
            .isEqualTo(BackupError.NOT_A_BACKUP)
        assertThat(errorOf { BackupCodec.decode("{}".toByteArray()) }).isEqualTo(BackupError.NOT_A_BACKUP)
    }

    @Test
    fun `damaged data is corrupt`() {
        assertThat(errorOf { BackupCodec.decode("{\"format\":".toByteArray()) }).isEqualTo(BackupError.CORRUPT)
        assertThat(errorOf { BackupCodec.decode("{\"format\":\"dhmo-backup\",\"schemaVersion\":1}".toByteArray()) })
            .isEqualTo(BackupError.CORRUPT)
        val gzip = BackupCodec.encodeGzip(full)
        assertThat(errorOf { BackupCodec.decode(gzip.copyOf(gzip.size / 2)) }).isEqualTo(BackupError.CORRUPT)
    }

    @Test
    fun `encrypted input needs a passphrase and decodes with one`() {
        val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey("correct horse".toCharArray(), salt, 1_000)
        val envelope = BackupCrypto.encrypt(BackupCodec.encodeGzip(full), key, salt, 1_000)
        assertThat(errorOf { BackupCodec.decode(envelope) }).isEqualTo(BackupError.PASSPHRASE_REQUIRED)
        assertThat(errorOf { BackupCodec.decode(envelope, "wrong horse!".toCharArray()) }).isEqualTo(BackupError.WRONG_PASSPHRASE)
        assertThat(BackupCodec.decode(envelope, "correct horse".toCharArray())).isEqualTo(full)
    }
}
