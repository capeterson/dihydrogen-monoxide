package casa.peterson.dhmo.feature.settings.about

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import casa.peterson.dhmo.core.designsystem.component.DhmoScreen
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.designsystem.component.SectionCard
import casa.peterson.dhmo.core.designsystem.component.SettingsRow
import casa.peterson.dhmo.feature.settings.R
import casa.peterson.dhmo.feature.settings.root.appVersionName

@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember(context) { appVersionName(context) }
    val privacyUrl = stringResource(R.string.feature_settings_privacy_policy_url)
    val privacyUri = remember(privacyUrl) { privacyUrl.toUri() }
    AboutContent(
        versionName = versionName,
        onBack = onBack,
        onOpenPrivacyPolicy = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, privacyUri))
            } catch (_: ActivityNotFoundException) {
                // No browser installed; nothing else can show the page.
            }
        },
    )
}

@Composable
internal fun AboutContent(versionName: String, onBack: () -> Unit, onOpenPrivacyPolicy: () -> Unit, showLicences: Boolean = false) {
    var licencesOpen by rememberSaveable { mutableStateOf(showLicences) }
    DhmoScreen(topBar = { DhmoTopBar(stringResource(R.string.feature_settings_about_title), onNavigateBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = stringResource(R.string.feature_settings_app_name)) {
                val version = versionName.ifEmpty { stringResource(R.string.feature_settings_unknown) }
                Text(
                    stringResource(R.string.feature_settings_version, version),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            SectionCard(title = stringResource(R.string.feature_settings_disclaimer_title)) {
                Text(stringResource(R.string.feature_settings_disclaimer_body))
            }
            SectionCard {
                SettingsRow(
                    stringResource(R.string.feature_settings_privacy_policy),
                    summary = stringResource(R.string.feature_settings_privacy_policy_summary),
                    onClick = onOpenPrivacyPolicy,
                )
                SettingsRow(
                    stringResource(R.string.feature_settings_licences),
                    summary = stringResource(R.string.feature_settings_licences_summary),
                    onClick = { licencesOpen = true },
                )
            }
        }
    }
    if (licencesOpen) LicencesDialog(onDismiss = { licencesOpen = false })
}

@Composable
private fun LicencesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_settings_licences)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.feature_settings_licences_intro))
                OpenSourceLibraries.forEach { library ->
                    Column {
                        Text(library.name, style = MaterialTheme.typography.titleSmall)
                        Text(library.licence, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_settings_close)) } },
    )
}

internal data class OpenSourceLibrary(val name: String, val licence: String)

private const val APACHE_2 = "Apache License 2.0"

/** Third-party libraries bundled in the app (`gradle/libs.versions.toml`, runtime dependencies only). */
internal val OpenSourceLibraries = listOf(
    OpenSourceLibrary(
        "Android Jetpack (AndroidX): Compose, Navigation, Room, DataStore, WorkManager, Paging, Glance, Lifecycle, Credentials",
        APACHE_2,
    ),
    OpenSourceLibrary("Material Components for Compose (Material 3)", APACHE_2),
    OpenSourceLibrary("Dagger and Hilt", APACHE_2),
    OpenSourceLibrary("Kotlin standard library, kotlinx.coroutines, kotlinx.serialization", APACHE_2),
    OpenSourceLibrary("Health Connect client", APACHE_2),
    OpenSourceLibrary("OkHttp and Okio", APACHE_2),
    OpenSourceLibrary("Vico", APACHE_2),
)
