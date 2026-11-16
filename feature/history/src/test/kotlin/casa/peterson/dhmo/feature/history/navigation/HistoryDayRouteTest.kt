package casa.peterson.dhmo.feature.history.navigation

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class HistoryDayRouteTest {
    @Test
    fun `valid epoch day maps to its date`() {
        val day = LocalDate.of(2026, 11, 15)
        assertThat(HistoryDayRoute(day.toEpochDay()).date).isEqualTo(day)
    }

    @Test
    fun `range limits of LocalDate are accepted`() {
        assertThat(HistoryDayRoute(LocalDate.MIN.toEpochDay()).date).isEqualTo(LocalDate.MIN)
        assertThat(HistoryDayRoute(LocalDate.MAX.toEpochDay()).date).isEqualTo(LocalDate.MAX)
    }

    @Test
    fun `out of range epoch day from a crafted deep link is null instead of throwing`() {
        assertThat(HistoryDayRoute(Long.MAX_VALUE).date).isNull()
        assertThat(HistoryDayRoute(Long.MIN_VALUE).date).isNull()
        assertThat(HistoryDayRoute(LocalDate.MAX.toEpochDay() + 1).date).isNull()
    }
}
