package app.ironlog.personal.data

import androidx.room.withTransaction
import app.ironlog.personal.data.db.IronlogDatabase

actual suspend fun <R> IronlogDatabase.transaction(block: suspend () -> R): R = withTransaction { block() }
