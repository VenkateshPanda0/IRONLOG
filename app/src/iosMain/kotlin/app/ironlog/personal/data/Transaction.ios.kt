package app.ironlog.personal.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import app.ironlog.personal.data.db.IronlogDatabase

actual suspend fun <R> IronlogDatabase.transaction(block: suspend () -> R): R =
    useWriterConnection { it.immediateTransaction { block() } }
