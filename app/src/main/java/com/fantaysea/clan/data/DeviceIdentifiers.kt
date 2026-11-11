package com.fantaysea.clan.data

import androidx.compose.runtime.Immutable

/**
 * 一次注册请求可携带的设备标识集合。
 *
 * 两个字段都是“客户端 fingerprint”：原始标识的 SHA-256（64 位小写 hex），
 * 服务端会在入库前再做一次 SHA-256。任一字段为 null 表示本次没有拿到该标识；
 * 两者都为空时注册会被拒绝。
 */
@Immutable
data class DeviceIdentifiers(
    val oaidFingerprint: String? = null,
    val androidIdFingerprint: String? = null,
) {
    fun isEmpty(): Boolean =
        oaidFingerprint.isNullOrBlank() && androidIdFingerprint.isNullOrBlank()
}
