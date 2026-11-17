package casa.peterson.dhmo.core.wear

import casa.peterson.dhmo.core.model.settings.VolumeUnit
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Test

class WearProtocolTest {
    private val id = UUID.fromString("5f0c1a52-8f2e-4b6e-9a57-0d7b8f3c2a11")

    @Test
    fun `log request round trips with the watch's id, instant and offset`() {
        val request = LogRequest.create(250, Instant.parse("2026-11-15T07:05:00.123Z"), ZoneOffset.ofHours(1), id)

        val decoded = WearProtocol.decodeLog(WearProtocol.encode(request))

        assertThat(decoded).isEqualTo(request)
        assertThat(decoded!!.uuid).isEqualTo(id)
        assertThat(decoded.instant).isEqualTo(Instant.parse("2026-11-15T07:05:00.123Z"))
        assertThat(decoded.offset).isEqualTo(ZoneOffset.ofHours(1))
    }

    @Test
    fun `malformed or out-of-range requests are dropped`() {
        assertThat(WearProtocol.decodeLog("nope".toByteArray())).isNull()
        assertThat(WearProtocol.decodeLog("""{"id":"x","epochMillis":0,"offsetSeconds":0,"volumeMl":250}""".toByteArray())).isNull()
        assertThat(WearProtocol.decodeLog(WearProtocol.encode(LogRequest(id.toString(), 0, 0, 0)))).isNull()
        assertThat(WearProtocol.decodeLog(WearProtocol.encode(LogRequest(id.toString(), 0, 0, 10_001)))).isNull()
        assertThat(WearProtocol.decodeLog(WearProtocol.encode(LogRequest(id.toString(), 0, 99_999, 250)))).isNull()
    }

    @Test
    fun `state round trips and derives progress, unit and default container`() {
        val state = WearState("2026-11-15", 1500, 2000, VolumeUnit.US_FL_OZ.name, listOf(WearContainer("Bottle", 500)), 42)

        val decoded = WearProtocol.decodeState(WearProtocol.encode(state))!!

        assertThat(decoded).isEqualTo(state)
        assertThat(decoded.date).isEqualTo(LocalDate.of(2026, 11, 15))
        assertThat(decoded.unit).isEqualTo(VolumeUnit.US_FL_OZ)
        assertThat(decoded.progress).isEqualTo(0.75f)
        assertThat(decoded.defaultContainer).isEqualTo(WearContainer("Bottle", 500))
        assertThat(decoded.plus(250).totalMl).isEqualTo(1750)
    }

    @Test
    fun `state edge cases are safe`() {
        val state = WearState("garbage", 3000, 0, "GALLONS")
        assertThat(state.date).isNull()
        assertThat(state.unit).isEqualTo(VolumeUnit.ML)
        assertThat(state.progress).isEqualTo(0f)
        assertThat(state.defaultContainer.volumeMl).isEqualTo(250)
        assertThat(WearState("2026-11-15", 5000, 2000).progress).isEqualTo(1f)
        assertThat(WearProtocol.decodeState("{}".toByteArray())).isNull()
    }
}
