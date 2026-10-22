package com.fantaysea.clan.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * 屏幕捕获前台服务(预留骨架, 尚未被启动)。
 *
 * 真实实现职责:
 * - 接收授权令牌(resultCode + resultData)后调用
 *   MediaProjectionManager.getMediaProjection() 建立 MediaProjection;
 * - 创建 VirtualDisplay 输出到 Surface(对接 WebRTC 视频编码);
 * - 以 mediaProjection 类型常驻前台(Android 14+ 要求)。
 *
 * 当前仅完成 Manifest 注册, 供后续投屏能力接入时直接填充。
 */
class ScreenCaptureService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val NOTIFICATION_CHANNEL = "screen_capture"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // TODO(真实实现): 建立前台通知 + MediaProjection + VirtualDisplay
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL).build()
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // TODO(真实实现): 释放 MediaProjection / VirtualDisplay
        super.onDestroy()
    }
}
