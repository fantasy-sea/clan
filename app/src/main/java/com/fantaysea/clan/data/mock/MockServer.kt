package com.fantaysea.clan.data.mock

import com.fantaysea.clan.data.PairingCodeGenerator
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.model.PairingCode
import com.fantaysea.clan.data.repository.ClanRepository
import com.fantaysea.clan.data.repository.DeviceOfflineException
import com.fantaysea.clan.data.repository.ExpiredPairingCodeException
import com.fantaysea.clan.data.repository.InvalidPairingCodeException
import com.fantaysea.clan.data.repository.PairedDevice
import com.fantaysea.clan.data.repository.RemoteEvent
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 本地内存 Mock「服务端」, 实现 [ClanRepository]。
 *
 * 用途: 在真实后端/WebSocket 就绪前, 单机驱动「远程控制 + 共享屏幕」完整闭环。
 *
 * 单机演示的角色约定(重要):
 * - 本机([self]) = 控制端 + 被控端二合一(同一台手机扮演两端);
 * - 种子 2 台示例设备用于展示设备列表 / 在线离线 / 左滑删除;
 * - 「添加设备」提交匹配码时, 虚构一台远端「家人的手机」作为配对发起方,
 *   确认配对后该设备加入本机列表 —— 直观体现「配对成功后列表新增设备」。
 *
 * 真实双机部署下, requester/controller 是对方真实设备, 由 WebSocket 信令送达。
 */
