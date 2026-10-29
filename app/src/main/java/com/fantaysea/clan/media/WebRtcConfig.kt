package com.fantaysea.clan.media

import com.fantaysea.clan.BuildConfig
import org.webrtc.PeerConnection

/**
 * 单个 ICE server 的纯数据描述。
 *
 * 刻意不直接依赖 [PeerConnection.IceServer]：STUN/TURN 的解析规则可以脱离 WebRTC native 库
 * 做 JVM 单测，只有真正创建 PeerConnection 时才转换。
 */
data class IceServerSpec(
    val urls: List<String>,
    val username: String = "",
    val password: String = "",
)

/**
 * WebRTC 媒体参数与 ICE 配置。
 *
 * STUN/TURN 通过 Gradle 属性注入到 BuildConfig：
 * - `CLAN_STUN_URLS`：逗号分隔的 STUN URL，默认一个公共 STUN；
 * - `CLAN_TURN_URL` / `CLAN_TURN_USERNAME` / `CLAN_TURN_PASSWORD`：TURN 地址与长期凭据，
 *   URL、用户名、密码三者齐全时才启用。
 *
 * 服务端不参与 TURN 部署，也不下发凭据。
 */
object WebRtcConfig {

    /** 屏幕采集帧率。屏幕内容不需要高帧率，30fps 足够且省电。 */
    const val CAPTURE_FPS = 30

    /** 采集长边上限；720p 级别已能看清手机 UI，同时降低编码压力。 */
    const val MAX_CAPTURE_LONG_SIDE = 1280

    /** 屏幕共享的码率上下限；控制端只收不发，因此只在发送侧生效。 */
    const val MIN_VIDEO_BITRATE_BPS = 200_000
    const val START_VIDEO_BITRATE_BPS = 1_500_000
    const val MAX_VIDEO_BITRATE_BPS = 3_000_000

    /** PC 进入 DISCONNECTED 后等待恢复的时间，超过后结束媒体会话。 */
    const val RECONNECT_GRACE_MS = 15_000L

    /**
     * 控制端远端画面健康判定。
     *
     * 只有“已收到首帧且最近仍有新帧”才允许向被控端发送操作；超过该时间没有新帧就视为
     * 画面停滞并锁定操作，避免用户对着已经冻结的画面继续点击。
     */
    const val REMOTE_VIDEO_STALL_TIMEOUT_MS = 5_000L
    const val REMOTE_VIDEO_HEALTH_CHECK_INTERVAL_MS = 1_000L

    /**
     * 等待对端 SDP 协商的超时时间。
     *
     * 控制端要等被控用户接受控制并授权 MediaProjection，因此留 90 秒；
     * 被控端在采集就绪后才开始计时，此时控制端的 offer 通常已经在信令缓冲区里，
     * 20 秒收不到基本可判定对端仍是未接入 WebRTC 的旧版本。
     */
    const val CONTROLLER_ANSWER_TIMEOUT_MS = 90_000L
    const val CONTROLLED_OFFER_TIMEOUT_MS = 20_000L

    /** 未进入会话前到达的信令缓存时间（与服务端 120s 连接截止对齐）与最多缓存的会话数。 */
    const val SIGNAL_TTL_MS = 120_000L
    const val SIGNAL_MAX_SESSIONS = 4

    /** 运行时构造的 ICE servers；未配置 STUN/TURN 时为空列表（局域网 host 候选仍可用）。 */
    val iceServers: List<PeerConnection.IceServer> by lazy {
        specToIceServers(
            parseIceServers(
                stunUrls = BuildConfig.CLAN_STUN_URLS,
                turnUrl = BuildConfig.CLAN_TURN_URL,
                turnUsername = BuildConfig.CLAN_TURN_USERNAME,
                turnPassword = BuildConfig.CLAN_TURN_PASSWORD,
            ),
        )
    }

    /**
     * 把 Gradle 属性字符串解析为 ICE server 列表。
     * STUN 允许逗号分隔多个地址；TURN 只接受一条，且必须同时提供用户名和密码。
     */
    fun parseIceServers(
        stunUrls: String,
        turnUrl: String,
        turnUsername: String,
        turnPassword: String,
    ): List<IceServerSpec> = buildList {
        val stuns = stunUrls.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (stuns.isNotEmpty()) add(IceServerSpec(urls = stuns))

        val turn = turnUrl.trim()
        val user = turnUsername.trim()
        if (turn.isNotEmpty() && user.isNotEmpty() && turnPassword.isNotEmpty()) {
            add(IceServerSpec(urls = listOf(turn), username = user, password = turnPassword))
        }
    }

    private fun specToIceServers(specs: List<IceServerSpec>): List<PeerConnection.IceServer> =
        specs.map { spec ->
            PeerConnection.IceServer.builder(spec.urls)
                .setUsername(spec.username)
                .setPassword(spec.password)
                .createIceServer()
        }
}
