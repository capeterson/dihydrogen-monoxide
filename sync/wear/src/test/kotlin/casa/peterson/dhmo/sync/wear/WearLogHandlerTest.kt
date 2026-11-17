package casa.peterson.dhmo.sync.wear

import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.testing.FakeIntakeRepository
import casa.peterson.dhmo.core.testing.MutableClock
import casa.peterson.dhmo.core.wear.LogRequest
import casa.peterson.dhmo.core.wear.WearProtocol
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WearLogHandlerTest {
    private val intake = FakeIntakeRepository(MutableClock(Instant.parse("2026-11-15T12:00:00Z")))
    private val handler = WearLogHandler(intake)

    @Test
    fun `a drink from the watch keeps its id and time and is stored once`() = runTest {
        val id = UUID.randomUUID()
        // 23:30 at +01:00 on the 14th, logged while the phone was away and delivered the next day.
        val payload = WearProtocol.encode(LogRequest.create(330, Instant.parse("2026-11-14T22:30:00Z"), ZoneOffset.ofHours(1), id))

        assertThat(handler.handle(payload)).isTrue()
        assertThat(handler.handle(payload)).isFalse() // the queued data item after the message

        val entry = intake.all.single()
        assertThat(entry.id).isEqualTo(id)
        assertThat(entry.volumeMl).isEqualTo(330)
        assertThat(entry.source).isEqualTo(Source.WEAR)
        assertThat(entry.instant).isEqualTo(Instant.parse("2026-11-14T22:30:00Z"))
        assertThat(entry.hydrationDay).isEqualTo(LocalDate.of(2026, 11, 14))
    }

    @Test
    fun `malformed payloads are ignored`() = runTest {
        assertThat(handler.handle("{}".toByteArray())).isFalse()
        assertThat(handler.handle(ByteArray(3))).isFalse()
        assertThat(intake.all).isEmpty()
    }
}
