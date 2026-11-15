package casa.peterson.dhmo.core.datastore

import casa.peterson.dhmo.core.model.settings.AppSettings

/** Upgrades decoded settings step by step. Version 1 is the first version, so this is the identity for now. */
object SettingsMigrations {
    const val CURRENT_VERSION = 1

    fun migrate(decoded: AppSettings): AppSettings = when (decoded.schemaVersion) {
        else -> decoded
    }
}
