package com.fantaysea.clan.data.model

/**
 * Devices 表对应的设备模型(表字段一一对应)。
 * 服务端 JSON 采用 snake_case: device_token / device_name / platform / model /
 * last_online_at / created_at。接入真实序列化时在此补充 @SerialName 映射。
 *
 * 说明: 在线状态不是表字段, 由服务端根据心跳推导; UI 层通过 [PairedDevice] 承载。
 */
data class Device(
    val id: Long,
    val deviceToken: String,
    val deviceName: String,
    val platform: String,
    val model: String,
    /** 最近在线时间(epoch millis); null 表示从未在线 */
    val lastOnlineAt: Long?,
    /** 注册/首次上报时间(epoch millis) */
    val createdAt: Long,
)
