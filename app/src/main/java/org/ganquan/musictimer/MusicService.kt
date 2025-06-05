package org.ganquan.musictimer

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri

// 通知通道与通知 ID
private const val CHANNEL_ID = "music_play_channel"
private const val NOTIF_ID  = 1001
// 通知动作字符串，用于区分点击事件
private const val ACTION_PLAY  = "ACTION_PLAY"
private const val ACTION_PAUSE = "ACTION_PAUSE"
private const val ACTION_STOP  = "ACTION_STOP"
class MusicService : Service() {
    private var mediaPlayer: MediaPlayer = MediaPlayer()
    private var isPlaying = false

    override fun onCreate() {
        super.onCreate()
        // 1. 创建通知渠道（Android 8.0+）
        createNotificationChannel()
        // 2. 初始化 MediaPlayer
        initMediaPlayer()
    }

    /**
     * 在 startService/startForegroundService 后回调
     */
    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent:Intent?, flags: Int, startId: Int): Int {
        // 区分点击通知的 Action
        if (intent != null && intent.action != null) {
            when (intent.action) {
                ACTION_PLAY -> resumeMusic()
                ACTION_PAUSE -> pauseMusic()
                ACTION_STOP -> {
                    stopSelf() // 停止 Service
                    return START_NOT_STICKY
                }
            }
        } else {
            // 首次启动，直接开始播放
            startMusic()
        }

        // 每次 onStartCommand 都需要调用 startForeground，保持前台状态
        startForeground(NOTIF_ID, buildNotification())
        // 如果被系统杀掉，不再自动重启
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.stop()
        mediaPlayer.release()
        // 取消前台状态与通知
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /**
     * 初始化 MediaPlayer，并设置音频属性
     */
    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer.create(this,
            "${Environment.getExternalStorageDirectory()}/${Environment.DIRECTORY_MUSIC}/音乐定时器/因着信Part007.mp3".toUri()
        )
        mediaPlayer.isLooping = true
        mediaPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        )
    }

    /** 开始播放并更新状态 */
    private fun startMusic() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
            isPlaying = true
        }
    }

    /** 暂停播放并更新状态 */
    private fun pauseMusic() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            isPlaying = false
        }
    }

    /** 恢复播放并更新状态 */
    private fun resumeMusic() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
            isPlaying = true
        }
    }

    /** 构建前台服务通知，包含播放/暂停/停止按钮 */
    private fun buildNotification(): Notification {
        // 点击通知主体，返回 MainActivity
        val mainIntent:PendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 播放/暂停按钮 Intent
        val playPauseIntent:Intent = Intent(this, MusicService::class.java)
            .setAction(if(isPlaying) ACTION_PAUSE else ACTION_PLAY)
        val ppPending: PendingIntent = PendingIntent.getService(
            this, 1, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 停止按钮 Intent
        val stopIntent:Intent = Intent(this, MusicService::class.java)
            .setAction(ACTION_STOP)
        val stopPending:PendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 构造 Notification
        val builder:NotificationCompat.Builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("正在播放音乐")
            .setContentText(if(isPlaying) "点击暂停" else "点击播放")
            .setSmallIcon(R.drawable.logo)
            .setContentIntent(mainIntent) // 点击通知主体
            .addAction(if(isPlaying) R.drawable.logo else R.drawable.logo,
                        if(isPlaying) "暂停" else "播放", ppPending)
            .addAction(R.drawable.logo, "停止", stopPending)
            // 使用 MediaStyle，支持在锁屏及车载展示
//            .setStyle(Notification.MediaStyle().setShowActionsInCompactView(0,1) as NotificationCompat.Style?)

        return builder.build()
    }

    /** 创建通知渠道（Android 8.0+） */
    private fun createNotificationChannel() {
        val chan = NotificationChannel(
            CHANNEL_ID,
            "音乐播放服务",
            NotificationManager.IMPORTANCE_LOW
        )
        chan.description = "用于在后台播放音乐的前台服务"
        val mgr:NotificationManager = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(chan)
    }
}