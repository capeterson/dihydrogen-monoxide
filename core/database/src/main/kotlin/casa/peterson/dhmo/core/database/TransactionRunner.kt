package casa.peterson.dhmo.core.database

import androidx.room.withTransaction
import javax.inject.Inject

/** Lets `:core:data` run multi-table writes in one transaction and be unit-tested with a fake. */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

internal class RoomTransactionRunner
@Inject
constructor(private val database: DhmoDatabase) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction { block() }
}
