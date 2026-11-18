package casa.peterson.dhmo.sync.healthconnect.engine

import androidx.health.connect.client.records.ExerciseSessionRecord as Session
import casa.peterson.dhmo.core.model.Intensity

/** How hard an exercise type is, for the extra water it adds (§10.5). Everything unlisted is moderate. */
internal fun intensityOf(exerciseType: Int): Intensity = when (exerciseType) {
    Session.EXERCISE_TYPE_WALKING,
    Session.EXERCISE_TYPE_YOGA,
    Session.EXERCISE_TYPE_PILATES,
    Session.EXERCISE_TYPE_STRETCHING,
    Session.EXERCISE_TYPE_GOLF,
    -> Intensity.LIGHT

    Session.EXERCISE_TYPE_RUNNING,
    Session.EXERCISE_TYPE_RUNNING_TREADMILL,
    Session.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
    Session.EXERCISE_TYPE_BOOT_CAMP,
    Session.EXERCISE_TYPE_ROWING,
    Session.EXERCISE_TYPE_ROWING_MACHINE,
    Session.EXERCISE_TYPE_SWIMMING_POOL,
    Session.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
    Session.EXERCISE_TYPE_FOOTBALL_AMERICAN,
    Session.EXERCISE_TYPE_FOOTBALL_AUSTRALIAN,
    Session.EXERCISE_TYPE_SOCCER,
    Session.EXERCISE_TYPE_BASKETBALL,
    Session.EXERCISE_TYPE_SQUASH,
    Session.EXERCISE_TYPE_BOXING,
    Session.EXERCISE_TYPE_MARTIAL_ARTS,
    Session.EXERCISE_TYPE_STAIR_CLIMBING,
    Session.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE,
    -> Intensity.VIGOROUS

    else -> Intensity.MODERATE
}
