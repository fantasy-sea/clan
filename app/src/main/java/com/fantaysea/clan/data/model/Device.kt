package com.fantaysea.clan.data.model

/** 不含认证 token 的设备领域模型。 */
data class Device(
    val id: String,
    val deviceName: String,
    val platform: String,
    val model: String,
    val androidVersion: String?,
    val lastOnlineAt: Long?,
    val createdAt: Long?,
)
