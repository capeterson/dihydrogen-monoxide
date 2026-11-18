package casa.peterson.dhmo.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Scaffold of the migration test (TECHNICAL_PLAN §4.5). With only version 1 it creates the v1 database,
 * inserts one row, then opens it through Room so the identity hash is validated. Each later version adds a
 * `migrateNToN+1` test and a `migrateAll` test that walks from 1 to [DhmoDatabase.VERSION].
 */
class MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            DhmoDatabase::class.java,
            listOf(DhmoDatabase.GenderMigration()),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun createVersion1AndOpenThroughRoom() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO container (id, name, volume_ml, icon, sort_order, updated_at, deleted_at) " +
                    "VALUES ('00000000-0000-4000-8000-000000000001', 'Glass', 250, 'GLASS', 0, 0, NULL)",
            )
            close()
        }
        val db =
            androidx.room.Room
                .databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, DhmoDatabase::class.java, TEST_DB)
                .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
                .build()
        db.openHelper.writableDatabase.close()
        db.close()
    }

    @Test
    fun migrate1To2RenamesSexToGenderAndRewritesStoredBreakdowns() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO profile_snapshot (id, effective_from, birth_date, sex, weight_kg, height_cm, activity_level, climate, " +
                    "pregnancy_status, lactating, high_altitude, source, created_at) VALUES " +
                    "('00000000-0000-4000-8000-000000000002', 20000, 7000, 'FEMALE', 65.0, 168.0, 'MODERATE', 'TEMPERATE', " +
                    "'NONE', 0, 0, 'MANUAL', 0)",
            )
            execSQL(
                "INSERT INTO daily_goal (date, target_ml, calculated_ml, mode, breakdown_json, day_start_minutes, policy_version, " +
                    "updated_at) VALUES (20000, 2300, 2300, 'CALCULATED', " +
                    "'{\"components\":[{\"kind\":\"BASE_SEX_FLOOR\",\"ml\":1600}]}', 0, 1, 0)",
            )
            close()
        }
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true)
        migrated.query("SELECT gender, weight_kg FROM profile_snapshot").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("FEMALE")
            assertThat(it.getDouble(1)).isEqualTo(65.0)
        }
        migrated.query("SELECT breakdown_json FROM daily_goal").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).contains("BASE_GENDER_FLOOR")
            assertThat(it.getString(0)).doesNotContain("BASE_SEX_FLOOR")
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
