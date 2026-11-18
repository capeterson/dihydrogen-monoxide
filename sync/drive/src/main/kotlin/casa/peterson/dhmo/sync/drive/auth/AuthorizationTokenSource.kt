package casa.peterson.dhmo.sync.drive.auth

import casa.peterson.dhmo.sync.drive.api.DriveTokenSource
import javax.inject.Inject

/** Gives the Drive client Google's access token. With no stored consent it has none to give, which the client reports as `AUTH`. */
internal class AuthorizationTokenSource @Inject constructor(private val auth: GoogleAuthManager) : DriveTokenSource {
    override suspend fun token(): String? = (auth.authorize() as? DriveAuthResult.Authorized)?.accessToken

    override suspend fun invalidate(token: String) = auth.clearToken(token)
}
