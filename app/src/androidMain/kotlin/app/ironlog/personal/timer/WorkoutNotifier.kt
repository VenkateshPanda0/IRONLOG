package app.ironlog.personal.timer

import app.ironlog.personal.time.*

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.ironlog.personal.IronlogApp
import app.ironlog.personal.MainActivity
import app.ironlog.personal.R
import app.ironlog.personal.data.db.IronlogDao
import app.ironlog.personal.data.db.RestTimerEntity
import app.ironlog.personal.data.db.SessionExerciseEntity
import app.ironlog.personal.data.db.SetLogEntity
import app.ironlog.personal.data.db.WorkoutSessionEntity
import app.ironlog.personal.domain.WeightUnit
import app.ironlog.personal.domain.WorkoutMath
import app.ironlog.personal.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** The set to do next and the numbers to fill in if the user just taps Done. */
data class NextSet(
    val row: SessionExerciseEntity,
    val set: SetLogEntity,
    val number: Int,
    val of: Int,
    val weightKg: Double?,
    val reps: Int?,
)

object WorkoutFlow {
    /**
     * The next open set. After a set in a superset, the next exercise of the round comes next
     * (wrapping to the first); otherwise it is the first open set in exercise order.
     */
    fun next(rows: List<SessionExerciseEntity>, sets: List<SetLogEntity>): Pair<SessionExerciseEntity, SetLogEntity>? {
        val active = rows.filter { it.status != "SKIPPED" }.sortedBy { it.orderIndex }
        val byRow = sets.groupBy { it.sessionExerciseId }
        fun open(row: SessionExerciseEntity) = byRow[row.id].orEmpty().filter { !it.isCompleted }.minByOrNull { it.setIndex }
        val last = sets.filter { it.isCompleted && it.completedAt != null }.maxByOrNull { it.completedAt!! }
        val lastRow = last?.let { l -> active.firstOrNull { it.id == l.sessionExerciseId } }
        if (lastRow?.supersetGroup != null) {
            val members = active.filter { it.supersetGroup == lastRow.supersetGroup }
            val i = members.indexOfFirst { it.id == lastRow.id }
            for (k in 1..members.size) {
                val member = members[(i + k) % members.size]
                open(member)?.let { return member to it }
            }
        }
        for (row in active) open(row)?.let { return row to it }
        return null
    }

    /** True when rest should start after a set of [row]: not mid-way through a superset round. */
    fun restsAfter(row: SessionExerciseEntity, rows: List<SessionExerciseEntity>): Boolean {
        val group = row.supersetGroup ?: return true
        return rows.filter { it.supersetGroup == group }.maxByOrNull { it.orderIndex }?.id == row.id
    }

    /**
     * Fills a set's blanks the way the logger's hints do: the same working set last session,
     * else the previous completed set of this exercise today.
     */
    suspend fun resolve(dao: IronlogDao, row: SessionExerciseEntity, set: SetLogEntity, rowSets: List<SetLogEntity>): NextSet {
        val ordered = rowSets.sortedBy { it.setIndex }
        val history = dao.exerciseHistory(row.exerciseId).first()
        val lastWorking = history.groupBy { it.sessionId }.values.firstOrNull().orEmpty().filter { it.type == "WORKING" }
        val workingNumber = ordered.filter { it.type == "WORKING" }.indexOfFirst { it.id == set.id }
        val fromHistory = if (set.type == "WORKING" && workingNumber >= 0) lastWorking.getOrNull(workingNumber) else null
        val prior = ordered.lastOrNull { it.setIndex < set.setIndex && it.isCompleted }
        return NextSet(
            row,
            set,
            ordered.indexOfFirst { it.id == set.id } + 1,
            ordered.size,
            set.weightKg ?: fromHistory?.weightKg ?: prior?.weightKg,
            set.reps ?: fromHistory?.reps ?: prior?.reps,
        )
    }
}

/**
 * Keeps an ongoing, silent notification in step with the active workout: elapsed time or the
 * rest countdown, the next set, and Done / +30 s / Skip rest buttons usable from the lock screen.
 * It observes the database, so it updates the same way whether a change came from the app or a
 * notification button.
 */
