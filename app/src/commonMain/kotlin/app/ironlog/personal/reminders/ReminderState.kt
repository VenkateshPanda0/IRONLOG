package app.ironlog.personal.reminders

import app.ironlog.personal.AppContainer
import app.ironlog.personal.domain.ReminderState
import app.ironlog.personal.domain.Training
import app.ironlog.personal.time.ZoneId
import app.ironlog.personal.time.atZone
import app.ironlog.personal.time.now
import app.ironlog.personal.time.ofEpochMilli
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/** Reads today's training state from the database, for the reminder rules. */
suspend fun AppContainer.reminderState(today: LocalDate = LocalDate.now()): ReminderState {
    val zone = ZoneId.systemDefault()
    val profile = profile.first()
    val dates = workouts.history.first().map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
    val active = programs.active.first()
    val next = active?.let { runCatching { programs.nextDay(it.programId) }.getOrNull() }
    val exercises = next?.let { programs.prescriptions(it.id).first().size } ?: 0
    return ReminderState(
        today = today,
        trainingDays = Training.parseWeekdays(profile?.trainingWeekdays.orEmpty()),
        perWeek = profile?.daysPerWeek ?: 3,
        workoutDates = dates,
        workoutInProgress = workouts.active.first() != null,
        nextDayName = next?.name,
        nextDayExercises = exercises,
    )
}
