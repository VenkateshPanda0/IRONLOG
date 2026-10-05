package app.ironlog.personal.data.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
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
    val permissions =
        setOf(HealthPermission.getReadPermission(StepsRecord::class), HealthPermission.getReadPermission(SleepSessionRecord::class))

    fun status(): HealthStatus =
        when (runCatching { HealthConnectClient.getSdkStatus(context, PROVIDER) }.getOrNull()) {
            HealthConnectClient.SDK_AVAILABLE -> HealthStatus.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthStatus.NEEDS_INSTALL
            else -> HealthStatus.UNSUPPORTED
        }

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun hasPermissions(): Boolean =
        status() == HealthStatus.AVAILABLE && runCatching { client.permissionController.getGrantedPermissions().containsAll(permissions) }.getOrDefault(false)

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

        fun permissionContract() = PermissionController.createRequestPermissionResultContract()
    }
}
