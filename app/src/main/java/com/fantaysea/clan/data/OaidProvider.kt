package com.fantaysea.clan.data

import android.content.Context
import com.github.gzuliyujiang.oaid.DeviceID
import com.github.gzuliyujiang.oaid.IGetter
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * OAID 读取抽象。只在用户同意隐私提示后调用。
 *
 * 使用 Android_CN_OAID 的异步 `DeviceID.getOAID`，而不是 `DeviceIdentifier.register`
 * 预取：后者会在 OAID 失败时回退到 AndroidID/GUID，导致我们无法区分拿到的是不是 OAID。
 */
internal interface OaidProvider {
    suspend fun oaid(): String?
}

internal class AndroidOaidProvider(context: Context) : OaidProvider {
    private val appContext = context.applicationContext

    override suspend fun oaid(): String? = withContext(Dispatchers.IO) {
        withTimeoutOrNull<String?>(OAID_TIMEOUT_MS) {
            suspendCancellableCoroutine<String?> { continuation ->
                val getter = object : IGetter {
                    override fun onOAIDGetComplete(result: String) {
                        if (continuation.isActive) continuation.resume(result)
                    }

                    override fun onOAIDGetError(error: Exception) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                // 某些厂商实现会在方法内部同步抛异常；统一降级为“没有 OAID”。
                runCatching { DeviceID.getOAID(appContext, getter) }
                    .onFailure { if (continuation.isActive) continuation.resume(null) }
            }
        }
    }

    private companion object {
        const val OAID_TIMEOUT_MS = 2_000L
    }
}
