package com.fantaysea.clan.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 欢迎页分页模型。
 * 整个欢迎页 = 上(步骤指示) + 中(content) + 下(按钮), 这里只需提供"中间内容区"。
 */
data class OnboardingPage(
    val content: @Composable () -> Unit,
)

/**
 * 欢迎页各步骤的页面列表(示例骨架)。
 * 当前 4 页: 欢迎 → 功能说明 → 通知权限 → 屏幕捕获授权(MediaProjection)。
 * 视觉由你自行绘制: 直接替换各页 content 中的实现即可, 框架不关心页面内部。
 */
internal fun onboardingPages(): List<OnboardingPage> = buildList {
    // ── 第 1 步: 欢迎与产品简介 ────
    add(
        OnboardingPage {
            SamplePage(
                title = "欢迎使用家'助'",
                description = "家助是一款远程屏幕控制应用, 让您随时远程操作家人的手机。\n\n这里是产品介绍正文区域, 后续替换为你设计的文案与配图。",
            )
        }
    )
    // ── 第 2 步: 核心功能说明 ───────────────────────────────────────
    add(
        OnboardingPage {
            SamplePage(
                title = "核心功能",
                description = "可以在此页介绍产品的主要能力, 例如:\n\n· 远程屏幕共享\n· 远程协助操作\n· 语音通话与消息\n· 设备安全与隐私保障",
            )
        }
    )
    // ── 第 3 步: 请求系统权限(示例: 通知权限) Android13才需要请求通知权限，android12不显示 ───────────────────────
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(
            OnboardingPage {
                NotificationPermissionPage()
            }
        )
    }
    //第 4 步: 请求屏幕捕获授权(MediaProjection) 请求后需要立即消费，欢迎页中不做消费
//    add(
//        OnboardingPage {
//            MediaProjectionPermissionPage()
//        }
//    )
}
/**
 * 普通说明页的通用布局(占位示例): 插图区 + 标题 + 说明文字。
 * 整体可滚动, 适配小屏。各页可直接复用或完全重绘。
 */
@Composable
internal fun SamplePage(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        // 插图占位: 替换为你的插画(可换 Image / Lottie / Canvas 等)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "插图占位",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 运行时权限请求示例(Android 13+ 通知权限)。
 * 结构 = 说明文案 + 授权按钮 + 授权状态。其他运行时权限照此模式替换即可。
 */
@Composable
internal fun NotificationPermissionPage() {
    var granted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        granted = isGranted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "开启通知权限",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "用于接收远程协助请求与状态提醒。\n(此页为你需要绘制的权限说明页)",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedButton(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(if (granted) "已开启" else "允许通知")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (granted) "✓ 已获得通知权限" else "未授权时仅系统消息会受限, 不影响核心功能",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * MediaProjection 授权示例(屏幕捕获 = 远程控制的核心能力)。
 *
 * 注意: 它不是普通运行时权限, 而是通过系统弹窗授权, 回调结果是一个一次性 Intent;
 * 该页当前未启用。正式流程由「共享屏幕」页通过 ScreenCaptureManager 授权，
 * 会话建立后由 ScreenCaptureService + WebRtcManager 消费。
 */
@Composable
internal fun MediaProjectionPermissionPage() {
    val context = LocalContext.current
    var authorized by remember { mutableStateOf(false) }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val granted = result.resultCode == Activity.RESULT_OK && result.data != null
        authorized = granted
        if (granted) {
            // TODO(后续实现): 把 result.data(用户同意投屏的 Intent)交给
            //  MediaProjection 前台服务开始投屏。
            //  骨架方案: Intent(context, ScreenCaptureService::class.java)
            //            .putExtra(EXTRA_RESULT_DATA, result.data)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "授权屏幕捕获",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "为了远程操作手机屏幕, 需要你授权系统屏幕捕获。\n授权后仅在主动发起连接时才会开启投屏, 可随时停止。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedButton(
            onClick = {
                val mediaProjectionManager =
                    context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                captureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(if (authorized) "已授权" else "同意并允许投屏")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (authorized) "✓ 已获得屏幕捕获授权" else "未授权时将无法进行远程操作",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}


/** 上方固定区域: 步骤文字 + 进度圆点 + 跳过按钮 */
@Composable
internal fun OnboardingHeader(
    currentStep: Int,
    totalSteps: Int,
    onSkip: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$currentStep/$totalSteps",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            // 最后一页按钮已变成"开始使用", 不再需要"跳过"
            if (currentStep < totalSteps) {
                TextButton(onClick = onSkip) {
                    Text("跳过")
                }
            }
        }

    }
}

/** 下方固定按钮: 下一步 → 开始使用 */
@Composable
internal fun OnboardingFooter(
    isLastPage: Boolean,
    currentStep: Int,
    totalSteps: Int,
    onNext: () -> Unit,
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement
                .spacedBy(space = 8.dp, alignment = Alignment.CenterHorizontally)
        ) {
            repeat(totalSteps) { index ->
                val isCurrent = index == currentStep - 1
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(if (isCurrent) 24.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                )
            }
        }
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = if (isLastPage) "开始使用" else "下一步",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
