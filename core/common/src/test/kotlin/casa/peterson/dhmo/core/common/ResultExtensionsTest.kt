package casa.peterson.dhmo.core.common

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class ResultExtensionsTest {
    @Test
    fun `success is wrapped`() = runTest {
        assertThat(suspendRunCatching { 42 }.getOrNull()).isEqualTo(42)
    }

    @Test
    fun `failure is captured`() = runTest {
        val result = suspendRunCatching<Int> { error("boom") }
        assertThat(result.exceptionOrNull()).hasMessageThat().isEqualTo("boom")
    }

    @Test
    fun `cancellation is rethrown`() = runTest {
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { suspendRunCatching<Int> { throw CancellationException("stop") } }
        }
    }

    @Test
    fun `zone provider is a functional interface`() {
        val provider = ZoneProvider { ZoneId.of("Europe/Berlin") }
        assertThat(provider.current().id).isEqualTo("Europe/Berlin")
    }
}
