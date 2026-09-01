package com.fantaysea.clan.data.remote

import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.model.DeviceBinding
import com.fantaysea.clan.data.model.PairingCode

/**
 * 预留的服务端 REST 接口(尚未实现)。
 *
 * 职责划分:
 * - REST 负责「资源操作」: 设备列表 / 绑定关系 / 配对码的创建与校验等;
 * - 实时推送(匹配请求、控制请求、会话建立)走 WebSocket, 见
 *   [com.fantaysea.clan.data.ws.WebSocketClient] 与 [com.fantaysea.clan.data.ws.WsMessage]。
 *
 * 落地建议: Retrofit + kotlinx.serialization, 端点如下(表结构见 AGENTS/需求):
 */
interface ClanApi {

    /** GET /api/devices?token={device_token} → 已配对设备列表 */
    suspend fun getDevices(deviceToken: String): List<Device>

    /** DELETE /api/bindings/{bindingId} → 解除绑定(对应 DeviceBinding 表删除) */
    suspend fun deleteBinding(bindingId: Long)

    /** GET /api/bindings?token={device_token} → 该设备的绑定关系 */
    suspend fun getBindings(deviceToken: String): List<DeviceBinding>

    /** POST /api/pairing-codes {device_token} → 生成配对码(写入 PairingCode 表) */
    suspend fun createPairingCode(deviceToken: String): PairingCode

    /**
     * POST /api/pairing-codes/validate {code, device_token}
     * → 校验配对码(命中未过期、未使用的 PairingCode 记录), 返回配对请求 id。
     */
    suspend fun validatePairingCode(code: String, deviceToken: String): String

    /** POST /api/pairing/respond {request_id, accept} → 被控端应答配对 */
    suspend fun respondPairing(requestId: String, accept: Boolean)

    /** POST /api/control/request {device_id, controller_token} → 控制端发起控制请求 */
    suspend fun requestControl(deviceId: Long, controllerToken: String): String

    /** POST /api/control/respond {request_id, accept} → 被控端应答控制请求 */
    suspend fun respondControl(requestId: String, accept: Boolean)
}
