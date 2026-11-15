package casa.peterson.dhmo.core.backup

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upgrades older documents step by step before decoding (TECHNICAL_PLAN §11.2). */
object BackupMigrations {
    /** The newest schema this app writes and reads. */
    const val CURRENT_SCHEMA_VERSION = 1

    /** `steps[n]` upgrades a schema `n` document to `n + 1`. Empty while only v1 exists. */
    private val steps: Map<Int, (JsonObject) -> JsonObject> = emptyMap()

    fun migrate(root: JsonObject): JsonObject {
        var current = root
        var version = schemaVersionOf(current)
        if (version > CURRENT_SCHEMA_VERSION) throw BackupException(BackupError.NEWER_VERSION)
        while (version < CURRENT_SCHEMA_VERSION) {
            val step = steps[version] ?: throw BackupException(BackupError.CORRUPT, "no migration from $version")
            version++
            current = JsonObject(step(current) + ("schemaVersion" to JsonPrimitive(version)))
        }
        return current
    }

    private fun schemaVersionOf(root: JsonObject): Int = (root["schemaVersion"] as? JsonPrimitive)?.content?.toIntOrNull()
        ?: throw BackupException(BackupError.NOT_A_BACKUP, "schemaVersion missing")
}
