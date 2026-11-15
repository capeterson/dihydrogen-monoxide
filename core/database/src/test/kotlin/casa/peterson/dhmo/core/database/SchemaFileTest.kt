package casa.peterson.dhmo.core.database

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** Plain JUnit sanity check of the exported Room schema files (TECHNICAL_PLAN §4.5). */
class SchemaFileTest {
    private val schemaDir =
        File(requireNotNull(System.getProperty("dhmo.schemaDir")) { "dhmo.schemaDir is not set" })
            .resolve("casa.peterson.dhmo.core.database.DhmoDatabase")

    private val versions: Map<Int, JsonObject> by lazy {
        schemaDir.listFiles { file -> file.extension == "json" }.orEmpty()
            .associate { it.nameWithoutExtension.toInt() to Json.parseToJsonElement(it.readText()).jsonObject }
    }

    @Test
    fun `highest schema version equals the database version`() {
        assertThat(versions.keys.max()).isEqualTo(DhmoDatabase.VERSION)
    }

    @Test
    fun `schema files are consecutive from 1`() {
        assertThat(versions.keys.sorted()).isEqualTo((1..versions.keys.max()).toList())
        versions.forEach { (version, root) ->
            assertThat(root.getValue("database").jsonObject.getValue("version").jsonPrimitive.int).isEqualTo(version)
        }
    }

    @Test
    fun `version 1 has exactly the planned tables and view`() {
        val database = versions.getValue(1).getValue("database").jsonObject
        val tables = database.getValue("entities").jsonArray.map { it.jsonObject.getValue("tableName").jsonPrimitive.content }
        assertThat(tables).containsExactly(
            "intake_entry", "container", "profile_snapshot", "daily_goal", "day_override",
            "activity_adjustment", "weather_day", "health_daily", "hc_pending_op", "sync_state",
        )
        val views = (database["views"] as? JsonArray).orEmpty().map { it.jsonObject.getValue("viewName").jsonPrimitive.content }
        assertThat(views).containsExactly("daily_total")
    }
}
