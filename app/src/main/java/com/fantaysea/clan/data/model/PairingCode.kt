package com.fantaysea.clan.data.model

/**
 * PairingCode 表对应的配对码模型(表字段一一对应)。
 * 服务端 JSON 采用 snake_case: device_id / expires_at / created_at。
 */
data class PairingCode(
    val id: Long,
    /** 6 位数字匹配码 */
    val code: String,
    /** 生成该码的被控端设备 id */
    val deviceId: String,
    /** 过期时间(epoch millis) */
    val expiresAt: Long,
    /** 是否已被使用(配对成功/校验通过后置 true) */
    val used: Boolean,
    val createdAt: Long,
)
