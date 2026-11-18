package casa.peterson.dhmo.sync.drive

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.sync.drive.crypto.BackupKeyStore
import com.google.common.truth.Truth.assertThat
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** The Android Keystore itself is not available on the JVM, so a software AES key stands in for it. */
@RunWith(AndroidJUnit4::class)
class BackupKeyStoreTest {
    private fun newKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun `a wrapped key unwraps to the same bytes`() {
        val store = BackupKeyStore(newKey().let { key -> { key } })
        val derived = ByteArray(32) { it.toByte() }
        assertThat(store.unwrap(store.wrap(derived))).isEqualTo(derived)
    }

    @Test
    fun `wrapping twice gives different output because the nonce is fresh`() {
        val store = BackupKeyStore(newKey().let { key -> { key } })
        val derived = ByteArray(32)
        assertThat(store.wrap(derived)).isNotEqualTo(store.wrap(derived))
    }

    @Test
    fun `a different wrapping key cannot unwrap`() {
        val wrapped = BackupKeyStore(newKey().let { key -> { key } }).wrap(ByteArray(32))
        val other = BackupKeyStore(newKey().let { key -> { key } })
        assertThrows(AEADBadTagException::class.java) { other.unwrap(wrapped) }
    }

    @Test
    fun `salts are 16 random bytes`() {
        val store = BackupKeyStore(newKey().let { key -> { key } })
        assertThat(store.newSalt()).hasLength(16)
        assertThat(store.newSalt()).isNotEqualTo(store.newSalt())
    }
}
