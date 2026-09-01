package com.fantaysea.clan.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager

/**
 * 屏幕捕获授权与投影管理(预留骨架)。
 *
 * MediaProjection 不是普通运行时权限: 每次调用 [createCaptureIntent] 都会弹系统授权框,
 * 回调里的 resultData 是「一次性」令牌, 必须在授权后尽快交给前台服务消费, 否则失效。
 *
 * 当前阶段:
 * - 只记录「本进程内已授权」状态以驱动 UI 流程(共享屏幕页的授权门控);
 * - 真正开始捕获(VirtualDisplay + ImageReader/Surface)由 [com.fantaysea.clan.service.ScreenCaptureService]
 *   消费 [resultData], 后续与 WebRTC 视频轨道对接, 此处留 TODO。
 *
 * 注意: 进程被杀后 [isGranted] 复位; Android 14+ 每次会话系统都会重新确认授权。
 */
class ScreenCaptureManager(private val context: Context) {

    private val projectionManager: MediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    /** 本进程内是否已获得授权 */
    var isGranted: Boolean = false
        private set

    var resultCode: Int = Activity.RESULT_CANCELED
        private set

    /** 系统返回的授权令牌(一次性, 需尽快消费) */
    var resultData: Intent? = null
        private set

    /** 已建立的投影(真实捕获开始后非空) */
    var mediaProjection: MediaProjection? = null
        private set

    /** 生成系统屏幕捕获授权 Intent */
    fun createCaptureIntent(): Intent = projectionManager.createScreenCaptureIntent()

    /** 授权回调结果处理 */
    fun onConsent(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            this.resultCode = resultCode
            this.resultData = data
            this.isGranted = true
        } else {
            this.isGranted = false
            this.resultData = null
        }
    }

    /**
     * 开始捕获。
     * TODO(真实实现): 启动 [com.fantaysea.clan.service.ScreenCaptureService],
     *  传入 resultData/resultCode, 由服务调用 projectionManager.getMediaProjection(...)
     *  并创建 VirtualDisplay 输出到 Surface。
     */
    fun startCapture(onStarted: (MediaProjection?) -> Unit = {}) {
        onStarted(mediaProjection)
    }

    /** 停止捕获并释放投影。 */
    fun stopCapture() {
        mediaProjection?.stop()
        mediaProjection = null
        isGranted = false
        resultData = null
    }
}
