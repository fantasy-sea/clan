package com.fantaysea.clan.data

import java.security.MessageDigest
import java.util.Locale

/** Android 2.2 时代遗留的坏 ANDROID_ID；防御性排除，避免多台设备被合并成同一身份。 */
internal const val BROKEN_ANDROID_ID = "9774d56d682e549c"

/** OAID 不可用时厂商/库可能返回的占位值；这些值不能作为设备身份。 */
private val OAID_PLACEHOLDERS = setOf(
    "00000000-0000-0000-0000-000000000000",
    "unknown",
    "null",
    "none",
    "0",
)

/**
 * 把 ANDROID_ID 转换成可上报的稳定设备指纹。
 *
 * ANDROID_ID 在同一设备 + 同一签名密钥 + 同一 Android 用户下保持不变，
 * 清除应用数据、卸载重装都不会改变，因此可以作为服务端设备去重的键。
 * 这里只上传 SHA-256，不上传原始 ANDROID_ID。
 */
internal fun androidIdToFingerprint(androidId: String?): String? {
    val value = androidId?.trim().orEmpty()
    if (value.isEmpty() || value.equals(BROKEN_ANDROID_ID, ignoreCase = true)) return null
    return sha256Hex(value)
}

/**
 * 把 OAID/AAID 转换成可上报的稳定设备指纹。
 *
 * 不同厂商返回的 OAID 大小写可能不一致，先 trim + lowercase 再哈希，避免同一
 * OAID 因大小写差异被判成两台设备。空值、全零 UUID 和常见占位值视为不可用。
 */
internal fun oaidToFingerprint(oaid: String?): String? {
    val value = oaid?.trim()?.lowercase(Locale.ROOT).orEmpty()
    if (value.isEmpty() || value in OAID_PLACEHOLDERS) return null
    return sha256Hex(value)
}

private fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
