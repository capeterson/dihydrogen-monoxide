package casa.peterson.dhmo.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource

/** An in-memory [DhmoDatabase] for Robolectric DAO tests. */
class DatabaseTestRule : ExternalResource() {
    lateinit var db: DhmoDatabase
        private set

    override fun before() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DhmoDatabase::class.java).allowMainThreadQueries().build()
    }

    override fun after() {
        db.close()
    }
}
