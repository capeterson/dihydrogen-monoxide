package casa.peterson.dhmo.core.testing

import casa.peterson.dhmo.core.model.ActivityLevel
import casa.peterson.dhmo.core.model.Climate
import casa.peterson.dhmo.core.model.Container
import casa.peterson.dhmo.core.model.ContainerIcon
import casa.peterson.dhmo.core.model.IntakeEntry
import casa.peterson.dhmo.core.model.PregnancyStatus
import casa.peterson.dhmo.core.model.Profile
import casa.peterson.dhmo.core.model.ProfileSnapshot
import casa.peterson.dhmo.core.model.ProfileSource
import casa.peterson.dhmo.core.model.Sex
import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

/** Sample domain objects and the golden datasets of TECHNICAL_PLAN §9.14 as Kotlin constants. */
@Suppress("MagicNumber")
object TestData {
    val GLASS_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000001")
    val BOTTLE_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000002")
    val LARGE_BOTTLE_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000003")

    /** The seeded containers of §4.3 for [units]. */
    fun defaultContainers(units: VolumeUnit, updatedAt: Instant = Instant.EPOCH): List<Container> {
        val ounces = units == VolumeUnit.US_FL_OZ || units == VolumeUnit.IMP_FL_OZ
        val volumes = if (ounces) listOf(237, 473, 710) else listOf(250, 500, 750)
        return listOf(
            Container(GLASS_ID, "Glass", volumes[0], ContainerIcon.GLASS, 0, updatedAt),
            Container(BOTTLE_ID, "Bottle", volumes[1], ContainerIcon.BOTTLE, 1, updatedAt),
            Container(LARGE_BOTTLE_ID, "Large bottle", volumes[2], ContainerIcon.LARGE_BOTTLE, 2, updatedAt),
        )
    }

    val adultProfile: Profile = Profile(
        birthDate = LocalDate.of(1990, 5, 17),
        sex = Sex.FEMALE,
        weightKg = 65.0,
        heightCm = 168.0,
        activityLevel = ActivityLevel.MODERATE,
        climate = Climate.TEMPERATE,
        pregnancyStatus = PregnancyStatus.NONE,
        lactating = false,
        highAltitude = false,
    )

    fun snapshot(
        effectiveFrom: LocalDate,
        profile: Profile = adultProfile,
        source: ProfileSource = ProfileSource.MANUAL,
        createdAt: Instant = effectiveFrom.atStartOfDay().toInstant(ZoneOffset.UTC),
    ): ProfileSnapshot = ProfileSnapshot(UUID.randomUUID(), effectiveFrom, profile, source, createdAt)

    /** A live entry at [instant]; the hydration day is the local date at [offset] for a midnight day start. */
    fun entry(
        instant: Instant,
        volumeMl: Int = 250,
        offset: ZoneOffset = ZoneOffset.UTC,
        source: Source = Source.MANUAL,
        id: UUID = UUID.randomUUID(),
    ): IntakeEntry = IntakeEntry(
        id = id,
        instant = instant,
        zoneOffset = offset,
        hydrationDay = instant.atOffset(offset).toLocalDate(),
        volumeMl = volumeMl,
        source = source,
        createdAt = instant,
        updatedAt = instant,
    )

    /** A Health Connect import that has not been adopted. */
    fun importedEntry(instant: Instant, volumeMl: Int = 300, origin: String = "com.example.water"): IntakeEntry =
        entry(instant, volumeMl, source = Source.HEALTH_CONNECT_IMPORT)
            .copy(hcRecordId = "hc-${UUID.randomUUID()}", hcDataOrigin = origin)

    // ---- §9.14 datasets -------------------------------------------------------------------------------------

    /** Dataset A: Monday 2026-03-02 to Sunday 2026-03-15; goal 2,000 ml every day; 0 is the no-log day (Wed 04). */
    val datasetAStart: LocalDate = LocalDate.of(2026, 3, 2)
    val datasetATotals: List<Int> = listOf(2100, 1800, 0, 2500, 1950, 2000, 1200, 2200, 2300, 1600, 2000, 2050, 900, 2400)
    const val DATASET_A_GOAL_ML: Int = 2000

