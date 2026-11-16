package casa.peterson.dhmo.feature.today

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar

@Composable
internal fun BatteryHelpScreen(onBack: () -> Unit, viewModel: BatteryHelpViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    Scaffold(topBar = { DhmoTopBar(stringResource(R.string.feature_today_battery_help_title), onNavigateBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.feature_today_battery_body))
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) {
                Text(stringResource(R.string.feature_today_battery_settings))
            }
            TextButton(onClick = {
                uri.openUri("https://dontkillmyapp.com/")
            }) { Text(stringResource(R.string.feature_today_battery_learn_more)) }
            TextButton(onClick = {
                viewModel.dismiss()
                onBack()
            }) { Text(stringResource(R.string.feature_today_battery_dismiss)) }
        }
    }
}
