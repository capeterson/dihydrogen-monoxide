package casa.peterson.dhmo.feature.profile

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.domain.body.BodyInputs
import casa.peterson.dhmo.core.model.ActivityAdjustment
import casa.peterson.dhmo.core.model.ActivitySource
import casa.peterson.dhmo.core.model.ClampFlag
import casa.peterson.dhmo.core.model.ComponentKind
import casa.peterson.dhmo.core.model.DayOverride
import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.GoalComponent
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.Intensity
import casa.peterson.dhmo.core.model.ResolvedGoal
import casa.peterson.dhmo.core.model.settings.AdjustmentToggles
import casa.peterson.dhmo.core.model.settings.GlobalOverride
import casa.peterson.dhmo.core.model.settings.GoalSettings
import casa.peterson.dhmo.core.model.settings.PinnedInputs
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.model.settings.WeatherMode
import casa.peterson.dhmo.core.testing.TestData
import casa.peterson.dhmo.core.testing.captureScreenVariants
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

private val DAY = LocalDate.of(2026, 6, 10)

private val calculatedBreakdown = GoalBreakdown(
    policyVersion = 1,
    ageYears = 36,
    components = listOf(
        GoalComponent(ComponentKind.BASE_WEIGHT, 2145, true, mapOf("weightKg" to "65", "factor" to "33")),
        GoalComponent(ComponentKind.ACTIVITY_MINUTES, 360, true, mapOf("minutes" to "30", "capped" to "false")),
        GoalComponent(ComponentKind.CLIMATE_FORECAST, 500, true, mapOf("maxApparentTempC" to "32")),
    ),
    rawMl = 3005,
    clampedMl = 3005,
    calculatedMl = 3000,
    clamp = ClampFlag.NONE,
)

private fun calculatedState() = ProfileGoalUiState.Ready(
    day = DAY,
    profile = TestData.adultProfile,
    breakdown = calculatedBreakdown,
    effective = ResolvedGoal(3000, 3000, GoalMode.CALCULATED),
    globalOverride = GlobalOverride(),
    todayOverride = null,
    toggles = AdjustmentToggles(),
    pinned = PinnedInputs(),
    weather = null,
    weatherMode = WeatherMode.MANUAL_CITY,
    forecastUnavailable = false,
    planned = listOf(
        ActivityAdjustment(ActivitySource.MANUAL_PLANNED, "manual-1", DAY, 30, Intensity.MODERATE, 360, null, Instant.EPOCH),
    ),
    units = VolumeUnit.ML,
)

private fun overrideState() = calculatedState().copy(
    breakdown = GoalBreakdown(
        policyVersion = 1,
        ageYears = 22,
        components = listOf(
            GoalComponent(ComponentKind.BASE_WEIGHT, 5600, true, mapOf("weightKg" to "160", "factor" to "35")),
            GoalComponent(ComponentKind.ACTIVITY_LEVEL, 750, false, mapOf("level" to "VERY_ACTIVE")),
            GoalComponent(ComponentKind.CLIMATE_MANUAL, 250, true, mapOf("climate" to "WARM", "fallback" to "true")),
            GoalComponent(ComponentKind.ALTITUDE, 250, true),
        ),
        rawMl = 6100,
        clampedMl = 6000,
        calculatedMl = 6000,
        clamp = ClampFlag.HIGH,
    ),
    effective = ResolvedGoal(4000, 6000, GoalMode.DAY_OVERRIDE),
    globalOverride = GlobalOverride(enabled = true, targetMl = 2500),
    todayOverride = DayOverride(DAY, 4000, "Long hike", Instant.EPOCH),
    toggles = AdjustmentToggles(activity = false),
    forecastUnavailable = true,
    planned = emptyList(),
    units = VolumeUnit.US_FL_OZ,
)

private fun noProfileState() = calculatedState().copy(
    profile = null,
    breakdown = null,
    effective = ResolvedGoal(1800, null, GoalMode.GLOBAL_OVERRIDE),
    globalOverride = GlobalOverride(enabled = true, targetMl = 1800),
    planned = emptyList(),
)

private fun captureGoal(name: String, state: ProfileGoalUiState.Ready) =
    captureScreenVariants("ProfileGoal_$name") { ProfileGoalContent(state, ProfileGoalActions()) }

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileGoalCalculatedScreenshotTest {
    @Test fun captureScreen() = captureGoal("calculated", calculatedState())
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileGoalOverrideScreenshotTest {
    @Test fun captureScreen() = captureGoal("override", overrideState())
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileGoalNoProfileScreenshotTest {
    @Test fun captureScreen() = captureGoal("noprofile", noProfileState())
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditProfileScreenshotTest {
    @Test fun captureScreen() = captureScreenVariants("EditProfile") {
        EditProfileContent(
            EditProfileUiState(
                loading = false,
                birthDate = "1990-05-17",
                body = BodyInputs(weight = "65", heightCm = "168"),
                invalid = true,
            ),
            onChange = {},
            onSave = {},
        )
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlobalOverrideScreenshotTest {
    @Test fun captureScreen() = captureScreenVariants("GlobalOverride") {
        val units = VolumeUnit.ML
        GlobalOverrideContent(
            GlobalOverrideUiState(
                loading = false,
                enabled = true,
                target = VolumeField.of(2500, units),
                perWeekday = DayOfWeek.entries.associateWith { VolumeField.of(if (it == DayOfWeek.SATURDAY) 3000 else null, units) },
                units = units,
            ),
            onEnabledChange = {},
            onTargetChange = {},
            onWeekdayChange = { _, _ -> },
            onSave = {},
        )
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DayOverrideScreenshotTest {
    @Test fun captureScreen() = captureScreenVariants("DayOverride") {
        DayOverrideContent(
            DayOverrideUiState(
                loading = false,
                day = DAY,
                withoutOverrideMl = 2300,
                hasOverride = true,
                target = VolumeField.of(4000, VolumeUnit.ML),
                reason = "Long hike",
            ),
            onTargetChange = {},
            onReasonChange = {},
            onSave = {},
            onClear = {},
        )
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdjustmentsScreenshotTest {
    @Test fun captureScreen() = captureScreenVariants("Adjustments") {
        AdjustmentsContent(
            GoalSettings(adjustments = AdjustmentToggles(climate = false), pinned = PinnedInputs(pinWeight = true)),
            onToggles = {},
            onPinned = {},
        )
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SourcesScreenshotTest {
    @Test fun captureScreen() = captureScreenVariants("Sources") { SourcesContent() }
}