    val datasetB: List<Int> = listOf(1500, 1600, 1550, 1700, 1800, 1750, 1900, 2000, 1950, 2100)
    val datasetC: List<Int> = listOf(2000, 2000, 1500, 2500, 2000, 1500, 2500, 2000, 2000, 2000)
    val datasetD: List<Int> = listOf(2400, 2300, 2350, 2200, 2100, 2150, 2000, 1900, 1950, 1800, 1700, 1750)
    val datasetE: List<Int> = listOf(1500, 1600, 1550, 1700, 5000, 1750, 1900, 2000, 1950, 2100)
    val datasetF: List<Int> = listOf(2000, 2100, 1900, 2050, 1950, 2000, 2200, 1800, 5000, 2000, 500, 2050)

    /** Dataset G: (max apparent temperature in degrees C, total ml). */
    val datasetG: List<Pair<Int, Int>> = listOf(
        18 to 1800, 22 to 2000, 25 to 2100, 30 to 2600, 28 to 2300, 15 to 1700, 33 to 2900,
        21 to 2000, 27 to 2500, 24 to 2100, 19 to 1900, 31 to 2700, 35 to 2600, 26 to 2200,
    )

    /** Dataset T: three days of `(clock time, ml)` entries; goal 2,000 ml; day start 00:00. */
    val datasetT: List<List<Pair<LocalTime, Int>>> = listOf(
        listOf(LocalTime.of(8, 0) to 500, LocalTime.of(12, 30) to 500, LocalTime.of(19, 0) to 600, LocalTime.of(21, 15) to 400),
        listOf(LocalTime.of(7, 30) to 250, LocalTime.of(10, 0) to 750, LocalTime.of(15, 0) to 500, LocalTime.of(18, 0) to 500),
        listOf(LocalTime.of(9, 0) to 300, LocalTime.of(13, 0) to 300, LocalTime.of(20, 0) to 900),
    )

    /** One row of the dataset H bucketing table. */
    data class BucketingCase(
        val name: String,
        val instant: Instant,
        val offset: ZoneOffset,
        val dayStart: LocalTime,
        val expectedDay: LocalDate,
    )

    private fun case(name: String, utc: String, offset: String, dayStart: LocalTime, day: String) =
        BucketingCase(name, Instant.parse(utc), ZoneOffset.of(offset), dayStart, LocalDate.parse(day))

    /** Dataset H1 to H12. */
    val datasetH: List<BucketingCase> = listOf(
        case("H1", "2026-10-24T21:30:00Z", "+02:00", LocalTime.MIDNIGHT, "2026-10-24"),
        case("H2", "2026-10-24T22:30:00Z", "+02:00", LocalTime.MIDNIGHT, "2026-10-25"),
        case("H3", "2026-10-25T00:30:00Z", "+02:00", LocalTime.MIDNIGHT, "2026-10-25"),
        case("H4", "2026-10-25T01:30:00Z", "+01:00", LocalTime.MIDNIGHT, "2026-10-25"),
        case("H5", "2026-10-25T22:59:00Z", "+01:00", LocalTime.MIDNIGHT, "2026-10-25"),
        case("H6", "2026-10-25T23:00:00Z", "+01:00", LocalTime.MIDNIGHT, "2026-10-26"),
        case("H7", "2026-03-29T00:59:00Z", "+01:00", LocalTime.MIDNIGHT, "2026-03-29"),
        case("H8", "2026-03-29T01:00:00Z", "+02:00", LocalTime.MIDNIGHT, "2026-03-29"),
        case("H9", "2026-06-11T01:30:00Z", "+02:00", LocalTime.of(4, 0), "2026-06-10"),
        case("H10", "2026-06-11T02:00:00Z", "+02:00", LocalTime.of(4, 0), "2026-06-11"),
        case("H11", "2026-06-10T03:30:00Z", "-04:00", LocalTime.MIDNIGHT, "2026-06-09"),
        case("H12", "2026-06-10T03:30:00Z", "+02:00", LocalTime.MIDNIGHT, "2026-06-10"),
    )

    /** The day-start change table of §9.14: entries a to d, all at +02:00 and 250 ml. */
    val dayStartChangeInstants: Map<String, Instant> = mapOf(
        "a" to Instant.parse("2026-06-09T23:00:00Z"),
        "b" to Instant.parse("2026-06-10T20:00:00Z"),
        "c" to Instant.parse("2026-06-10T22:30:00Z"),
        "d" to Instant.parse("2026-06-11T03:00:00Z"),
    )
}
