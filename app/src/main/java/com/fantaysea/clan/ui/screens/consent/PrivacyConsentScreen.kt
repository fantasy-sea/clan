package com.fantaysea.clan.ui.screens.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fantaysea.clan.core.di.EMPTY_FUNCTION

/**
 * 隐私提示与设备标识同意门。
 *
 * OAID 库要求在最终用户同意隐私政策后才能读取设备标识。当前工程没有独立的隐私政策页，
 * 这里先用最小提示承载同意动作；正式发布前应由产品/法务确认文案与隐私政策链接。
 */




@Preview(showBackground = true)
@Composable
fun PrivacyConsentPreview(){
    PrivacyConsentScreen(
        EMPTY_FUNCTION,
        EMPTY_FUNCTION
    )
}

@Composable
fun PrivacyConsentScreen(
    onAgree: () -> Unit,
    onDecline: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "隐私保护提示",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "为了识别同一台设备、避免重复注册，家助会在你同意后读取设备标识" +
                "（OAID 和 ANDROID_ID）。标识会在本地计算哈希后上传，服务端只保存二次哈希，" +
                "仅用于设备去重与账号安全，不会用于广告。\n\n" +
                "OAID 可能因系统设置或重置而改变；读取失败时会自动改用 ANDROID_ID。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onAgree) {
            Text("同意并继续")
        }
        TextButton(onClick = onDecline) {
            Text("暂不同意")
        }
    }
}
