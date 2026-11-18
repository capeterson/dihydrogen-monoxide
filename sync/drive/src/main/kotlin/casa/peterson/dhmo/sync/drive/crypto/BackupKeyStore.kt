package casa.peterson.dhmo.sync.drive.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

/** Supplies the key that wraps the derived backup key. Production keeps it in the Android Keystore. */
fun interface WrappingKeyProvider {
    fun key(): SecretKey
}

internal class AndroidKeystoreWrappingKey @Inject constructor() : WrappingKeyProvider {
    override fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "dhmo_backup_wrap"
        const val KEY_BITS = 256
    }
}

/**
 * Scheduled backups cannot ask for the passphrase, so the key derived from it is stored wrapped by a non-exportable
 * Keystore key (TECHNICAL_PLAN §11.4). The passphrase itself is never stored.
 */
class BackupKeyStore @Inject internal constructor(private val wrapping: WrappingKeyProvider) {
    /** `iv (12) + ciphertext + tag`, Base64. */
    fun wrap(derivedKey: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, wrapping.key()) }
        val sealed = cipher.doFinal(derivedKey)
        return Base64.getEncoder().encodeToString(cipher.iv + sealed)
    }

    fun unwrap(wrapped: String): ByteArray {
        val bytes = Base64.getDecoder().decode(wrapped)
        val cipher = Cipher.getInstance(TRANSFORMATION)
            .apply { init(Cipher.DECRYPT_MODE, wrapping.key(), GCMParameterSpec(TAG_BITS, bytes.copyOfRange(0, IV_SIZE))) }
        return cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE)
    }

    fun newSalt(): ByteArray = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        const val SALT_SIZE = 16
    }
}
