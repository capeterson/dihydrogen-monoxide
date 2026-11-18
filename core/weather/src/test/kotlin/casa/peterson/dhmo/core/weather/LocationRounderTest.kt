package casa.peterson.dhmo.core.weather

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocationRounderTest {
    @Test
    fun `rounds to a tenth of a degree`() {
        assertThat(LocationRounder.toE1(52.5237)).isEqualTo(525)
        assertThat(LocationRounder.toE1(13.46)).isEqualTo(135)
        assertThat(LocationRounder.toE1(-33.868)).isEqualTo(-339)
        assertThat(LocationRounder.toE1(0.04)).isEqualTo(0)
    }
}
