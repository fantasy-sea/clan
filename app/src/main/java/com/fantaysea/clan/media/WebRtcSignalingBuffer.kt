package com.fantaysea.clan.media

import com.fantaysea.clan.data.model.IceCandidate
import com.fantaysea.clan.data.repository.RemoteEvent

/**
 * 按 sessionId 缓冲 SDP/ICE 信令。
 *
 * 场景：控制端在 `SESSION_STARTED` 后立刻发 offer，而被控端会话页/前台服务/PeerConnection 可能
 * 还没就绪；或者 Activity 重建导致 manager 的 start 调用晚于信令到达。信令先落在这里，
 * PeerConnection 建好后再按到达顺序 drain。
 *
 * 纯 Kotlin、无 Android/WebRTC 依赖，便于单测。
 */
class WebRtcSignalBuffer(
    private val maxSessions: Int = WebRtcConfig.SIGNAL_MAX_SESSIONS,
    private val ttlMs: Long = WebRtcConfig.SIGNAL_TTL_MS,
) {
    private data class Entry(
        val events: MutableList<RemoteEvent.Signal>,
        var updatedAt: Long,
    )

    private val entries = LinkedHashMap<String, Entry>()

    /** 同一会话内保持到达顺序；超出会话数上限时淘汰最早创建的会话。 */
    @Synchronized
    fun put(
        sessionId: String,
        event: RemoteEvent.Signal,
        now: Long = System.currentTimeMillis(),
    ) {
        if (sessionId.isBlank()) return
        pruneLocked(now)
        val entry = entries.getOrPut(sessionId) { Entry(mutableListOf(), now) }
        entry.events += event
        entry.updatedAt = now
        while (entries.size > maxSessions.coerceAtLeast(1)) {
            val eldest = entries.keys.firstOrNull() ?: break
            entries.remove(eldest)
        }
    }

    /** 取出并清空一个会话的待处理信令。 */
    @Synchronized
    fun take(sessionId: String): List<RemoteEvent.Signal> =
        entries.remove(sessionId)?.events?.toList().orEmpty()

    @Synchronized
    fun clear(sessionId: String) {
        entries.remove(sessionId)
    }

    @Synchronized
    fun prune(now: Long = System.currentTimeMillis()) {
        pruneLocked(now)
    }

    @Synchronized
    fun bufferedCount(sessionId: String): Int = entries[sessionId]?.events?.size ?: 0

    private fun pruneLocked(now: Long) {
        entries.entries.removeAll { (_, entry) -> now - entry.updatedAt > ttlMs }
    }
}

/**
 * 远端 ICE 候选缓冲。
 *
 * 服务端按到达顺序转发，ICE 可能先于 offer/answer；客户端必须先把候选排队，等
 * `setRemoteDescription` 成功后再逐条 `addIceCandidate`。
 */
class IceCandidateBuffer {
    private val pending = mutableListOf<IceCandidate>()
    private var remoteDescriptionReady = false

    /** 返回 true 表示候选尚不能加入，已入队；false 表示 remote description 已就绪，应直接加入。 */
    @Synchronized
    fun add(candidate: IceCandidate): Boolean {
        if (remoteDescriptionReady) return false
        pending += candidate
        return true
    }

    /** 标记 remote description 已就绪，返回缓冲中的候选并清空。 */
    @Synchronized
    fun markRemoteDescriptionReady(): List<IceCandidate> {
        remoteDescriptionReady = true
        val queued = pending.toList()
        pending.clear()
        return queued
    }

    @Synchronized
    fun clear() {
        pending.clear()
        remoteDescriptionReady = false
    }

    @Synchronized
    fun bufferedCount(): Int = pending.size
}
