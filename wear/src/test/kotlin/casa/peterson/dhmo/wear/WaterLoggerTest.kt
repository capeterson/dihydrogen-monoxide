package casa.peterson.dhmo.wear

import casa.peterson.dhmo.core.wear.LogRequest
import casa.peterson.dhmo.core.wear.WearState
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WaterLoggerTest {
    private class MemoryStore(var state: WearState?) : StateStore {
        override fun read() = state

        override fun write(state: WearState) {
            this.state = state
        }
    }

    private class FakeLink(var reachable: Boolean = true, var throws: Boolean = false) : PhoneLink {
        val sent = mutableListOf<LogRequest>()
        val queued = mutableListOf<LogRequest>()

        override suspend fun send(request: LogRequest): Boolean {
            if (throws) throw IOException("no Play services")
            if (reachable) sent += request
            return reachable
        }

        override suspend fun queue(request: LogRequest) {
            queued += request
        }

        override suspend fun requestState() = Unit
    }

    private val now = Instant.parse("2026-11-15T07:05:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val store = MemoryStore(WearState("2026-11-15", 1000, 2000))
    private var refreshes = 0

    private fun logger(link: PhoneLink) = WaterLogger(store, link, { refreshes++ }, clock) { ZoneId.of("Europe/Berlin") }

    @Test
    fun `connected phone gets a message with the watch's time and offset`() = runTest {
        val link = FakeLink()

        val request = logger(link).log(250)

        assertThat(link.sent).containsExactly(request)
        assertThat(link.queued).isEmpty()
        assertThat(request.instant).isEqualTo(now)
        assertThat(request.offset).isEqualTo(ZoneOffset.ofHours(1))
        assertThat(store.state!!.totalMl).isEqualTo(1250) // shown at once, before the phone confirms
        assertThat(refreshes).isEqualTo(1)
    }

    @Test
    fun `unreachable phone queues the same request`() = runTest {
        val link = FakeLink(reachable = false)

        val request = logger(link).log(500)

        assertThat(link.queued).containsExactly(request)
        assertThat(store.state!!.totalMl).isEqualTo(1500)
    }

    @Test
    fun `a failing send also falls back to the queue`() = runTest {
        val link = FakeLink(throws = true)
        val request = logger(link).log(300)
        assertThat(link.queued).containsExactly(request)
    }

    @Test
    fun `each drink gets its own id`() = runTest {
        val link = FakeLink()
        val a = logger(link).log(250)
        val b = logger(link).log(250)
        assertThat(a.id).isNotEqualTo(b.id)
    }
}
