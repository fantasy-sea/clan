package com.fantaysea.clan.data.model

/**
 * 服务端 remote_sessions.status 的客户端映射。
 *
 * 状态机：REQUESTING -> CONNECTING -> CONNECTED -> ENDED，另有 REJECTED / FAILED 终态。
 */
enum class SessionStatus {
    REQUESTING,
    CONNECTING,
    CONNECTED,
    REJECTED,
    ENDED,
    FAILED;

    val isActive: Boolean
        get() = this == REQUESTING || this == CONNECTING || this == CONNECTED

    companion object {
        fun fromWire(value: String): SessionStatus? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

/** 本机在一次会话中的角色。角色属于会话，不属于设备。 */
enum class SessionRole { CONTROLLER, CONTROLLED }

/**
 * `POST /api/v1/sessions` 与 `GET /api/v1/sessions/{id}` 的领域模型。
 * 时间统一转为 epoch millis，解析失败时为 null（UI 只做展示，不参与权限判断）。
 */
data class SessionInfo(
    val sessionId: String,
    val controllerDeviceId: String,
    val controlledDeviceId: String,
    val status: SessionStatus,
    val expiresAt: Long?,
    val createdAt: Long?,
    val acceptedAt: Long? = null,
    val connectExpiresAt: Long? = null,
    val connectedAt: Long? = null,
    val rejectedAt: Long? = null,
    val endedAt: Long? = null,
    val failedAt: Long? = null,
    val failureReason: String? = null,
) {
    fun peerId(selfDeviceId: String): String? = when (selfDeviceId) {
        controllerDeviceId -> controlledDeviceId
        controlledDeviceId -> controllerDeviceId
        else -> null
    }
}

/**
 * 当前本机参与的会话。仓库收到 SESSION_STARTED / SESSION_CONNECTED 时维护，
 * 会话结束后清空；UI 通过它取 sessionId、角色和对端设备。
 */
data class ActiveSession(
    val info: SessionInfo,
    val peer: Device,
    val role: SessionRole,
)

/** 被控端本机权限状态，用于向控制端回传 PERMISSION_STATUS。 */
data class LocalPermissions(
    val screenCapture: Boolean = false,
    val accessibility: Boolean = false,
)

/** Trickle ICE 候选；candidate 允许空串表示 end-of-candidates。 */
data class IceCandidate(
    val candidate: String,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val usernameFragment: String? = null,
)
