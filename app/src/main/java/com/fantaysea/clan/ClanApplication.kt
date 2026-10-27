package com.fantaysea.clan

import android.app.Application
import com.fantaysea.clan.data.AndroidDeviceCredentialStore
import com.fantaysea.clan.data.repository.ClanRepository
import com.fantaysea.clan.data.repository.RealClanRepository

/**
 * 进程级依赖容器。
 *
 * WebSocket 连接与设备凭证需要跨 Activity 重建存活；放在 Application 中可避免
 * 旋转屏幕后旧 ViewModel 持有已关闭仓库、或新旧连接互相替换的问题。
 */
class ClanApplication : Application() {
    val repository: ClanRepository by lazy {
        RealClanRepository.create(
            baseUrl = BuildConfig.CLAN_API_BASE_URL,
            credentialStore = AndroidDeviceCredentialStore(this),
        )
    }
}
