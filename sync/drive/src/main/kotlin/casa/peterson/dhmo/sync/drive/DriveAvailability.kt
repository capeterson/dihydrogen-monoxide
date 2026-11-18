package casa.peterson.dhmo.sync.drive

/** Whether this build has a Google Web OAuth client id (`dhmo.googleWebClientId`), so Drive sign-in can work at all. */
object DriveAvailability {
    val isConfigured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
}