class MockServer(
    private val delayMillis: Long = DEFAULT_DELAY,
) : ClanRepository {

    companion object {
        const val DEFAULT_DELAY = 300L
        const val SELF_ID = 100_000L
        /** 全局单例, 供 UI 默认注入 */
        val instance: ClanRepository by lazy { MockServer() }
    }

    private val now: Long get() = System.currentTimeMillis()

    private val _self = MutableStateFlow<Device?>(null)
    override val self: StateFlow<Device?> = _self.asStateFlow()

    private val _events = MutableSharedFlow<RemoteEvent>(replay = 64, extraBufferCapacity = 16)
    override val events: SharedFlow<RemoteEvent> = _events.asSharedFlow()

    // 已配对设备(本机视角)
    private val devices = mutableStateListOf(seedDevices())
    // 在线设备 id(模拟心跳)
    private val onlineIds = mutableSetOf(1L)
    // 当前生效的配对码(被控端=本机生成)
    private var activeCode: PairingCode? = null
    // 待应答的配对/控制请求
    private data class MatchCtx(val code: String, val requester: Device, val expiresAt: Long)
    private data class ControlCtx(val controller: Device, val target: Device)
    private val pendingMatches = mutableMapOf<String, MatchCtx>()
    private val pendingControls = mutableMapOf<String, ControlCtx>()

    private val idSeq = AtomicInteger(100)
    private val peerSeq = AtomicInteger(0)

    private fun nextId(): Long = idSeq.incrementAndGet().toLong()
    private fun newRequestId(): String = UUID.randomUUID().toString()
    private suspend fun simulateLatency() { if (delayMillis > 0) delay(delayMillis) }

    private fun seedDevices(): List<Device> = listOf(
        Device(
            id = 1L,
            deviceToken = "demo-mom-001",
            deviceName = "妈妈的手机",
            platform = "Android",
            model = "HUAWEI Mate 40",
            lastOnlineAt = now,
            createdAt = now - 30L * 24 * 3600_000,
        ),
        Device(
            id = 2L,
            deviceToken = "demo-dad-002",
            deviceName = "爸爸的手机",
            platform = "Android",
            model = "Xiaomi 12",
            lastOnlineAt = now - 3L * 3600_000,
            createdAt = now - 30L * 24 * 3600_000,
        ),
    )

    /** 虚构一台远端设备, 代表「另一台手机」 */
    private fun fabricatePeer(): Device {
        val n = peerSeq.incrementAndGet()
        return Device(
            id = nextId(),
            deviceToken = "demo-peer-$n",
            deviceName = if (n == 1) "家人的手机" else "家人的手机·$n",
            platform = "Android",
            model = "远程设备",
            lastOnlineAt = now,
            createdAt = now,
        )
    }

    override suspend fun registerSelf(deviceToken: String, deviceName: String, platform: String, model: String) {
        simulateLatency()
        _self.value = Device(
            id = SELF_ID,
            deviceToken = deviceToken,
            deviceName = deviceName,
            platform = platform,
            model = model,
            lastOnlineAt = now,
            createdAt = now,
        )
    }

    override suspend fun fetchDevices(): List<PairedDevice> {
        simulateLatency()
        return devices.map { PairedDevice(it, it.id in onlineIds) }
    }

    override suspend fun unbindDevice(deviceId: Long) {
        simulateLatency()
        devices.removeAll { it.id == deviceId }
        onlineIds.remove(deviceId)
    }

    override suspend fun createPairingCode(): PairingCode {
        simulateLatency()
        val existing = activeCode
        var code = PairingCodeGenerator.generate()
        // 若上一个码仍未过期, 避免重复
        while (existing != null && !PairingCodeGenerator.isExpired(existing.expiresAt, now) && existing.code == code) {
            code = PairingCodeGenerator.generate()
        }
        val created = PairingCode(
            id = nextId(),
            code = code,
            deviceId = _self.value?.id ?: SELF_ID,
            expiresAt = now + PairingCodeGenerator.VALIDITY_MS,
            used = false,
            createdAt = now,
        )
        activeCode = created
        return created
    }

    override suspend fun submitPairingCode(code: String): String {
        simulateLatency()
        if (!PairingCodeGenerator.isValid(code)) {
            throw InvalidPairingCodeException("配对码错误，请重新输入")
        }
        val current = activeCode
        if (current == null || current.used || current.code != code) {
            throw InvalidPairingCodeException("配对码错误，请重新输入")
        }
        if (PairingCodeGenerator.isExpired(current.expiresAt, now)) {
            activeCode = null
            throw ExpiredPairingCodeException("配对码已过期，请重新获取")
        }
        activeCode = current.copy(used = true)
        val requestId = newRequestId()
        val requester = fabricatePeer()
        pendingMatches[requestId] = MatchCtx(code, requester, current.expiresAt)
        _events.tryEmit(
            RemoteEvent.MatchRequest(requestId = requestId, code = code, requester = requester),
        )
        return requestId
    }

    override suspend fun respondMatch(requestId: String, accept: Boolean) {
        simulateLatency()
        val ctx = pendingMatches.remove(requestId) ?: return
        if (!accept) {
            _events.tryEmit(RemoteEvent.MatchResult(requestId, accepted = false, pairedDevice = null))
            return
        }
        val peer = ctx.requester
        devices += peer
        onlineIds += peer.id
        _events.tryEmit(RemoteEvent.MatchResult(requestId, accepted = true, pairedDevice = peer))
        _events.tryEmit(RemoteEvent.PairingSucceeded(requestId = requestId, pairedDevice = peer))
    }

    override suspend fun sendControlRequest(deviceId: Long): String {
        simulateLatency()
        val target = devices.firstOrNull { it.id == deviceId }
            ?: throw IllegalArgumentException("设备不存在")
        if (target.id !in onlineIds) {
            throw DeviceOfflineException("设备不在线，无法发起控制")
        }
        val controller = _self.value ?: throw IllegalStateException("本机身份未注册")
        val requestId = newRequestId()
        pendingControls[requestId] = ControlCtx(controller, target)
        _events.tryEmit(RemoteEvent.ControlRequest(requestId = requestId, controller = controller))
        return requestId
    }

    override suspend fun respondControl(requestId: String, accept: Boolean) {
        simulateLatency()
        val ctx = pendingControls.remove(requestId) ?: return
        if (!accept) {
            _events.tryEmit(RemoteEvent.ControlResult(requestId, accepted = false))
            return
        }
        _events.tryEmit(RemoteEvent.ControlResult(requestId, accepted = true))
        _events.tryEmit(
            RemoteEvent.SessionStarted(
                requestId = requestId,
                controller = ctx.controller,
                controlled = ctx.target,
            ),
        )
    }

    override suspend fun endSession(peerDeviceId: Long) {
        simulateLatency()
        val peer = devices.firstOrNull { it.id == peerDeviceId } ?: _self.value ?: return
        _events.tryEmit(RemoteEvent.SessionEnded(peer = peer))
    }
}

// 保持源码文件内聚: 可变列表辅助函数(避免额外 import 分散)
private fun <T> mutableStateListOf(initial: List<T>): MutableList<T> = initial.toMutableList()
