package casa.peterson.dhmo.feature.reminders

import casa.peterson.dhmo.core.model.settings.CurvePoint
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PaceCurveEditorTest {
    @Test
    fun validatesEndpointsOrderAndDecimalPrecision() {
        val valid = listOf(CurveDraftPoint("0", "0"), CurveDraftPoint("12.5", "25"), CurveDraftPoint("100", "100"))
        assertThat(pointsFromDraft(valid)).containsExactly(CurvePoint(0, 0), CurvePoint(125, 250), CurvePoint(1000, 1000)).inOrder()
        assertThat(pointsFromDraft(valid + CurveDraftPoint("100", "100"))).isNull()
        assertThat(pointsFromDraft(valid.mapIndexed { i, p -> if (i == 1) p.copy(y = "100.1") else p })).isNull()
        assertThat(pointsFromDraft(valid.mapIndexed { i, p -> if (i == 1) p.copy(x = "12.55") else p })).isNull()
        assertThat(pointsFromDraft(valid.mapIndexed { i, p -> if (i == 0) p.copy(y = "1") else p })).isNull()
    }

    @Test
    fun addPointSplitsWidestSegmentAndKeepsCurveValid() {
        val source = listOf(CurvePoint(0, 0), CurvePoint(1000, 1000))
        assertThat(addMidpoint(source)).containsExactly(CurvePoint(0, 0), CurvePoint(500, 500), CurvePoint(1000, 1000)).inOrder()
    }
}
