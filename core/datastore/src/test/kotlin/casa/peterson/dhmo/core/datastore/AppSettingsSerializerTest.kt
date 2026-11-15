package casa.peterson.dhmo.core.datastore

import androidx.datastore.core.CorruptionException
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class AppSettingsSerializerTest {
    private suspend fun read(text: String) = AppSettingsSerializer.readFrom(ByteArrayInputStream(text.encodeToByteArray()))

    @Test
    fun `round trip`() = runTest {
        val original = AppSettings(units = VolumeUnit.IMP_FL_OZ, dayStartMinutes = 300)
        val out = ByteArrayOutputStream()
        AppSettingsSerializer.writeTo(original, out)
        assertThat(read(out.toString())).isEqualTo(original)
    }

    @Test
    fun `empty object gives defaults`() = runTest { assertThat(read("{}")).isEqualTo(AppSettings()) }

    @Test
    fun `unknown keys are ignored`() = runTest {
        assertThat(read("""{"units":"L","somethingNew":{"a":1}}""").units).isEqualTo(VolumeUnit.L)
    }

    @Test
    fun `values are sanitised on read`() = runTest { assertThat(read("""{"dayStartMinutes":99999}""").dayStartMinutes).isEqualTo(1439) }

    @Test
    fun `corrupt content raises a corruption exception`() = runTest {
        assertThrows(CorruptionException::class.java) { kotlinx.coroutines.runBlocking { read("not json") } }
        assertThrows(CorruptionException::class.java) { kotlinx.coroutines.runBlocking { read("""{"units":"PINTS"}""") } }
    }
}
