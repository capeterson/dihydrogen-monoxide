package casa.peterson.dhmo.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
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

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
