package casa.peterson.dhmo.wear

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.protolayout.DeviceParametersBuilders
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.wear.WearContainer
import casa.peterson.dhmo.core.wear.WearState
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaterTileModelTest {
    private val state = WearState(
        "2026-11-15",
        1250,
        2000,
        VolumeUnit.ML.name,
        listOf(WearContainer("Glass", 250), WearContainer("Bottle", 500), WearContainer("Flask", 750)),
    )

    @Test
    fun `model shows progress, total, goal and two add buttons`() {
        val model = WaterTileModel.from(state, Locale.US)

        assertThat(model.percent).isEqualTo(63)
        assertThat(model.total).contains("1,250")
        assertThat(model.goal).contains("2,000")
        assertThat(model.buttons.map { it.volumeMl }).containsExactly(250, 500).inOrder()
        assertThat(model.connected).isTrue()
    }

    @Test
    fun `click ids map back to volumes and reject anything else`() {
        val button = WaterTileModel.from(state, Locale.US).buttons.first()
        assertThat(WaterTileModel.volumeForClick(button.clickId)).isEqualTo(250)
        assertThat(WaterTileModel.volumeForClick(null)).isNull()
        assertThat(WaterTileModel.volumeForClick("")).isNull()
        assertThat(WaterTileModel.volumeForClick("add_abc")).isNull()
        assertThat(WaterTileModel.volumeForClick("add_0")).isNull()
        assertThat(WaterTileModel.volumeForClick("add_99999")).isNull()
    }

    @Test
    fun `before the phone has sent anything the tile says so`() {
        val model = WaterTileModel.from(null, Locale.US)
        assertThat(model.connected).isFalse()
        assertThat(model.buttons).isEmpty()
    }

    @Test
    fun `fluid ounces and a missing goal are handled`() {
        val model = WaterTileModel.from(WearState("2026-11-15", 500, 0, VolumeUnit.US_FL_OZ.name), Locale.US)
        assertThat(model.total).contains("fl oz")
        assertThat(model.goal).isNull()
        assertThat(model.percent).isEqualTo(0)
        assertThat(model.buttons.single().volumeMl).isEqualTo(250) // the default glass
    }

    @Test
    fun `layout builds on round and square screens`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        listOf(DeviceParametersBuilders.SCREEN_SHAPE_ROUND, DeviceParametersBuilders.SCREEN_SHAPE_RECT).forEach { shape ->
            val device = DeviceParametersBuilders.DeviceParameters.Builder()
                .setScreenWidthDp(192)
                .setScreenHeightDp(192)
                .setScreenDensity(2f)
                .setScreenShape(shape)
                .build()
            listOf(WaterTileModel.from(state, Locale.US), WaterTileModel.from(null, Locale.US)).forEach { model ->
                assertThat(tileLayout(context, model, device)).isNotNull() // the builders throw on an invalid layout
            }
        }
    }
}
