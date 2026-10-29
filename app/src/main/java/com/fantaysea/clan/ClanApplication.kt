package com.fantaysea.clan

import android.app.Application
import com.fantaysea.clan.data.AndroidDeviceCredentialStore
import com.fantaysea.clan.data.repository.ClanRepository
import com.fantaysea.clan.data.repository.RealClanRepository
import com.fantaysea.clan.media.ScreenCaptureManager
import com.fantaysea.clan.media.WebRtcManager

/**
 * 进程级依赖容器。
 *
 * WebSocket 连接、设备凭证和 WebRTC 媒体需要跨 Activity 重建存活；repository 放在 Application 中
 * 可避免旋转屏幕后旧 ViewModel 持有已关闭仓库、或新旧连接互相替换的问题。媒体管理器同理：
 * 旋转/重建时会话页只重新绑定渲染器，不能重建 PeerConnection。
 */
class ClanApplication : Application() {
    val repository: ClanRepository by lazy {
        RealClanRepository.create(
            baseUrl = BuildConfig.CLAN_API_BASE_URL,
            credentialStore = AndroidDeviceCredentialStore(this),
        )
    }

    /** 屏幕捕获授权与前台服务共享的进程级状态。 */
    val screenCaptureManager: ScreenCaptureManager by lazy { ScreenCaptureManager(this) }

    /** WebRTC 媒体链路；构造时即开始订阅仓库事件，避免页面进入前丢失 offer/ICE。 */
    val webRtcManager: WebRtcManager by lazy {
        WebRtcManager(this, repository, screenCaptureManager)
    }
}
