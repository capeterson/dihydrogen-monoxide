package casa.peterson.dhmo.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.component.DhmoScreen
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.designsystem.component.SectionCard
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import casa.peterson.dhmo.core.domain.goal.GoalPolicyV1
import casa.peterson.dhmo.core.model.settings.VolumeUnit

@Composable
internal fun SourcesScreen(onBack: () -> Unit) {
    DhmoScreen(topBar = { DhmoTopBar(stringResource(R.string.feature_profile_sources_title), onNavigateBack = onBack) }) { padding ->
        SourcesContent(Modifier.padding(padding).consumeWindowInsets(padding))
    }
}

@Composable
internal fun SourcesContent(modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    fun ml(value: Int) = VolumeFormatter.format(value, VolumeUnit.ML, locale)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard { Text(stringResource(R.string.feature_profile_disclaimer)) }
        Text(stringResource(R.string.feature_profile_sources_intro))
        SectionCard(title = stringResource(R.string.feature_profile_sources_title)) {
            Text(stringResource(R.string.feature_profile_source_efsa))
            Text(stringResource(R.string.feature_profile_source_nasem))
            Text(stringResource(R.string.feature_profile_source_acsm))
        }
        Text(stringResource(R.string.feature_profile_drinks_only))
        Text(
            stringResource(
                R.string.feature_profile_limits,
                ml(GoalPolicyV1.adultMinMl),
                ml(GoalPolicyV1.adultMaxMl),
                ml(GoalPolicyV1.childMinMl),
                ml(GoalPolicyV1.childMaxMl),
            ),
        )
    }
}
