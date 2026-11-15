package casa.peterson.dhmo.core.testing

import casa.peterson.dhmo.core.common.ZoneProvider
import java.time.ZoneId

/** A [ZoneProvider] whose zone tests can change, for example to simulate travel. */
class FixedZoneProvider(var zone: ZoneId = ZoneId.of("Europe/Berlin")) : ZoneProvider {
    override fun current(): ZoneId = zone
}
