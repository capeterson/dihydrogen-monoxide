package casa.peterson.dhmo.sync.healthconnect.rationale

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.component.DhmoScreen
import casa.peterson.dhmo.core.designsystem.theme.DhmoTheme
import casa.peterson.dhmo.sync.healthconnect.R

/**
 * Explains each Health Connect data type (§10.3). Health Connect shows this as the app's rationale on Android 9 to 13
 * and links to it from the permission screen on Android 14 and later.
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DhmoTheme {
                DhmoScreen { padding ->
                    Column(
                        Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(stringResource(R.string.hc_rationale_title), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.hc_rationale_intro), style = MaterialTheme.typography.bodyLarge)
                        listOf(
                            R.string.hc_rationale_hydration,
                            R.string.hc_rationale_weight,
                            R.string.hc_rationale_exercise,
                            R.string.hc_rationale_activity,
                            R.string.hc_rationale_background,
                        ).forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}
