package com.fantaysea.clan.media

import java.util.concurrent.atomic.AtomicLong

/**
 * 控制端远端视频的可用状态。
 *
 * 控制端只有在 [Streaming] 时才能向被控端发送操作指令：
 * - [Idle]：没有控制端媒体会话（或被控端角色、会话已结束）；
 * - [WaitingFirstFrame]：媒体会话存在，但还没连通或尚未收到任何视频帧；
 * - [Streaming]：最近 [WebRtcConfig.REMOTE_VIDEO_STALL_TIMEOUT_MS] 内收到过视频帧；
 * - [Stalled]：曾经收到帧，但超过阈值没有新帧，画面可能已经冻结。
 */
enum class RemoteVideoState {
    Idle,
    WaitingFirstFrame,
    Streaming,
    Stalled,
}

/**
 * 远端视频健康判定的纯逻辑部分。
 *
 * 输入只有“最近一帧的时间戳”和“媒体是否连通”，方便在 JVM 单测里用可控时间验证；
 * 真正的 StateFlow 与看门狗仍由 [WebRtcManager] 持有。
 *
 * 帧回调来自 WebRTC 解码线程，看门狗来自 Default 协程，因此用 [AtomicLong] 保证可见性。
 */
class RemoteVideoHealthTracker(
    private val stallTimeoutMs: Long = WebRtcConfig.REMOTE_VIDEO_STALL_TIMEOUT_MS,
) {
    private val lastFrameAtMs = AtomicLong(NO_FRAME)

    fun reset() {
        lastFrameAtMs.set(NO_FRAME)
    }

    /** 收到一帧；[nowMs] 由调用方提供，便于单测。 */
    fun onFrame(nowMs: Long) {
        lastFrameAtMs.set(nowMs)
    }

    /**
     * 根据当前会话/媒体状态推导视频可用状态。
     *
     * - 没有会话 → [RemoteVideoState.Idle]；
     * - 会话存在但媒体未连通 → [RemoteVideoState.WaitingFirstFrame]；
     * - 尚未收到帧 → [RemoteVideoState.WaitingFirstFrame]；
     * - 距最近帧超过 [stallTimeoutMs] → [RemoteVideoState.Stalled]；
     * - 否则 → [RemoteVideoState.Streaming]。
     */
    fun evaluate(
        nowMs: Long,
        hasActiveSession: Boolean,
        mediaConnected: Boolean,
    ): RemoteVideoState {
        if (!hasActiveSession) return RemoteVideoState.Idle
        if (!mediaConnected) return RemoteVideoState.WaitingFirstFrame
        val last = lastFrameAtMs.get()
        if (last == NO_FRAME) return RemoteVideoState.WaitingFirstFrame
        return if (nowMs - last > stallTimeoutMs) {
            RemoteVideoState.Stalled
        } else {
            RemoteVideoState.Streaming
        }
    }

    private companion object {
        /** elapsedRealtime 不会为负，用 -1 表示“还没收到过帧”。 */
        const val NO_FRAME = -1L
    }
}
