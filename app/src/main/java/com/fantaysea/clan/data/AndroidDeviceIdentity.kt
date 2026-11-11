package com.fantaysea.clan.data

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 读取设备级稳定标识。
 *
 * - ANDROID_ID 随时可读；
 * - OAID 可能不存在、被用户关闭或读取超时，失败时返回 null，由上层降级到 ANDROID_ID。
 *
 * OAID 只能在用户同意隐私提示后读取：调用方通过 [identifiers] 的 allowOaid 参数控制。
 * 读取失败不抛异常，避免设备标识问题阻塞整个注册流程。
 */
class AndroidDeviceIdentity(context: Context) {
    private val appContext = context.applicationContext
    private val oaidProvider: OaidProvider = AndroidOaidProvider(appContext)

    @SuppressLint("HardwareIds")
    fun androidIdFingerprint(): String? = runCatching {
        Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
    }.getOrNull().let(::androidIdToFingerprint)

    suspend fun identifiers(allowOaid: Boolean): DeviceIdentifiers = withContext(Dispatchers.IO) {
        DeviceIdentifiers(
            oaidFingerprint = if (allowOaid) oaidToFingerprint(oaidProvider.oaid()) else null,
            androidIdFingerprint = androidIdFingerprint(),
        )
    }
}
