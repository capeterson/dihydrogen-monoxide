package casa.peterson.dhmo.core.backup

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptoTest {
    private val fast = 1_000
    private val pass = "correct horse".toCharArray()
    private val salt = ByteArray(16) { it.toByte() }
    private val plain = "hydration data".toByteArray()

    private fun encrypt(iterations: Int = fast): ByteArray =
        BackupCrypto.encrypt(plain, BackupCrypto.deriveKey(pass, salt, iterations), salt, iterations)

    private fun errorOf(block: () -> Unit): BackupError = assertThrows(BackupException::class.java) { block() }.error

    @Test
    fun `round trip with passphrase and with key`() {
        val envelope = encrypt()
        assertThat(BackupCrypto.decrypt(envelope, pass)).isEqualTo(plain)
        assertThat(BackupCrypto.decryptWithKey(envelope, BackupCrypto.deriveKey(pass, salt, fast))).isEqualTo(plain)
    }

    @Test
    fun `wrong passphrase is reported`() {
        assertThat(errorOf { BackupCrypto.decrypt(encrypt(), "another one!".toCharArray()) }).isEqualTo(BackupError.WRONG_PASSPHRASE)
    }

    @Test
    fun `header layout matches the documented offsets`() {
        val envelope = encrypt()
        val buffer = ByteBuffer.wrap(envelope)
        assertThat(envelope.copyOfRange(0, 8)).isEqualTo("DHMOENC1".toByteArray())
        assertThat(envelope[8].toInt()).isEqualTo(1)
        assertThat(envelope[9].toInt()).isEqualTo(1)
        assertThat(buffer.getInt(10)).isEqualTo(fast)
        assertThat(envelope.copyOfRange(14, 30)).isEqualTo(salt)
        assertThat(envelope.size).isEqualTo(42 + plain.size + 16)
    }

    @Test
    fun `nonce is fresh for every file`() {
        assertThat(encrypt().copyOfRange(30, 42)).isNotEqualTo(encrypt().copyOfRange(30, 42))
    }

    @Test
    fun `tampered header fails authentication`() {
        for (index in listOf(8, 14, 29, 30, 41)) {
            val envelope = encrypt()
            envelope[index] = (envelope[index].toInt() xor 1).toByte()
            val error = errorOf { BackupCrypto.decrypt(envelope, pass) }
            assertThat(error).isAnyOf(BackupError.WRONG_PASSPHRASE, BackupError.CORRUPT)
        }
    }

    @Test
    fun `tampered iteration count fails`() {
        val envelope = encrypt()
        envelope[13] = (envelope[13].toInt() xor 1).toByte()
        assertThat(errorOf { BackupCrypto.decrypt(envelope, pass) }).isEqualTo(BackupError.WRONG_PASSPHRASE)
    }

    @Test
    fun `tampered ciphertext and tag fail`() {
        for (index in listOf(42, 42 + plain.size)) {
            val envelope = encrypt()
            envelope[index] = (envelope[index].toInt() xor 1).toByte()
            assertThat(errorOf { BackupCrypto.decrypt(envelope, pass) }).isEqualTo(BackupError.WRONG_PASSPHRASE)
        }
    }

    @Test
    fun `truncated or foreign data is corrupt`() {
        assertThat(errorOf { BackupCrypto.decrypt(encrypt().copyOf(50), pass) }.name).isAnyOf("CORRUPT", "WRONG_PASSPHRASE")
        assertThat(errorOf { BackupCrypto.decrypt(ByteArray(10), pass) }).isEqualTo(BackupError.CORRUPT)
        assertThat(errorOf { BackupCrypto.decrypt(ByteArray(100), pass) }).isEqualTo(BackupError.CORRUPT)
    }

    @Test
    fun `key derivation is deterministic and 32 bytes`() {
        val key = BackupCrypto.deriveKey(pass, salt, fast)
        assertThat(key).hasLength(32)
        assertThat(BackupCrypto.deriveKey(pass, salt, fast)).isEqualTo(key)
        assertThat(BackupCrypto.deriveKey(pass, BackupCrypto.newSalt(), fast)).isNotEqualTo(key)
        assertThat(BackupCrypto.newSalt()).hasLength(16)
    }

    @Test
    fun `passphrases are NFKC normalised`() {
        val composed = "café-pass".toCharArray()
        val decomposed = "café-pass".toCharArray()
        assertThat(BackupCrypto.deriveKey(composed, salt, fast)).isEqualTo(BackupCrypto.deriveKey(decomposed, salt, fast))
        assertThat(BackupCrypto.isAcceptablePassphrase("short".toCharArray())).isFalse()
        assertThat(BackupCrypto.isAcceptablePassphrase("longenough".toCharArray())).isTrue()
    }

    @Test
    fun `the default iteration count is 600000 and round trips`() {
        assertThat(BackupCrypto.ITERATIONS).isEqualTo(600_000)
        val key = BackupCrypto.deriveKey(pass, salt)
        val envelope = BackupCrypto.encrypt(plain, key, salt)
        assertThat(ByteBuffer.wrap(envelope).getInt(10)).isEqualTo(600_000)
        assertThat(BackupCrypto.decrypt(envelope, pass)).isEqualTo(plain)
    }
}
