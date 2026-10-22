package com.fantaysea.clan.ui.control

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.repository.ClanRepository

/**
 * 会话目的地设备解析门: 按 deviceId 从仓库解析 [Device] 后再渲染会话页。
 * 解析期间保持空白; 设备不存在(如参数缺失 / 会话中设备被解绑)时自动返回上一页。
 */
@Composable
fun SessionDeviceGate(
    repository: ClanRepository,
    deviceId: String,
    onBack: () -> Unit,
    content: @Composable (Device) -> Unit,
) {
    var device by remember { mutableStateOf<Device?>(null) }
    LaunchedEffect(deviceId) {
        device = null
        val found = runCatching { repository.findDevice(deviceId) }.getOrNull()
        if (found == null) {
            onBack()
        } else {
            device = found
        }
    }
    val found = device
    if (found != null) {
        content(found)
    } else {
        Box(modifier = Modifier.fillMaxSize())
    }
}
