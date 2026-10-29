package com.fantaysea.clan.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fantaysea.clan.ClanApplication
import com.fantaysea.clan.MainActivity
import com.fantaysea.clan.R

/**
 * 屏幕捕获前台服务。
 *
 * Android 14+ 要求：应用必须先以 `mediaProjection` 类型进入前台，才能调用
 * `MediaProjectionManager.getMediaProjection()`。因此本服务只做三件事：
 * 1. 立刻 `startForeground`（通知渠道在 onCreate 建好）；
 * 2. 通知 [com.fantaysea.clan.media.WebRtcManager] 创建 MediaProjection / PeerConnection；
 * 3. 服务被系统回收时回调 manager 收敛会话。
 *
 * 采集本身由 manager 持有的 `ScreenCapturerAndroid` 完成，服务不直接持有 VideoTrack。
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ClanScreenCapture"
        private const val EXTRA_SESSION_ID = "extra_session_id"
        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, sessionId: String) {
            val intent = Intent(context, ScreenCaptureService::class.java)
                .putExtra(EXTRA_SESSION_ID, sessionId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenCaptureService::class.java))
        }
    }

    private var sessionId: String? = null
    private var foregroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val activeSessionId = intent?.getStringExtra(EXTRA_SESSION_ID)?.takeIf { it.isNotBlank() }
        if (activeSessionId == null) {
            Log.w(TAG, "missing session id, stop service")
            stopSelf()
            return START_NOT_STICKY
        }
        if (sessionId == activeSessionId && foregroundStarted) {
            return START_NOT_STICKY
        }
        sessionId = activeSessionId

        return try {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
            foregroundStarted = true
            // 会话可能已在 startForeground 期间被结束/替换：不要留下无主的前台通知。
            if (!manager().onCaptureServiceStarted(activeSessionId)) {
                stopSelf()
            }
            START_NOT_STICKY
        } catch (error: Exception) {
            // 常见原因：后台启动受限、通知渠道/权限异常、服务类型未声明。
            Log.e(TAG, "startForeground failed", error)
            manager().onCaptureServiceDestroyed(activeSessionId, "无法启动屏幕共享服务")
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        val activeSessionId = sessionId
        sessionId = null
        foregroundStarted = false
        if (activeSessionId != null) {
            manager().onCaptureServiceDestroyed(activeSessionId, "屏幕共享服务已停止")
        }
        super.onDestroy()
    }

    private fun manager() = (application as ClanApplication).webRtcManager

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.screen_capture_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.screen_capture_notification_text)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_screen_share)
            .setContentTitle(getString(R.string.screen_capture_notification_title))
            .setContentText(getString(R.string.screen_capture_notification_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
