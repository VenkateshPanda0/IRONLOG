package app.ironlog.personal.data.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.units.Mass
import app.ironlog.personal.domain.CardioType
import java.time.Instant
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

enum class HealthStatus { AVAILABLE, NEEDS_INSTALL, UNSUPPORTED }

/** Read-only access to steps and sleep in Health Connect. Nothing is written back. */
class HealthConnect(private val context: Context) : HealthSource {
    private val byAccess: Map<HealthAccess, Set<String>> =
        mapOf(
            HealthAccess.STEPS_SLEEP to setOf(HealthPermission.getReadPermission(StepsRecord::class), HealthPermission.getReadPermission(SleepSessionRecord::class)),
            HealthAccess.READ_WEIGHT to setOf(HealthPermission.getReadPermission(WeightRecord::class)),
            HealthAccess.WRITE_WEIGHT to setOf(HealthPermission.getWritePermission(WeightRecord::class)),
            HealthAccess.READ_EXERCISE to setOf(HealthPermission.getReadPermission(ExerciseSessionRecord::class)),
            HealthAccess.WRITE_EXERCISE to setOf(HealthPermission.getWritePermission(ExerciseSessionRecord::class)),
            HealthAccess.READ_DISTANCE to setOf(HealthPermission.getReadPermission(DistanceRecord::class)),
        )

    /** Everything Ironlog can use; the user may grant any subset. */
    val permissions = byAccess.values.flatten().toSet()

    fun accessFor(grantedPermissions: Set<String>): Set<HealthAccess> = byAccess.filterValues { grantedPermissions.containsAll(it) }.keys

    override suspend fun granted(): Set<HealthAccess> =
        if (status() != HealthStatus.AVAILABLE) emptySet()
        else runCatching { accessFor(client.permissionController.getGrantedPermissions()) }.getOrDefault(emptySet())

    fun status(): HealthStatus =
        when (runCatching { HealthConnectClient.getSdkStatus(context, PROVIDER) }.getOrNull()) {
            HealthConnectClient.SDK_AVAILABLE -> HealthStatus.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthStatus.NEEDS_INSTALL
            else -> HealthStatus.UNSUPPORTED
        }

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    /** True when at least one kind of data may be synced. */
    suspend fun hasPermissions(): Boolean = granted().isNotEmpty()

    private fun notMine(origin: String) = origin != context.packageName

    override suspend fun readWeights(from: Instant, to: Instant): List<HealthWeight> =
        readAll(WeightRecord::class, TimeRangeFilter.between(from, to))
            .filter { notMine(it.metadata.dataOrigin.packageName) }
            .map { HealthWeight(it.metadata.id, it.time, it.weight.inKilograms) }

