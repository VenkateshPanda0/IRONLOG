package app.ironlog.personal.data.health

/** Stable client ids for records Ironlog writes to a health store, so writes are idempotent. */
object HealthIds {
    fun weight(id: Long) = "ironlog-weight-$id"

    fun workout(id: Long) = "ironlog-workout-$id"

    fun cardio(id: Long) = "ironlog-cardio-$id"
}
