package casa.peterson.dhmo.sync.drive.auth

import android.accounts.Account
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import casa.peterson.dhmo.sync.drive.BuildConfig
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/** What asking Google for Drive access led to. */
sealed interface DriveAuthResult {
    data class Authorized(val accessToken: String) : DriveAuthResult

    /** Consent is needed: only an activity can show it. */
    data class NeedsResolution(val pendingIntent: PendingIntent) : DriveAuthResult

    /** The flow was dismissed or failed; nothing changed. */
    data object Cancelled : DriveAuthResult

    /** This build has no Web OAuth client id. */
    data object NotConfigured : DriveAuthResult
}

/**
 * Sign-in with Credential Manager, then the Authorization API for the single `drive.appdata` scope
 * (TECHNICAL_PLAN §11.5). The app keeps the e-mail address for display and never sends the ID token anywhere.
 */
@Singleton
class GoogleAuthManager @Inject internal constructor(
    @param:ApplicationContext private val context: Context,
    private val deviceState: DeviceStateRepository,
) : DriveAuth {
    override val isConfigured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /** Shows Google's account chooser. Returns the account's e-mail address, or null when dismissed. */
    suspend fun signIn(activity: Activity): String? {
        if (!isConfigured) return null
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build())
            .build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleIdTokenCredential.createFrom(credential.data).email
            } else {
                null
            }
        } catch (_: NoCredentialException) {
            null // no Google account on this phone
        } catch (_: GetCredentialException) {
            null // dismissed, cancelled or failed
        }
    }

    /** Asks for the Drive scope. Safe to call from a worker: it never shows UI, it reports [DriveAuthResult.NeedsResolution]. */
    suspend fun authorize(): DriveAuthResult {
        if (!isConfigured) return DriveAuthResult.NotConfigured
        return try {
            toResult(Identity.getAuthorizationClient(context).authorize(request(deviceState.current().backupRuntime.accountEmail)).await())
        } catch (_: ApiException) {
            DriveAuthResult.Cancelled
        }
    }

    /** Called with the data of the consent screen's result. */
    suspend fun completeResolution(data: Intent?): DriveAuthResult = try {
        toResult(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))
    } catch (_: ApiException) {
        DriveAuthResult.Cancelled
    }

    suspend fun rememberAccount(email: String) {
        deviceState.update {
            it.copy(backupRuntime = it.backupRuntime.copy(accountEmail = email, needsReconnect = false, lastError = null))
        }
    }

    /** A rejected token is cleared from Google's cache so the next [authorize] returns a fresh one. */
    suspend fun clearToken(token: String) {
        runCatching { Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(token).build()).await() }
    }

    /** Revokes the scope and forgets the signed-in account. Failures are ignored: the local state is cleared regardless. */
    override suspend fun disconnect() {
        val email = deviceState.current().backupRuntime.accountEmail
        if (email != null) {
            runCatching {
                Identity.getAuthorizationClient(context)
                    .revokeAccess(
                        RevokeAccessRequest.builder().setAccount(
                            Account(email, GOOGLE_ACCOUNT_TYPE),
                        ).setScopes(listOf(DRIVE_SCOPE)).build(),
                    )
                    .await()
            }
        }
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    private fun request(email: String?): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(DRIVE_SCOPE))
        .apply { email?.let { setAccount(Account(it, GOOGLE_ACCOUNT_TYPE)) } }
        .build()

    private fun toResult(result: AuthorizationResult): DriveAuthResult {
        val pending = result.pendingIntent
        val token = result.accessToken
        return when {
            result.hasResolution() && pending != null -> DriveAuthResult.NeedsResolution(pending)
            token != null -> DriveAuthResult.Authorized(token)
            else -> DriveAuthResult.Cancelled
        }
    }

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private val DRIVE_SCOPE = Scope(DRIVE_APPDATA_SCOPE)
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
