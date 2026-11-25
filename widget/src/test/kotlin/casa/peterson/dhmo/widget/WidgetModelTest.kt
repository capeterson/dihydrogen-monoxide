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

    private val three = listOf(
        container("Bottle", 500, 2, ContainerIcon.BOTTLE),
        container("Glass", 250, 0),
        container("Mug", 350, 1, ContainerIcon.MUG),
    )

    private fun snapshot(total: Int, goalMl: Int? = 2500, pace: Pace? = null, lastDrink: Instant? = Instant.parse("2026-06-10T14:15:00Z")) =
        TodaySnapshot(day, listOfNotNull(lastDrink?.let { TestData.entry(it) }), total, goalMl?.let(::goal), pace)

    private fun build(
        snapshot: TodaySnapshot,
        containers: List<Container> = three,
        units: VolumeUnit = VolumeUnit.L,
        onboarded: Boolean = true,
        locale: Locale = Locale.US,
    ) = buildWidgetModel(snapshot, containers, units, locale, onboarded, resources, ZoneOffset.UTC, is24Hour = false)

    @Test
    fun `partial progress behind pace shows the amount, goal, percentage and the deficit`() {
        val model = build(snapshot(1250, pace = Pace(1500, 250, PaceStatus.BEHIND)))
        assertThat(model.amountText).isEqualTo("1.25 L")
        assertThat(model.goalText).isEqualTo("2.5 L")
        assertThat(model.compactText).isEqualTo("1.25 / 2.5 L")
        assertThat(model.goalLine).isEqualTo("of 2.5 L")
        assertThat(model.percentText).isEqualTo("50%")
        assertThat(model.paceText).isEqualTo("0.25 L behind")
        assertThat(model.detailLine).isEqualTo("of 2.5 L · 50% · 0.25 L behind")
        assertThat(model.fraction).isEqualTo(0.5f)
        assertThat(model.goalReached).isFalse()
        assertThat(model.lastDrinkText).isEqualTo("last drink 2:15 PM")
        assertThat(model.progressDescription).isEqualTo("1.25 L of 2.5 L, 50 percent")
        assertThat(model.needsSetup).isFalse()
    }

    @Test
    fun `on track and ahead have their own words`() {
        assertThat(build(snapshot(1500, pace = Pace(1500, 0, PaceStatus.ON_TRACK))).paceText).isEqualTo("On track")
        assertThat(build(snapshot(1750, pace = Pace(1500, -250, PaceStatus.AHEAD))).paceText).isEqualTo("Ahead")
    }

    @Test
    fun `outside the wake window there is no pace text`() {
        val model = build(snapshot(1250, pace = null))
        assertThat(model.paceText).isNull()
        assertThat(model.detailLine).isEqualTo("of 2.5 L · 50%")
    }

    @Test
    fun `reaching the goal is flagged, the fraction is capped and the percentage is not`() {
        val model = build(snapshot(2600, pace = Pace(2500, -100, PaceStatus.AHEAD)))
        assertThat(model.goalReached).isTrue()
        assertThat(model.fraction).isEqualTo(1f)
        assertThat(model.percentText).isEqualTo("104%")
        assertThat(model.progressDescription).isEqualTo("2.6 L of 2.5 L, goal reached")
    }

    @Test
    fun `a nearly full day never says 100 percent before the goal is reached`() {
        val model = build(snapshot(2499))
        assertThat(model.goalReached).isFalse()
        assertThat(model.percentText).isEqualTo("99%")
    }

    @Test
    fun `exactly the goal counts as reached`() {
        val model = build(snapshot(2500))
        assertThat(model.goalReached).isTrue()
        assertThat(model.percentText).isEqualTo("100%")
    }

    @Test
    fun `without a goal only the amount is shown`() {
        val model = build(snapshot(750, goalMl = null))
        assertThat(model.amountText).isEqualTo("0.75 L")
        assertThat(model.compactText).isEqualTo("0.75 L")
        assertThat(model.goalText).isNull()
        assertThat(model.goalLine).isNull()
        assertThat(model.percentText).isNull()
        assertThat(model.paceText).isNull()
        assertThat(model.detailLine).isNull()
        assertThat(model.fraction).isEqualTo(0f)
        assertThat(model.goalReached).isFalse()
        assertThat(model.progressDescription).isEqualTo("0.75 L so far today, no goal set yet")
    }

    @Test
    fun `a nothing-yet day has no last drink`() {
        val model = build(snapshot(0, lastDrink = null))
        assertThat(model.lastDrinkText).isNull()
        assertThat(model.percentText).isEqualTo("0%")
    }

    @Test
    fun `the last drink is the latest entry, in the device zone, in 24 hour time when asked`() {
        val entries = listOf(TestData.entry(Instant.parse("2026-06-10T08:00:00Z")), TestData.entry(Instant.parse("2026-06-10T19:05:00Z")))
        val snapshot = TodaySnapshot(day, entries, 500, goal(2500), null)
        val model = buildWidgetModel(snapshot, three, VolumeUnit.L, Locale.US, true, resources, ZoneOffset.ofHours(2), is24Hour = true)
        assertThat(model.lastDrinkText).isEqualTo("last drink 21:05")
    }

    @Test
    fun `three containers give three buttons ordered by sort order with their icons and descriptions`() {
        val buttons = build(snapshot(1250), units = VolumeUnit.ML).buttons
        assertThat(buttons.map { it.label }).containsExactly("+250 ml", "+350 ml", "+500 ml").inOrder()
        assertThat(buttons.map { it.shortLabel }).containsExactly("+250", "+350", "+500").inOrder()
        assertThat(buttons.map { it.icon }).containsExactly(ContainerIcon.GLASS, ContainerIcon.MUG, ContainerIcon.BOTTLE).inOrder()
        assertThat(buttons.map { it.contentDescription })
            .containsExactly("Add Glass, 250 ml", "Add Mug, 350 ml", "Add Bottle, 500 ml").inOrder()
        assertThat(buttons.map { it.containerId }).containsNoDuplicates()
    }

    @Test
    fun `five containers are cut to the first three`() {
        val five = (0..4).map { container("Cup $it", 100 + it * 50, it) }
        val buttons = build(snapshot(1250), containers = five).buttons
        assertThat(buttons).hasSize(3)
        assertThat(buttons.map { it.containerId }).containsExactlyElementsIn(five.take(3).map { it.id }).inOrder()
    }

    @Test
    fun `one container gives one button`() {
        assertThat(build(snapshot(1250), containers = three.filter { it.sortOrder == 0 }).buttons).hasSize(1)
    }

    @Test
    fun `no containers leaves one button that opens the app`() {
        val buttons = build(snapshot(1250), containers = emptyList()).buttons
        assertThat(buttons).hasSize(1)
        assertThat(buttons.single().containerId).isNull()
        assertThat(buttons.single().label).isEqualTo("Open app")
        assertThat(buttons.single().shortLabel).isEqualTo("Open")
        assertThat(buttons.single().icon).isNull()
        assertThat(buttons.single().contentDescription).isEqualTo("Open the app to add a drink container")
    }

    @Test
    fun `unfinished onboarding is flagged`() {
        val model = build(snapshot(0), onboarded = false)
        assertThat(model.needsSetup).isTrue()
        assertThat(model.setupText).isEqualTo("Finish setup in the app")
    }

    @Test
    fun `millilitres show whole numbers with grouping`() {
        val model = build(snapshot(1250), units = VolumeUnit.ML)
        assertThat(model.amountText).isEqualTo("1,250 ml")
        assertThat(model.goalText).isEqualTo("2,500 ml")
        assertThat(model.compactText).isEqualTo("1,250 / 2,500 ml")
    }

    @Test
    fun `fluid ounces convert amounts, goal and buttons`() {
        val model = build(snapshot(1250, pace = Pace(1500, 250, PaceStatus.BEHIND)), units = VolumeUnit.US_FL_OZ)
        assertThat(model.amountText).isEqualTo("42.3 fl oz")
        assertThat(model.goalText).isEqualTo("84.5 fl oz")
        assertThat(model.paceText).isEqualTo("8.5 fl oz behind")
        assertThat(model.buttons.first().label).isEqualTo("+8.5 fl oz")
        assertThat(model.buttons.first().shortLabel).isEqualTo("+8.5")
        assertThat(model.buttons.first().contentDescription).isEqualTo("Add Glass, 8.5 fl oz")
    }

    @Test
    fun `numbers follow the locale`() {
        val model = build(snapshot(1250), units = VolumeUnit.L, locale = Locale.GERMANY)
        assertThat(model.amountText).isEqualTo("1,25 L")
    }

    @Test
    fun `every sample state builds`() {
        WidgetSample.entries.forEach { sampleModel(resources, it) }
    }
}
