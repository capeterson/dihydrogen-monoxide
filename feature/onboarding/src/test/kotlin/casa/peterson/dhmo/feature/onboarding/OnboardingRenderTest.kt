package casa.peterson.dhmo.feature.onboarding

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.domain.body.BodyInputs
import casa.peterson.dhmo.core.domain.goal.GoalCalculator
import casa.peterson.dhmo.core.model.ActivityInput
import casa.peterson.dhmo.core.model.ClimateInput
import casa.peterson.dhmo.core.model.GoalInput
import casa.peterson.dhmo.core.model.settings.AdjustmentToggles
import casa.peterson.dhmo.core.testing.TestData
import casa.peterson.dhmo.core.testing.renderScreenVariants
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith

private fun capture(page: OnboardingPage) {
    renderScreenVariants("Onboarding_${page.name}") {
        OnboardingPageContent(
            page = page,
            state = OnboardingState(loading = false, birthDate = "1990-05-17", body = BodyInputs(weight = "65")),
            preview = GoalCalculator().calculate(
                GoalInput(
                    LocalDate.of(2026, 6, 10),
                    TestData.adultProfile,
                    ActivityInput.Level(TestData.adultProfile.activityLevel),
                    ClimateInput.Manual(TestData.adultProfile.climate),
                    AdjustmentToggles(),
                ),
            ),
            change = {},
            next = {},
            back = {},
            allowNotifications = {},
            skipNotifications = {},
        )
    }
}

@RunWith(AndroidJUnit4::class)
class OnboardingWelcomeRenderTest {
    @Test fun captureScreen() = capture(OnboardingPage.WELCOME)
}

@RunWith(AndroidJUnit4::class)
class OnboardingDisclaimerRenderTest {
    @Test fun captureScreen() = capture(OnboardingPage.DISCLAIMER)
}

@RunWith(AndroidJUnit4::class)
class OnboardingUnitsRenderTest {
    @Test fun captureScreen() = capture(OnboardingPage.UNITS)
}

@RunWith(AndroidJUnit4::class)
class OnboardingGoalModeRenderTest {
    @Test fun captureScreen() = capture(OnboardingPage.GOAL_MODE)
}

@RunWith(AndroidJUnit4::class)
class OnboardingProfileRenderTest {
    @Test fun captureScreen() = capture(OnboardingPage.PROFILE)
}

@RunWith(AndroidJUnit4::class)
class OnboardingManualRenderTest {
    @Test fun captureScreen() = capture(OnboardingPage.MANUAL)
}

@RunWith(AndroidJUnit4::class)
class OnboardingPreviewRenderTest {
    @Test fun captureScreen() = capture(OnboardingPage.PREVIEW)
}

@RunWith(AndroidJUnit4::class)
class OnboardingNotificationRenderTest {
    @Test fun captureScreen() = capture(OnboardingPage.NOTIFICATION)
}

@RunWith(AndroidJUnit4::class)
class OnboardingRemindersRenderTest {
    @Test fun captureScreen() = capture(OnboardingPage.REMINDERS)
}

@RunWith(AndroidJUnit4::class)
class OnboardingDoneRenderTest {
    @Test fun captureScreen() = capture(OnboardingPage.DONE)
}
