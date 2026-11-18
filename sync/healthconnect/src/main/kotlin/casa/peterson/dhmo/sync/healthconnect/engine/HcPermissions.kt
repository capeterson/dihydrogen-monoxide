package casa.peterson.dhmo.sync.healthconnect.engine

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import casa.peterson.dhmo.core.model.settings.HealthConnectSettings
import casa.peterson.dhmo.core.model.sync.HcSetting

/** Which permission each switch needs (TECHNICAL_PLAN §10.1), and which switches count right now. */
internal object HcPermissions {
    fun forSetting(setting: HcSetting): Set<String> = when (setting) {
        HcSetting.WRITE_HYDRATION -> setOf(HealthPermission.getWritePermission(HydrationRecord::class))

        HcSetting.IMPORT_HYDRATION -> setOf(HealthPermission.getReadPermission(HydrationRecord::class))

        HcSetting.SYNC_WEIGHT -> setOf(HealthPermission.getReadPermission(WeightRecord::class))

        HcSetting.USE_EXERCISE -> setOf(HealthPermission.getReadPermission(ExerciseSessionRecord::class))

        HcSetting.READ_STEPS_AND_CALORIES -> setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        )

        HcSetting.BACKGROUND_SYNC -> setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)

        HcSetting.IMPORT_HISTORY -> setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
    }

    fun isOn(settings: HealthConnectSettings, setting: HcSetting): Boolean = when (setting) {
        HcSetting.WRITE_HYDRATION -> settings.writeHydration
        HcSetting.IMPORT_HYDRATION -> settings.importHydration
        HcSetting.SYNC_WEIGHT -> settings.syncWeight
        HcSetting.USE_EXERCISE -> settings.useExercise
        HcSetting.READ_STEPS_AND_CALORIES -> settings.readStepsAndCalories
        HcSetting.BACKGROUND_SYNC -> settings.backgroundSync
        HcSetting.IMPORT_HISTORY -> settings.importHistory
    }

    fun anyOn(settings: HealthConnectSettings): Boolean = HcSetting.entries.any { isOn(settings, it) }

    /** Permissions of switches that are on but not granted (the user revoked them in system settings). */
    fun missing(settings: HealthConnectSettings, granted: Set<String>): Set<String> = HcSetting.entries
        .filter { isOn(settings, it) }
        .flatMap { forSetting(it) }
        .filterNot { it in granted }
        .toSet()

    fun effective(settings: HealthConnectSettings, granted: Set<String>): Effective {
        fun ok(setting: HcSetting) = isOn(settings, setting) && granted.containsAll(forSetting(setting))
        return Effective(
            write = ok(HcSetting.WRITE_HYDRATION),
            importHydration = ok(HcSetting.IMPORT_HYDRATION),
            weight = ok(HcSetting.SYNC_WEIGHT),
            exercise = ok(HcSetting.USE_EXERCISE),
            stepsAndCalories = ok(HcSetting.READ_STEPS_AND_CALORIES),
            background = ok(HcSetting.BACKGROUND_SYNC),
            history = ok(HcSetting.IMPORT_HISTORY),
        )
    }

    /** A setting is effective only when it is on and its permission is currently granted. */
    data class Effective(
        val write: Boolean,
        val importHydration: Boolean,
        val weight: Boolean,
        val exercise: Boolean,
        val stepsAndCalories: Boolean,
        val background: Boolean,
        val history: Boolean,
    ) {
        val reads: Boolean get() = importHydration || weight || exercise
        val any: Boolean get() = write || reads || stepsAndCalories
    }
}
