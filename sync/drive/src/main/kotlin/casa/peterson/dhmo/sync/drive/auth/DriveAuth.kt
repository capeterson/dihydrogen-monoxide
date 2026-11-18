package casa.peterson.dhmo.sync.drive.auth

/** The two things the backup controller needs from Google sign-in; [GoogleAuthManager] is the real one. */
interface DriveAuth {
    /** False when this build has no Web OAuth client id. */
    val isConfigured: Boolean

    /** Revokes the Drive scope and forgets the signed-in account. */
    suspend fun disconnect()
}
