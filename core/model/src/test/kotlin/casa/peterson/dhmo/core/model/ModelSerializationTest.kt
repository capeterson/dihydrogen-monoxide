package casa.peterson.dhmo.core.model

import casa.peterson.dhmo.core.model.serialization.InstantIsoSerializer
import casa.peterson.dhmo.core.model.serialization.LocalDateIsoSerializer
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.DeviceState
import casa.peterson.dhmo.core.model.settings.ManualCity
import casa.peterson.dhmo.core.model.settings.UpdateSettings
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.model.settings.WeatherMode
import casa.peterson.dhmo.core.model.settings.WeatherSettings
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class ModelSerializationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Serializable
    private data class Holder(
        @Serializable(with = InstantIsoSerializer::class) val at: Instant,
        @Serializable(with = LocalDateIsoSerializer::class) val day: LocalDate,
    )

    @Test
    fun `iso serializers write instants and dates as strings`() {
        val holder = Holder(Instant.parse("2026-11-15T09:30:00.123Z"), LocalDate.of(2026, 11, 15))
        val text = json.encodeToString(holder)
        assertThat(text).isEqualTo("""{"at":"2026-11-15T09:30:00.123Z","day":"2026-11-15"}""")
        assertThat(json.decodeFromString<Holder>(text)).isEqualTo(holder)
    }

    @Test
    fun `empty object decodes to default settings`() {
        val settings = json.decodeFromString<AppSettings>("{}")
        assertThat(settings).isEqualTo(AppSettings())
        assertThat(settings.units).isEqualTo(VolumeUnit.ML)
        assertThat(settings.reminders.wakeMinutes).isEqualTo(420)
    }

    @Test
    fun `unknown keys are ignored and settings round trip`() {
        val original =
            AppSettings(
                units = VolumeUnit.US_FL_OZ,
                dayStartMinutes = 240,
                weather = WeatherSettings(WeatherMode.MANUAL_CITY, ManualCity("Berlin", "State of Berlin", "DE", 525, 134)),
            )
        val text = json.encodeToString(original).replace("{\"schemaVersion\"", "{\"future\":1,\"schemaVersion\"")
        assertThat(json.decodeFromString<AppSettings>(text)).isEqualTo(original)
    }

    @Test
    fun `device state defaults`() {
        val state = json.decodeFromString<DeviceState>("{}")
        assertThat(state.onboarding.completed).isFalse()
        assertThat(state.cachedLocation).isNull()
    }

    @Test
    fun `legacy device state keeps update checks off and update settings survive restart`() {
        val old = json.decodeFromString<DeviceState>("""{"onboarding":{"completed":true}}""")
        assertThat(old.onboarding.completed).isTrue()
        assertThat(old.updates.enabled).isFalse()
        val state = old.copy(updates = UpdateSettings(enabled = true, dismissedTag = "debug-12"))
        assertThat(json.decodeFromString<DeviceState>(json.encodeToString(state))).isEqualTo(state)
    }

    @Test
    fun `imports are editable only when adopted`() {
        val entry =
            IntakeEntry(
                id = java.util.UUID.randomUUID(),
                instant = Instant.EPOCH,
                zoneOffset = java.time.ZoneOffset.UTC,
                hydrationDay = LocalDate.EPOCH,
                volumeMl = 250,
                source = Source.HEALTH_CONNECT_IMPORT,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
        assertThat(entry.isEditable).isFalse()
        assertThat(entry.copy(hcAdopted = true).isEditable).isTrue()
        assertThat(entry.copy(source = Source.MANUAL).isEditable).isTrue()
    }
}
