package casa.peterson.dhmo.sync.healthconnect.rationale

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import casa.peterson.dhmo.core.designsystem.component.ComingSoon
import casa.peterson.dhmo.core.designsystem.theme.DhmoTheme

/**
 * Explains each Health Connect data type and links to the privacy policy (§10.3). A stub until Phase 5; its
 * manifest entries are commented out in this module's `AndroidManifest.xml`.
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DhmoTheme { ComingSoon() } }
    }
}
