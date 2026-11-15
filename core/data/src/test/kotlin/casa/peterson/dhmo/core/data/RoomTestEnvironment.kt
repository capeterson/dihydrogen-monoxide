package casa.peterson.dhmo.core.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import casa.peterson.dhmo.core.database.DhmoDatabase
import casa.peterson.dhmo.core.database.TransactionRunner
import org.junit.rules.ExternalResource

/** An in-memory [DhmoDatabase] with a real transaction runner, for repository tests under Robolectric. */
class RoomTestEnvironment : ExternalResource() {
    lateinit var db: DhmoDatabase
        private set

    val transactions: TransactionRunner =
        object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
        }

    override fun before() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DhmoDatabase::class.java).allowMainThreadQueries().build()
    }

    override fun after() {
        db.close()
    }
}
