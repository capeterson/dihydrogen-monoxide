package casa.peterson.dhmo.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import casa.peterson.dhmo.R
import casa.peterson.dhmo.core.designsystem.icon.DhmoIcons
import casa.peterson.dhmo.feature.history.navigation.HistoryRoute
import casa.peterson.dhmo.feature.insights.navigation.InsightsRoute
import casa.peterson.dhmo.feature.settings.navigation.SettingsRoute
import casa.peterson.dhmo.feature.today.navigation.TodayRoute

/** The four bottom bar / rail destinations (TECHNICAL_PLAN §13.5). */
enum class TopLevelDestination(val route: Any, @StringRes val label: Int, @DrawableRes val icon: Int) {
    Today(TodayRoute, R.string.nav_today, DhmoIcons.NavToday),
    History(HistoryRoute, R.string.nav_history, DhmoIcons.NavHistory),
    Insights(InsightsRoute(), R.string.nav_insights, DhmoIcons.NavInsights),
    Settings(SettingsRoute, R.string.nav_settings, DhmoIcons.NavSettings),
}
