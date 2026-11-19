@file:Suppress("MagicNumber")

package casa.peterson.dhmo.core.designsystem.component

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.PI
import kotlin.math.sin

/** Tracks Android's Remove animations setting, including changes while the screen is visible. */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun readSetting() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(readSetting()) }
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    reduced = readSetting()
                }
            }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        reduced = readSetting()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

/** The user's app-level preference, provided by the phone app. */
val LocalCozyAnimationsEnabled = staticCompositionLocalOf { true }

/** Stops decorative frame clocks when a screen leaves the foreground. */
@Composable
fun rememberCozyMotionEnabled(animated: Boolean): Boolean {
    if (!animated || !LocalCozyAnimationsEnabled.current || rememberReducedMotion()) return false
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return state.isAtLeast(Lifecycle.State.RESUMED)
}

/** A note slips onto the desk, pivots on its upper corner and settles in place once per visit. */
@Composable
fun Modifier.gentleNoteArrival(animated: Boolean = true): Modifier {
    val enabled = rememberCozyMotionEnabled(animated)
    var arrived by remember { mutableStateOf(false) }
    val phase = remember { Animatable(0f) }
    LaunchedEffect(enabled) {
        if (enabled && !arrived) {
            arrived = true
            phase.animateTo(1f, tween(660, easing = LinearEasing))
        } else if (arrived) {
            phase.snapTo(1f)
        }
    }
    if (!enabled) return this
    return graphicsLayer {
        val t = phase.value
        val slide = 1f - smoothStep(interval(t, 0f, 0.83f))
        translationY = -9.dp.toPx() * slide
        rotationZ = -1.8f * slide + 0.38f * pulse(t, 0.56f, 0.78f, 1f)
        transformOrigin = TransformOrigin(0.22f, 0.10f)
        alpha = 0.74f + 0.26f * smoothStep(interval(t, 0f, 0.48f))
    }
}

/** Read these clocks during drawing; frame ticks should invalidate a canvas, not its whole screen. */
@Composable
internal fun rememberAmbientTime(enabled: Boolean): State<Float> {
    if (!enabled) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = "ambient").animateFloat(
        initialValue = 0f,
        targetValue = 3_600f,
        animationSpec = infiniteRepeatable(tween(3_600_000, easing = LinearEasing)),
        label = "time",
    )
}

/** A restored event is the baseline. Enabling motion is also a new baseline, never a queued performance. */
@Composable
internal fun rememberEventPhase(event: Long, duration: Int, enabled: Boolean): State<Float> {
    if (!enabled) return remember { mutableFloatStateOf(1f) }
    var previous by remember { mutableLongStateOf(event) }
    val phase = remember { Animatable(1f) }
    LaunchedEffect(event) {
        val shouldPlay = event != 0L && event != previous
        previous = event
        if (!shouldPlay) {
            phase.snapTo(1f)
        } else {
            phase.snapTo(0f)
            phase.animateTo(1f, tween(duration, easing = LinearEasing))
        }
    }
    return phase.asState()
}

internal fun interval(value: Float, start: Float, end: Float): Float = ((value - start) / (end - start)).coerceIn(0f, 1f)

internal fun smoothStep(t: Float): Float = t * t * (3f - 2f * t)

internal fun pulse(t: Float, start: Float, peak: Float, end: Float): Float =
    smoothStep(interval(t, start, peak)) * (1f - smoothStep(interval(t, peak, end)))

internal const val SIP_DURATION = 1_160
internal const val GOAL_DURATION = 3_240
internal const val IMPACT = 0.32f

/** Poses are relative to planted feet. A positive stretch makes the body taller and narrower. */
internal data class SpiritPose(val height: Float, val stretch: Float, val lean: Float, val leafAngle: Float, val delight: Float)

private val RestingPose = SpiritPose(0f, 0f, 0f, 0f, 0f)

internal fun sipPose(t: Float, bigHop: Boolean): SpiritPose {
    if (t <= 0f || t >= 1f) return RestingPose
    val flight = interval(t, 0.35f, 0.65f)
    val hop = 4f * flight * (1f - flight)
    val rebound = interval(t, 0.73f, 0.84f)
    val height = (if (bigHop) 24f else 16f) * hop + 2.1f * 4f * rebound * (1f - rebound)
    val stretch =
        -0.17f * pulse(t, 0.16f, 0.295f, 0.36f) + 0.14f * pulse(t, 0.295f, 0.38f, 0.49f) +
            0.07f * pulse(t, 0.52f, 0.62f, 0.65f) - 0.2f * pulse(t, 0.63f, 0.69f, 0.76f) +
            0.045f * pulse(t, 0.71f, 0.78f, 0.83f) - 0.035f * pulse(t, 0.81f, 0.86f, 0.94f)
    // The soft leaf trails the launch, catches up in the air, then rocks past neutral after landing.
    val settling = interval(t, 0.64f, 1f)
    val leaf =
        11f * pulse(t, 0.18f, 0.32f, 0.41f) - 26f * pulse(t, 0.31f, 0.43f, 0.59f) +
            14f * pulse(t, 0.49f, 0.60f, 0.68f) - 16f * sin(settling * 3f * PI.toFloat()) * (1f - settling) * (1f - settling)
    return SpiritPose(height, stretch, -6f * hop + 3f * pulse(t, 0.63f, 0.71f, 0.86f), leaf, pulse(t, 0.30f, 0.48f, 0.92f))
}
