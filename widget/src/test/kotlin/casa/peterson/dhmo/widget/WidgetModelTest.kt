package casa.peterson.dhmo.widget

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.data.usecase.today.TodaySnapshot
import casa.peterson.dhmo.core.domain.pace.Pace
import casa.peterson.dhmo.core.domain.pace.PaceStatus
import casa.peterson.dhmo.core.model.ClampFlag
import casa.peterson.dhmo.core.model.Container
import casa.peterson.dhmo.core.model.ContainerIcon
import casa.peterson.dhmo.core.model.DailyGoal
import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetModelTest {
    private val resources: Resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val day = LocalDate.of(2026, 6, 10)

    private fun goal(ml: Int) = DailyGoal(
        date = day,
        targetMl = ml,
        calculatedMl = ml,
        mode = GoalMode.CALCULATED,
        breakdown = GoalBreakdown(1, 0, emptyList(), 0, 0, 0, ClampFlag.NONE),
        dayStartMinutes = 0,
        policyVersion = 1,
        updatedAt = Instant.EPOCH,
    )

    private fun container(name: String, ml: Int, order: Int, icon: ContainerIcon = ContainerIcon.GLASS) =
        Container(UUID.nameUUIDFromBytes(name.toByteArray()), name, ml, icon, order, Instant.EPOCH)

    private val five = listOf(
        container("Bottle", 500, 2, ContainerIcon.BOTTLE),
        container("Glass", 250, 0),
        container("Flask", 750, 4, ContainerIcon.FLASK),
        container("Mug", 350, 1, ContainerIcon.MUG),
        container("Cup", 150, 3, ContainerIcon.CUP),
    )

    private fun snapshot(total: Int, goalMl: Int? = 2500, pace: Pace? = null, lastDrink: Instant? = Instant.parse("2026-06-10T14:15:00Z")) =
        TodaySnapshot(day, listOfNotNull(lastDrink?.let { TestData.entry(it) }), total, goalMl?.let(::goal), pace)

    private fun build(
        snapshot: TodaySnapshot,
        containers: List<Container> = five,
        units: VolumeUnit = VolumeUnit.ML,
        onboarded: Boolean = true,
        night: Boolean = false,
        undo: PendingUndo? = null,
    ) = buildWidgetModel(snapshot, containers, units, Locale.US, onboarded, night, resources, ZoneOffset.UTC, is24Hour = false, undo = undo)

    @Test
    fun `the total is written as the Today screen writes it`() {
        val model = build(snapshot(1250))
        assertThat(model.amountNumber).isEqualTo("1,250")
        assertThat(model.goalSuffix).isEqualTo("/ 2,500 ml")
        assertThat(model.goalLine).isEqualTo("of 2,500 ml")
        assertThat(model.amountValue).isEqualTo("1,250")
        assertThat(model.unitSymbol).isEqualTo("ml")
        assertThat(model.fraction).isEqualTo(0.5f)
        assertThat(model.progressDescription).isEqualTo("1,250 ml of 2,500 ml, 50 percent")
        assertThat(model.lastDrinkText).isEqualTo("Last drink 2:15 PM")
    }

    @Test
    fun `pace uses the Today screen's words, with a short form for the strips`() {
        val behind = build(snapshot(1250, pace = Pace(1500, 250, PaceStatus.BEHIND)))
        assertThat(behind.statusText).isEqualTo("250 ml behind pace")
        assertThat(behind.statusShortText).isEqualTo("250 ml behind")
        assertThat(behind.statusTone).isEqualTo(StatusTone.WATER)
        assertThat(behind.raining).isTrue()

        val onTrack = build(snapshot(1500, pace = Pace(1500, 0, PaceStatus.ON_TRACK)))
        assertThat(onTrack.statusText).isEqualTo("On track")
        assertThat(onTrack.raining).isFalse()

        val ahead = build(snapshot(1750, pace = Pace(1500, -250, PaceStatus.AHEAD)))
        assertThat(ahead.statusText).isEqualTo("Ahead of pace")
        assertThat(ahead.statusShortText).isEqualTo("Ahead")
    }

    @Test
    fun `outside the wake window there is no pace and no rain`() {
        val model = build(snapshot(1250, pace = null))
        assertThat(model.statusText).isNull()
        assertThat(model.statusShortText).isNull()
        assertThat(model.raining).isFalse()
    }

    @Test
    fun `a met goal fills the jar, lights the lamp and stops the rain`() {
        val model = build(snapshot(2600, pace = Pace(2500, -100, PaceStatus.AHEAD)))
        assertThat(model.goalReached).isTrue()
        assertThat(model.fraction).isEqualTo(1f)
        assertThat(model.statusText).isEqualTo("Goal reached")
        assertThat(model.statusTone).isEqualTo(StatusTone.LAMPLIGHT)
        assertThat(model.raining).isFalse()
        assertThat(model.progressDescription).isEqualTo("2,600 ml of 2,500 ml, goal reached")
    }

    @Test
    fun `just short of the goal is never read out as 100 percent`() {
        assertThat(build(snapshot(2490)).progressDescription).endsWith("99 percent")
        assertThat(build(snapshot(2490)).goalReached).isFalse()
    }

    @Test
    fun `without a goal there is only the amount, and the jar stays empty as on the Today screen`() {
        val model = build(snapshot(750, goalMl = null, pace = Pace(0, 0, PaceStatus.BEHIND)))
        assertThat(model.amountNumber).isEqualTo("750 ml")
        assertThat(model.goalSuffix).isNull()
        assertThat(model.goalLine).isNull()
        assertThat(model.fraction).isEqualTo(0f)
        assertThat(model.statusText).isNull()
        assertThat(model.raining).isFalse()
        assertThat(model.progressDescription).isEqualTo("750 ml so far today, no goal set yet")
    }

    @Test
    fun `before the first drink there is no last-drink line`() {
        assertThat(build(snapshot(0, lastDrink = null)).lastDrinkText).isNull()
    }

    @Test
    fun `coasters are the three lowest sort orders, in order`() {
        val buttons = build(snapshot(1250)).buttons
        assertThat(buttons.map { it.name }).containsExactly("Glass", "Mug", "Bottle").inOrder()
        assertThat(buttons.first().amountText).isEqualTo("250 ml")
        assertThat(buttons.first().contentDescription).isEqualTo("Add Glass, 250 ml")
        assertThat(buttons.first().icon).isEqualTo(ContainerIcon.GLASS)
    }

    @Test
    fun `one container gives one coaster`() {
        val buttons = build(snapshot(1250), containers = five.filter { it.name == "Mug" }).buttons
        assertThat(buttons.map { it.name }).containsExactly("Mug")
    }

    @Test
    fun `with no containers the single coaster opens the app`() {
        val buttons = build(snapshot(1250), containers = emptyList()).buttons
        assertThat(buttons).hasSize(1)
        assertThat(buttons.single().containerId).isNull()
        assertThat(buttons.single().name).isEqualTo("Add")
        assertThat(buttons.single().amountText).isNull()
        assertThat(buttons.single().contentDescription).isEqualTo("Open the app to add a drink container")
    }

    @Test
    fun `fluid ounces split into number and unit for narrow coasters`() {
        val model = build(snapshot(1250), units = VolumeUnit.US_FL_OZ)
        assertThat(model.amountNumber).isEqualTo("42.3")
        assertThat(model.goalSuffix).isEqualTo("/ 84.5 fl oz")
        val glass = model.buttons.first()
        assertThat(glass.amountText).isEqualTo("8.5 fl oz")
        assertThat(glass.amountValue).isEqualTo("8.5")
        assertThat(glass.unitSymbol).isEqualTo("fl oz")
    }

    @Test
    fun `a pending undo offers to take back the drink`() {
        val id = UUID.randomUUID()
        val undo = build(snapshot(1250), undo = PendingUndo(id, 250)).undo
        assertThat(undo?.entryId).isEqualTo(id)
        assertThat(undo?.text).isEqualTo("Added 250 ml")
        assertThat(undo?.description).isEqualTo("Undo adding 250 ml")
        assertThat(build(snapshot(1250)).undo).isNull()
    }

    @Test
    fun `setup and theme are carried through`() {
        assertThat(build(snapshot(0), onboarded = false).needsSetup).isTrue()
        assertThat(build(snapshot(0)).needsSetup).isFalse()
        assertThat(build(snapshot(0), night = true).night).isTrue()
    }
}
