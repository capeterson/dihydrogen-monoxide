package casa.peterson.dhmo.feature.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.model.DailyTotal
import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.testing.TestData
import casa.peterson.dhmo.core.testing.captureScreenVariants
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HistoryScreenshotTest {
    @Test
    fun historyStates() {
        captureScreenVariants("History") { HistorySample() }
    }

    @Test
    fun weeklyGardenShowsSoilSproutsAndBlooms() {
        val today = LocalDate.of(2026, 3, 9)
        val amounts = listOf(0, 750, 1500, 2000, 250, 1250, 1750)
        val days = List(7) { today.minusDays((6 - it).toLong()) }
        val state = HistoryUiState(
            totals = days.zip(amounts).filter { it.second > 0 }.associate { (day, amount) ->
                val at = day.atStartOfDay().toInstant(ZoneOffset.UTC)
                day to DailyTotal(day, amount, 1, at, at)
            },
            goals = days.associateWith { 2000 },
            today = today,
        )
        captureScreenVariants("HistoryGarden") { HistoryGarden(state, today, animated = false) }
    }
}

@Composable
private fun HistorySample() {
    val date = LocalDate.of(2026, 3, 9)
    val manual = TestData.entry(Instant.parse("2026-03-09T12:00:00Z"), 500)
    val imported = TestData.entry(Instant.parse("2026-03-09T08:00:00Z"), 250, source = Source.HEALTH_CONNECT_IMPORT)
    Scaffold(topBar = { DhmoTopBar("History") }) { padding ->
        Column(Modifier.padding(padding)) {
            HistoryDayHeader(HistoryItem.DayHeader(date, 750, 2000, true), VolumeUnit.ML) { }
            HistoryEntryRow(manual, VolumeUnit.ML, null, {}, {}, {})
            HistoryEntryRow(imported, VolumeUnit.ML, "Other Water", {}, {}, {})
        }
    }
}
