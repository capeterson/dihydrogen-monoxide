package casa.peterson.dhmo.core.common

import java.time.ZoneId

/** The only way production code reads the current time zone, so tests can pin it. */
fun interface ZoneProvider {
    fun current(): ZoneId
}
