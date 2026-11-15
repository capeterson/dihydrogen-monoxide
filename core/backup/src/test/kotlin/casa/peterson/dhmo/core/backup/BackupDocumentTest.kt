package casa.peterson.dhmo.core.backup

import casa.peterson.dhmo.core.model.settings.VolumeUnit
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class BackupDocumentTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** The abridged example of TECHNICAL_PLAN §11.2. */
    private val example =
        """
        {
          "format": "dhmo-backup", "schemaVersion": 1, "appVersion": "1.0.0", "appVersionCode": 1,
          "dbVersion": 1, "policyVersion": 1, "createdAt": "2026-11-15T09:30:00Z", "deviceName": "Pixel 8",
          "counts": {"intakeEntries": 2, "intakeTombstones": 1, "containers": 3, "profileSnapshots": 1, "dailyGoals": 1,
                     "dayOverrides": 0, "activityAdjustments": 0, "weatherDays": 0, "healthDaily": 0},
          "entities": {
            "intakeEntries": [
              {"id": "5f0c1a52-8f2e-4b6e-9a57-0d7b8f3c2a11", "instant": "2026-11-15T07:05:00Z", "zoneOffsetSeconds": 3600,
               "hydrationDay": "2026-11-15", "volumeMl": 250, "source": "MANUAL",
               "createdAt": "2026-11-15T07:05:02Z", "updatedAt": "2026-11-15T07:05:02Z"}
            ],
            "containers": [
              {"id": "00000000-0000-4000-8000-000000000001", "name": "Glass", "volumeMl": 250, "icon": "GLASS", "sortOrder": 0,
               "updatedAt": "2026-11-01T10:00:00Z"}
            ]
          },
          "settings": {"schemaVersion": 1, "units": "ML", "dayStartMinutes": 0}
        }
        """.trimIndent()

    @Test
    fun `the plan example decodes`() {
        val doc = json.decodeFromString<BackupDocument>(example)
        assertThat(doc.format).isEqualTo("dhmo-backup")
        assertThat(doc.entities.intakeEntries.single().volumeMl).isEqualTo(250)
        assertThat(doc.entities.containers.single().name).isEqualTo("Glass")
        assertThat(doc.settings.units).isEqualTo(VolumeUnit.ML)
    }

    @Test
    fun `encoding and decoding is lossless`() {
        val doc = json.decodeFromString<BackupDocument>(example)
        assertThat(json.decodeFromString<BackupDocument>(json.encodeToString(BackupDocument.serializer(), doc))).isEqualTo(doc)
    }
}
