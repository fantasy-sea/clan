package com.fantaysea.clan.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.fantaysea.clan.media.WebRtcManager
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

/**
 * 控制端远端画面渲染器。
 *
 * - 渲染器在 AndroidView factory 中初始化：必须早于 Surface 创建，否则首帧可能丢失；
 * - 用 manager 的共享 EglBase context，避免额外的 GL 资源；
 * - 不在这里调用 addSink：由 [WebRtcManager] 在远端视频轨到达/渲染器绑定时统一管理；
 * - 离开组合时先解绑 sink 再 release，防止 GC 前访问已释放的 EGL 资源。
 */
@Composable
fun RemoteVideoSurface(
    manager: WebRtcManager,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val renderer = remember {
        SurfaceViewRenderer(context).apply {
            init(manager.eglBaseContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            setEnableHardwareScaler(true)
            setMirror(false)
        }
    }

    AndroidView(
        factory = {
            manager.bindRenderer(renderer)
            renderer
        },
        modifier = modifier,
    )

    DisposableEffect(renderer) {
        onDispose {
            manager.unbindRenderer(renderer)
            runCatching { renderer.release() }
        }
    }
}
