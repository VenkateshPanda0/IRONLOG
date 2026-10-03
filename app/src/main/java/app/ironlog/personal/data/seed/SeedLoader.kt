package app.ironlog.personal.data.seed

import app.ironlog.personal.data.db.*
import androidx.room.withTransaction

/** Stable, bundled records only. No network access and no invented nutrient values. */
class SeedLoader(private val db:IronlogDatabase) {
    suspend fun load()=db.withTransaction {
        // No seed rows are inserted unless licensed exercise/FDC source files are bundled.
        // User-created records are never overwritten by this loader.
    }
}
