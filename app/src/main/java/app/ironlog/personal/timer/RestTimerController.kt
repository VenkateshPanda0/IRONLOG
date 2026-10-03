package app.ironlog.personal.timer

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import app.ironlog.personal.R
import app.ironlog.personal.data.db.IronlogDao
import app.ironlog.personal.data.db.RestTimerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RestTimerController(private val context:Context,private val dao:IronlogDao) {
    suspend fun start(sessionId:Long,durationSec:Int=90) = withContext(Dispatchers.IO) {
        val end=System.currentTimeMillis()+durationSec.coerceAtLeast(1)*1000L
        dao.saveRestTimer(RestTimerEntity(sessionId=sessionId,endAtEpochMs=end,durationSec=durationSec,isRunning=true))
        schedule(end)
    }
    suspend fun adjust(seconds:Int) = withContext(Dispatchers.IO) {
        val timer=dao.restTimer() ?: return@withContext
        val end=timer.endAtEpochMs+seconds*1000L
        if(end<=System.currentTimeMillis()) skip() else { dao.saveRestTimer(timer.copy(endAtEpochMs=end)); schedule(end) }
    }
    suspend fun skip() = withContext(Dispatchers.IO) { cancel(); dao.clearRestTimer() }
    suspend fun remainingMs():Long=withContext(Dispatchers.IO) { ((dao.restTimer()?.endAtEpochMs ?: 0L)-System.currentTimeMillis()).coerceAtLeast(0L) }
    private fun schedule(endAt:Long) {
        val alarm=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,endAt,pendingIntent())
    }
    private fun cancel() {
        val alarm=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pendingIntent())
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(RestTimerReceiver.NOTIFICATION_ID)
    }
    private fun pendingIntent():PendingIntent {
        val intent=Intent(context,RestTimerReceiver::class.java)
        return PendingIntent.getBroadcast(context,73,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class RestTimerReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent?) {
        val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel=NotificationChannel(CHANNEL_ID,"Rest timer",NotificationManager.IMPORTANCE_DEFAULT).apply { description="Workout rest timer"; enableVibration(true) }
        manager.createNotificationChannel(channel)
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val notification=NotificationCompat.Builder(context,CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Rest complete")
            .setContentText("Your rest timer has finished.")
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(NOTIFICATION_ID,notification)
    }
    companion object { const val CHANNEL_ID="ironlog_rest_timer"; const val NOTIFICATION_ID=7401 }
}
