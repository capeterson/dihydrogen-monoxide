package casa.peterson.dhmo.feature.today

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.data.di.RepositoryModule
import casa.peterson.dhmo.core.data.repository.ContainerRepository
import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import casa.peterson.dhmo.core.data.repository.GoalRepository
import casa.peterson.dhmo.core.data.repository.IntakeRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.testing.FakeContainerRepository
import casa.peterson.dhmo.core.testing.FakeDeviceStateRepository
import casa.peterson.dhmo.core.testing.FakeGoalRepository
import casa.peterson.dhmo.core.testing.FakeIntakeRepository
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.feature.today.quicklog.QuickLogActivity
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@HiltAndroidTest
@UninstallModules(RepositoryModule::class)
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class QuickLogInteractionTest {
    private val fakeIntake = FakeIntakeRepository()

    @BindValue @JvmField
    val intake: IntakeRepository = fakeIntake

    @BindValue @JvmField
    val containers: ContainerRepository = FakeContainerRepository()

    @BindValue @JvmField
    val device: DeviceStateRepository = FakeDeviceStateRepository()

    @BindValue @JvmField
    val goals: GoalRepository = FakeGoalRepository()

    @BindValue @JvmField
    val settings: SettingsRepository = FakeSettingsRepository()

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<QuickLogActivity>()

    @Test
    fun confirmsCustomAmountAndFinishes() {
        compose.onNodeWithText("250").performTextClearance()
        compose.onNodeWithText("Amount in ml").performTextInput("375")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { fakeIntake.all.isNotEmpty() }
        assertThat(fakeIntake.all.single().volumeMl).isEqualTo(375)
        assertThat(fakeIntake.all.single().source).isEqualTo(Source.NOTIFICATION)
        compose.waitUntil(5_000) { compose.activity.isFinishing }
    }
}
