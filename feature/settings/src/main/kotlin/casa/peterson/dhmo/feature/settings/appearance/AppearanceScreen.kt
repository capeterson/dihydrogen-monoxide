package casa.peterson.dhmo.feature.settings.appearance

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.component.DhmoScreen
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.designsystem.component.SectionCard
import casa.peterson.dhmo.core.designsystem.component.SwitchRow
import casa.peterson.dhmo.core.model.settings.ThemeMode
import casa.peterson.dhmo.core.model.settings.ThemeSettings
import casa.peterson.dhmo.feature.settings.R
import casa.peterson.dhmo.feature.settings.root.themeLabel
import casa.peterson.dhmo.feature.settings.units.ChoiceRow

@Composable
internal fun AppearanceScreen(onBack: () -> Unit, viewModel: AppearanceViewModel = hiltViewModel()) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    AppearanceContent(
        theme = theme,
        dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        onBack = onBack,
        onMode = viewModel::setMode,
        onDynamicColor = viewModel::setDynamicColor,
    )
}

@Composable
internal fun AppearanceContent(
    theme: ThemeSettings,
    dynamicColorSupported: Boolean,
    onBack: () -> Unit,
    onMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
) {
    DhmoScreen(topBar = { DhmoTopBar(stringResource(R.string.feature_settings_appearance_title), onNavigateBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = stringResource(R.string.feature_settings_theme_heading)) {
                Column(Modifier.selectableGroup()) {
                    ThemeMode.entries.forEach { mode ->
                        ChoiceRow(stringResource(themeLabel(mode)), selected = mode == theme.mode, onSelect = { onMode(mode) })
                    }
                }
            }
            SectionCard {
                SwitchRow(
                    stringResource(R.string.feature_settings_dynamic_color),
                    checked = theme.dynamicColor && dynamicColorSupported,
                    onCheckedChange = onDynamicColor,
                    summary = stringResource(
                        if (dynamicColorSupported) {
                            R.string.feature_settings_dynamic_color_summary
                        } else {
                            R.string.feature_settings_dynamic_color_unsupported
                        },
                    ),
                    enabled = dynamicColorSupported,
                )
            }
        }
    }
}
