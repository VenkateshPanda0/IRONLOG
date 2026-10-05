package app.ironlog.personal.data

import app.ironlog.personal.data.db.IronlogDatabase

/** Runs [block] in one database transaction: all of its writes happen, or none do. */
expect suspend fun <R> IronlogDatabase.transaction(block: suspend () -> R): R
