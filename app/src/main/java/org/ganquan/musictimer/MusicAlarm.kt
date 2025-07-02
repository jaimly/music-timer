package org.ganquan.musictimer

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import org.ganquan.musictimer.tools.Broadcast
import org.ganquan.musictimer.tools.Music
import org.ganquan.musictimer.tools.Utils

class MusicAlarm {
    private var activity: Context
    private var alarmManager: AlarmManager
    private lateinit var startPendingIntent: PendingIntent
    private lateinit var endPendingIntent: PendingIntent
    private var isRequest: Boolean = false

    constructor(activity1: Context) {
        activity = activity1
        alarmManager = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    @SuppressLint("ScheduleExactAlarm")
    fun request(
        playTime: Long,
        delayTime: Long = 1,
        folderPath: String = "${Environment.getExternalStorageDirectory()}/${Environment.DIRECTORY_MUSIC}",
        musicName: String = ""
    ) {
        isRequest = true

        val startIntent = Intent(activity, StartReceiver::class.java)
        startIntent.putExtra("FOLDER_PATH", folderPath)
        startIntent.putExtra("MUSIC_NAME", musicName)
        startPendingIntent = PendingIntent.getBroadcast(activity, 0, startIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, delayTime*1000, startPendingIntent)

        val endIntent = Intent(activity, EndReceiver::class.java)
        endPendingIntent = PendingIntent.getBroadcast(activity, 1, endIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, (delayTime + playTime) * 1000, endPendingIntent)
    }

    fun cancel() {
        if(!isRequest) return
        Music.stop()
        alarmManager.cancel(startPendingIntent)
        alarmManager.cancel(endPendingIntent)
    }
}

class StartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val folderPath: String = intent.getStringExtra("FOLDER_PATH").toString()
        val name: String = intent.getStringExtra("MUSIC_NAME").toString()
        val list = Utils.getFileList(folderPath)
        Music.playCircle(list, name)
        Broadcast.sendLocal(context, "start worker")
    }
}

class EndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Music.stop()
        Broadcast.sendLocal(context, "end worker")
    }
}