class WorkoutNotifier(private val context: Context, private val dao: IronlogDao, private val unit: Flow<WeightUnit>) {
    private val manager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private data class State(
        val session: WorkoutSessionEntity,
        val rows: List<SessionExerciseEntity>,
        val sets: List<SetLogEntity>,
        val rest: RestTimerEntity?,
        val unit: WeightUnit,
    )

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            dao.activeSession()
                .flatMapLatest { session ->
                    if (session == null) flowOf(null)
                    else combine(dao.sessionExercises(session.id), dao.sessionSets(session.id), dao.restTimerFlow(), unit) { rows, sets, rest, u -> State(session, rows, sets, rest, u) }
                }
                .debounce(150)
                .collect { state -> runCatching { if (state == null) cancel() else show(state) } }
        }
    }

    fun cancel() = manager.cancel(NOTIFICATION_ID)

    private suspend fun show(s: State) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Workout in progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Live workout with next set and rest timer, also on the lock screen"
                setShowBadge(false)
            }
        )
        if (!ReminderScheduler.canNotify(context)) return
        val now = nowMillis()
        val paused = s.session.status == "PAUSED"
        val restEnd = s.rest?.takeIf { it.isRunning && it.sessionId == s.session.id && it.endAtEpochMs > now }?.endAtEpochMs
        val next = WorkoutFlow.next(s.rows, s.sets)?.let { (row, set) -> WorkoutFlow.resolve(dao, row, set, s.sets.filter { it.sessionExerciseId == row.id }) }
        val done = s.sets.count { it.isCompleted }
        val nextLine =
            next?.let { n ->
                val load = listOfNotNull(n.weightKg?.takeIf { it > 0 }?.let(s.unit::format), n.reps?.let { "$it reps" }).joinToString(" × ")
                "${n.row.exerciseNameSnapshot} · set ${n.number} of ${n.of}" + if (load.isNotEmpty()) " · $load" else ""
            } ?: "All sets done. Tap to finish."
        val title =
            when {
                paused -> "${s.session.name} · paused"
                restEnd != null -> "Rest · next up"
                else -> s.session.name
            }
        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(nextLine)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$nextLine\n$done of ${s.sets.size} sets done"))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_WORKOUT)
                .setContentIntent(openWorkout(s.session.id))
        if (restEnd != null) {
            builder.setUsesChronometer(true).setChronometerCountDown(true).setWhen(restEnd).setShowWhen(true)
        } else if (!paused) {
            // Count up from the effective start so pauses are left out.
            val elapsed = WorkoutMath.elapsedMs(s.session.startedAt, s.session.endedAt, s.session.totalPausedMs, s.session.pausedAt, now)
            builder.setUsesChronometer(true).setWhen(now - elapsed).setShowWhen(true)
        }
        if (next != null && !paused) builder.addAction(0, "Done", action(ACTION_COMPLETE, next.set.id))
        if (restEnd != null) {
            builder.addAction(0, "+30 s", action(ACTION_ADD_30, 0))
            builder.addAction(0, "Skip rest", action(ACTION_SKIP_REST, 0))
        }
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun openWorkout(sessionId: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            210,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_WORKOUT, sessionId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun action(name: String, setId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            name.hashCode(),
            Intent(context, WorkoutActionReceiver::class.java).setAction(name).putExtra(EXTRA_SET, setId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val CHANNEL_ID = "ironlog_workout"
        const val NOTIFICATION_ID = 7402
        const val ACTION_COMPLETE = "app.ironlog.personal.workout.COMPLETE"
        const val ACTION_ADD_30 = "app.ironlog.personal.workout.ADD_30"
        const val ACTION_SKIP_REST = "app.ironlog.personal.workout.SKIP_REST"
        const val EXTRA_SET = "set"
    }
}

/** Handles the notification buttons; the notifier redraws itself from the database change. */
class WorkoutActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val c = (context.applicationContext as IronlogApp).container
        val done = goAsync()
        c.appScope.launch {
            try {
                when (intent?.action) {
                    WorkoutNotifier.ACTION_COMPLETE -> complete(c, intent.getLongExtra(WorkoutNotifier.EXTRA_SET, -1))
                    WorkoutNotifier.ACTION_ADD_30 -> c.restTimer.adjust(30)
                    WorkoutNotifier.ACTION_SKIP_REST -> c.restTimer.skip()
                }
            } finally {
                done.finish()
            }
        }
    }

    private suspend fun complete(c: app.ironlog.personal.AppContainer, setId: Long) {
        val dao = c.dao()
        val set = dao.set(setId)?.takeIf { !it.isCompleted } ?: return
        val session = dao.activeSession().first() ?: return
        val rows = dao.sessionExercisesOnce(session.id)
        val row = rows.firstOrNull { it.id == set.sessionExerciseId } ?: return
        val next = WorkoutFlow.resolve(dao, row, set, dao.setsOnce(row.id))
        c.workouts.completeWithFallback(set.id, next.weightKg, next.reps)
        if (WorkoutFlow.restsAfter(row, rows)) c.restTimer.start(session.id, row.restSeconds) else c.restTimer.skip()
    }
}