    override suspend fun readSessions(from: Instant, to: Instant, withDistance: Boolean): List<HealthSession> =
        readAll(ExerciseSessionRecord::class, TimeRangeFilter.between(from, to))
            .filter { notMine(it.metadata.dataOrigin.packageName) }
            .map { r ->
                val type = CARDIO_IN[r.exerciseType] ?: if (r.exerciseType in SPORTS) CardioType.SPORT else null
                val km =
                    if (withDistance && type?.hasDistance == true) {
                        runCatching {
                            client.aggregate(AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), TimeRangeFilter.between(r.startTime, r.endTime)))[DistanceRecord.DISTANCE_TOTAL]?.inKilometers
                        }.getOrNull()
                    } else null
                HealthSession(r.metadata.id, r.startTime, r.endTime, type, r.title, km)
            }

    override suspend fun writeWeights(items: List<OutWeight>) {
        if (items.isEmpty()) return
        items.chunked(500).forEach { chunk ->
            client.insertRecords(
                chunk.map { w ->
                    WeightRecord(
                        time = w.time,
                        zoneOffset = ZoneId.systemDefault().rules.getOffset(w.time),
                        weight = Mass.kilograms(w.kg),
                        metadata = Metadata.manualEntry(clientRecordId = w.clientId, clientRecordVersion = w.version),
                    )
                }
            )
        }
    }

    override suspend fun writeSessions(items: List<OutSession>) {
        if (items.isEmpty()) return
        val zone = ZoneId.systemDefault()
        items.chunked(500).forEach { chunk ->
            client.insertRecords(
                chunk.map { o ->
                    ExerciseSessionRecord(
                        startTime = o.start,
                        startZoneOffset = zone.rules.getOffset(o.start),
                        endTime = o.end,
                        endZoneOffset = zone.rules.getOffset(o.end),
                        exerciseType = o.type?.let { CARDIO_OUT[it] } ?: ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                        title = o.title,
                        notes = o.notes.ifBlank { null },
                        metadata = Metadata.manualEntry(clientRecordId = o.clientId, clientRecordVersion = o.version),
                    )
                }
            )
        }
    }

    override suspend fun deleteWeights(clientIds: List<String>) {
        if (clientIds.isNotEmpty()) client.deleteRecords(WeightRecord::class, emptyList(), clientIds)
    }

    override suspend fun deleteSessions(clientIds: List<String>) {
        if (clientIds.isNotEmpty()) client.deleteRecords(ExerciseSessionRecord::class, emptyList(), clientIds)
    }

    private suspend fun <T : Record> readAll(type: kotlin.reflect.KClass<T>, range: TimeRangeFilter): List<T> {
        val out = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(ReadRecordsRequest(type, range, pageToken = pageToken))
            out += response.records
            pageToken = response.pageToken
        } while (pageToken != null)
        return out
    }

    override suspend fun read(from: LocalDate, to: LocalDate): List<HealthDay> {
        val zone = ZoneId.systemDefault()
        val steps =
            client
                .aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()),
                        timeRangeSlicer = Period.ofDays(1),
                    )
                )
                // The aggregate removes double counting between the phone and a watch.
                .associate { it.startTime.toLocalDate() to it.result[StepsRecord.COUNT_TOTAL]?.toInt() }
        // Sleep that ended on these days may have started the evening before.
        val sessions = mutableListOf<SleepSessionRecord>()
        var pageToken: String? = null
        do {
            val response =
                client.readRecords(
                    ReadRecordsRequest(
                        SleepSessionRecord::class,
                        TimeRangeFilter.between(from.minusDays(1).atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant()),
                        pageToken = pageToken,
                    )
                )
            sessions += response.records
            pageToken = response.pageToken
        } while (pageToken != null)
        val awakeStages = setOf(SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED)
        val sleep =
            HealthMerge.sleepByDate(
                sessions.map { s ->
                    SleepSpan(s.startTime, s.endTime, s.stages.filter { it.stage in awakeStages }.fold(Duration.ZERO) { acc, st -> acc + Duration.between(st.startTime, st.endTime) })
                },
                zone,
            )
        return (steps.keys + sleep.keys).filter { it in from..to }.sorted().map { HealthDay(it, steps[it], sleep[it]) }
    }

    /** Opens Health Connect's own screen where the user reviews or revokes Ironlog's access. */
    fun manageIntent(): Intent = HealthConnectClient.getHealthConnectManageDataIntent(context, PROVIDER)

    /** Play Store page for installing or updating Health Connect on Android 13 and older. */
    fun installIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding"))
            .setPackage("com.android.vending")
            .putExtra("overlay", true)
            .putExtra("callerId", context.packageName)

    companion object {
        const val PROVIDER = "com.google.android.apps.healthdata"

        private val CARDIO_OUT =
            mapOf(
                CardioType.RUN to ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
                CardioType.WALK to ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
                CardioType.CYCLE to ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
                CardioType.SWIM to ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
                CardioType.ROW to ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
                CardioType.HIKE to ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
                CardioType.HIIT to ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
                CardioType.SPORT to ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
                CardioType.MOBILITY to ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING,
                CardioType.OTHER to ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
            )

        /** Kinds imported as cardio. Other apps' strength sessions are skipped: they carry no sets. */
        private val CARDIO_IN =
            mapOf(
                ExerciseSessionRecord.EXERCISE_TYPE_RUNNING to CardioType.RUN,
                ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL to CardioType.RUN,
                ExerciseSessionRecord.EXERCISE_TYPE_WALKING to CardioType.WALK,
                ExerciseSessionRecord.EXERCISE_TYPE_BIKING to CardioType.CYCLE,
                ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY to CardioType.CYCLE,
                ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL to CardioType.SWIM,
                ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER to CardioType.SWIM,
                ExerciseSessionRecord.EXERCISE_TYPE_ROWING to CardioType.ROW,
                ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE to CardioType.ROW,
                ExerciseSessionRecord.EXERCISE_TYPE_HIKING to CardioType.HIKE,
                ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING to CardioType.HIIT,
                ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING to CardioType.MOBILITY,
                ExerciseSessionRecord.EXERCISE_TYPE_YOGA to CardioType.MOBILITY,
                ExerciseSessionRecord.EXERCISE_TYPE_PILATES to CardioType.MOBILITY,
                ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL to CardioType.OTHER,
                ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING to CardioType.OTHER,
                ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE to CardioType.OTHER,
            )

        private val SPORTS =
            setOf(
                ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON, ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL, ExerciseSessionRecord.EXERCISE_TYPE_CRICKET,
                ExerciseSessionRecord.EXERCISE_TYPE_SOCCER, ExerciseSessionRecord.EXERCISE_TYPE_TENNIS, ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS,
                ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL, ExerciseSessionRecord.EXERCISE_TYPE_SQUASH, ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS,
                ExerciseSessionRecord.EXERCISE_TYPE_BOXING, ExerciseSessionRecord.EXERCISE_TYPE_DANCING, ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN,
                ExerciseSessionRecord.EXERCISE_TYPE_RUGBY, ExerciseSessionRecord.EXERCISE_TYPE_HANDBALL,
            )

        fun permissionContract() = PermissionController.createRequestPermissionResultContract()
    }
}
