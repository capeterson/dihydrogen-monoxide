package casa.peterson.dhmo.convention

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Marker service that caps how many `Test` tasks run at the same time. Each unit-test JVM loads the Android framework
 * for Robolectric and can use around a gigabyte, so running one per module in parallel is what exhausts small machines.
 */
abstract class TestConcurrencyService : BuildService<BuildServiceParameters.None>
