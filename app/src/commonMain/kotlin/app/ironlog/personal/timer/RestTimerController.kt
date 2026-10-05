package app.ironlog.personal.timer

import app.ironlog.personal.data.db.IronlogDao
import app.ironlog.personal.data.db.RestTimerEntity
import app.ironlog.personal.platform.ioDispatcher
import app.ironlog.personal.time.nowMillis
import kotlinx.coroutines.withContext

/** The platform's way to ring when a rest period ends. */
interface RestAlarm {
    fun schedule(endAtMs: Long)

    fun cancel()
}

class RestTimerController(private val dao: IronlogDao, private val alarm: RestAlarm) {
    suspend fun start(sessionId: Long, durationSec: Int = 90) =
        withContext(ioDispatcher) {
            val end = nowMillis() + durationSec.coerceAtLeast(1) * 1000L
            dao.saveRestTimer(
                RestTimerEntity(
                    sessionId = sessionId,
                    endAtEpochMs = end,
                    durationSec = durationSec,
                    isRunning = true,
                )
            )
            schedule(end)
        }

    suspend fun adjust(seconds: Int) =
        withContext(ioDispatcher) {
            val timer = dao.restTimer() ?: return@withContext
            val end = timer.endAtEpochMs + seconds * 1000L
            if (end <= nowMillis()) skip()
            else {
                dao.saveRestTimer(timer.copy(endAtEpochMs = end))
                schedule(end)
            }
        }

    suspend fun skip() =
        withContext(ioDispatcher) {
            cancel()
            dao.clearRestTimer()
        }

    suspend fun remainingMs(): Long =
        withContext(ioDispatcher) {
            ((dao.restTimer()?.endAtEpochMs ?: 0L) - nowMillis()).coerceAtLeast(0L)
        }

    private fun schedule(endAt: Long) = alarm.schedule(endAt)

    private fun cancel() = alarm.cancel()
}
