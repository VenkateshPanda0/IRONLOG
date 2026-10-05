package app.ironlog.personal.data.backup

/** What a stored backup holds, shown before restoring it. */
data class SnapshotInfo(val name: String, val exportedAt: Long, val workouts: Int, val photos: Int)
