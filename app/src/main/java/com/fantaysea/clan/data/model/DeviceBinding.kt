package com.fantaysea.clan.data.model

/**
 * DeviceBinding 表对应的设备绑定关系模型(表字段一一对应)。
 * 记录一对已配对设备: device_a 与 device_b(二者无方向性)。
 */
data class DeviceBinding(
    val id: Long,
    val deviceA: String,
    val deviceB: String,
    val createdAt: Long,
)
