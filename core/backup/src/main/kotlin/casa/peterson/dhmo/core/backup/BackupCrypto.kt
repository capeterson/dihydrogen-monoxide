package casa.peterson.dhmo.core.backup

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * PBKDF2-HMAC-SHA256 + AES-256-GCM, `javax.crypto` only (TECHNICAL_PLAN §11.4).
 * The header (the first [HEADER_SIZE] bytes) is authenticated as GCM additional data.
 */
object BackupCrypto {
    const val ITERATIONS = 600_000
    const val MIN_PASSPHRASE_LENGTH = 8
    const val SALT_SIZE = 16
    const val NONCE_SIZE = 12
    const val KEY_SIZE = 32
    const val HEADER_SIZE = 42

    private const val TAG_BITS = 128
    private const val TAG_BYTES = 16
    private const val KEY_BITS = 256
    private const val ENVELOPE_VERSION = 1
    private const val KDF_PBKDF2_SHA256 = 1
    private const val MAX_ITERATIONS = 10_000_000
    private const val UINT_MASK = 0xFFFFFFFFL
    private const val OFFSET_VERSION = 8
    private const val OFFSET_KDF = 9
    private const val OFFSET_ITERATIONS = 10
    private const val OFFSET_SALT = 14
    private const val OFFSET_NONCE = 30
    private val magic = "DHMOENC1".toByteArray(Charsets.US_ASCII)
    private val random = SecureRandom()

    /** True when [passphrase] meets the length rule (counted after NFKC normalisation). */
    fun isAcceptablePassphrase(passphrase: CharArray): Boolean = normalize(passphrase).size >= MIN_PASSPHRASE_LENGTH

    fun newSalt(): ByteArray = ByteArray(SALT_SIZE).also(random::nextBytes)

    fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val normalized = normalize(passphrase)
        val spec = PBEKeySpec(normalized, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            normalized.fill(' ')
        }
    }

    fun encrypt(plain: ByteArray, key: ByteArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        require(key.size == KEY_SIZE && salt.size == SALT_SIZE) { "bad key or salt size" }
        val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_SIZE)
            .put(magic).put(ENVELOPE_VERSION.toByte()).put(KDF_PBKDF2_SHA256.toByte())
            .putInt(iterations).put(salt).put(nonce).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain)
    }

    fun decrypt(envelope: ByteArray, passphrase: CharArray): ByteArray {
        val (salt, iterations) = readHeader(envelope)
        return decryptWithKey(envelope, deriveKey(passphrase, salt, iterations))
    }

    fun decryptWithKey(envelope: ByteArray, key: ByteArray): ByteArray {
        readHeader(envelope)
        require(key.size == KEY_SIZE) { "bad key size" }
        val nonce = envelope.copyOfRange(OFFSET_NONCE, HEADER_SIZE)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(envelope, 0, HEADER_SIZE)
            cipher.doFinal(envelope, HEADER_SIZE, envelope.size - HEADER_SIZE)
        } catch (e: AEADBadTagException) {
            throw BackupException(BackupError.WRONG_PASSPHRASE, cause = e)
        } catch (e: GeneralSecurityException) {
            throw BackupException(BackupError.CORRUPT, cause = e)
        }
    }

    /** Validates magic, versions and size, then returns the salt and the iteration count. */
    fun readHeader(envelope: ByteArray): Pair<ByteArray, Int> {
        val valid = envelope.size >= HEADER_SIZE + TAG_BYTES &&
            envelope.copyOfRange(0, magic.size).contentEquals(magic) &&
            envelope[OFFSET_VERSION].toInt() == ENVELOPE_VERSION &&
            envelope[OFFSET_KDF].toInt() == KDF_PBKDF2_SHA256
        if (!valid) throw BackupException(BackupError.CORRUPT, "bad envelope header")
        val iterations = ByteBuffer.wrap(envelope, OFFSET_ITERATIONS, Int.SIZE_BYTES).int.toLong() and UINT_MASK
        if (iterations < 1 || iterations > MAX_ITERATIONS) throw BackupException(BackupError.CORRUPT, "bad iteration count")
        return envelope.copyOfRange(OFFSET_SALT, OFFSET_NONCE) to iterations.toInt()
    }

    private fun normalize(passphrase: CharArray): CharArray = Normalizer.normalize(String(passphrase), Normalizer.Form.NFKC).toCharArray()
}
