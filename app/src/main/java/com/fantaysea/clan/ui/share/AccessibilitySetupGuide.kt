package com.fantaysea.clan.ui.share

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fantaysea.clan.R
import com.fantaysea.clan.service.AccessibilitySettingsTarget

/**
 * 无障碍分步引导（全屏）。
 *
 * 面向不熟悉手机操作的用户：跳转前把「会发生什么、点哪里」说清楚；
 * 回来后明确告诉用户「已开启」或「还没开，下一步怎么做」；同时把
 * Android 13+ 非商店安装的「受限设置」和各家 ROM 的入口差异写进帮助。
 *
 * 显示与否、阶段切换由 [ShareViewModel] 的 [AccessibilityGuideState] 决定。
 */
@Composable
internal fun AccessibilitySetupGuide(
    state: AccessibilityGuideState,
    onOpenSettings: () -> Unit,
    onRecheck: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.visible) return
    // 与系统设置里显示的名称完全一致，用户才能对上号。
    val serviceLabel = stringResource(R.string.accessibility_service_label)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (state.stage) {
                AccessibilityGuideStage.Enabled -> GuideEnabled()
                AccessibilityGuideStage.Missing -> GuideMissing(
                    serviceLabel = serviceLabel,
                    onOpenSettings = onOpenSettings,
                    onDismiss = onDismiss,
                )
                AccessibilityGuideStage.Waiting -> GuideWaiting(
                    serviceLabel = serviceLabel,
                    onOpenSettings = onOpenSettings,
                    onDismiss = onDismiss,
                )
                AccessibilityGuideStage.Intro -> GuideIntro(
                    target = state.target,
                    serviceLabel = serviceLabel,
                    onOpenSettings = onOpenSettings,
                    onRecheck = onRecheck,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun GuideIntro(
    target: AccessibilitySettingsTarget?,
    serviceLabel: String,
    onOpenSettings: () -> Unit,
    onRecheck: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Accessibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "开启「无障碍」",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "开启后，家人才可以在你同意后帮你点屏幕。打开后会跳到手机设置，跟着下面 3 步做，大约 20 秒。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        GuideStep(number = 1, text = "点下面的「去打开设置」。")
        GuideStep(number = 2, text = stepTwoText(target, serviceLabel))
        GuideStep(number = 3, text = "手机问「允许……完全控制您的设备？」时，点「允许」。")
        Spacer(Modifier.height(16.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ) {
            Text(
                text = "只有你在这里点「允许控制」，家人才会操作。随时可以在系统设置里关掉。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("去打开设置", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onRecheck,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("我已经打开了，检测一下")
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("暂时不用")
        }
    }
}

@Composable
private fun GuideWaiting(
    serviceLabel: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(20.dp))
        Text(
            text = "正在等待你完成设置",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "请在系统设置里打开「$serviceLabel」右边的开关。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("重新打开设置")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss) { Text("取消") }
    }
}

@Composable
private fun GuideMissing(
    serviceLabel: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    var helpExpanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Text(
            text = "还没检测到已开启",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "可能是开关没打开、弹窗点了取消，或打开的是别的项目。请检查：",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        ChecklistItem("「$serviceLabel」右边的开关是打开状态")
        ChecklistItem("弹窗里点的是「允许」，不是「取消」")
        ChecklistItem("打开的就是这一项，不是其他无障碍服务")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "如果开关是灰的，或提示「受限制的设置」，点下面的「查看开启方法」。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("再打开一次设置", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { helpExpanded = !helpExpanded },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(if (helpExpanded) "收起开启方法" else "查看开启方法")
        }
        if (helpExpanded) {
            Spacer(Modifier.height(20.dp))
            GuideHelp(serviceLabel = serviceLabel, context = context)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("稍后")
        }
    }
}

@Composable
private fun GuideHelp(serviceLabel: String, context: Context) {
    Text(
        text = "开关是灰的，或提示「受限制的设置」？",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = "从浏览器、微信或文件管理器安装的 App（不是应用商店），Android 13 起系统会默认禁止开启无障碍：\n" +
            "1. 点下面的「打开应用信息」\n" +
            "2. 点右上角的 ⋮（三个点）\n" +
            "3. 点「允许受限制的设置」并确认\n" +
            "4. 回到无障碍页面，再打开开关",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
    OutlinedButton(onClick = { openAppInfo(context) }) { Text("打开应用信息") }
    Spacer(Modifier.height(18.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    Spacer(Modifier.height(14.dp))
    Text(
        text = "找不到开关？各品牌大致位置：",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(6.dp))
    OemHints.forEach { (brand, path) ->
        Row(modifier = Modifier.padding(vertical = 3.dp)) {
            Text(
                text = brand,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(132.dp),
            )
            Text(
                text = path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = "也可以直接在设置顶部搜索「家助」。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = { shareSteps(context, serviceLabel) }) {
        Text("把开启方法发给家人")
    }
}

@Composable
private fun GuideEnabled() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = GuideGreen,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "已开启",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = GuideGreen,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "现在可以接受家人的操作请求了",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GuideStep(number: Int, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$number",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(top = 2.dp),
        )
    }
}

@Composable
private fun ChecklistItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(10.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 步骤 2 的文案跟着本次实际落点走，避免「明明已经在这一页，却让用户去列表找」。 */
private fun stepTwoText(target: AccessibilitySettingsTarget?, serviceLabel: String): String =
    when (target) {
        AccessibilitySettingsTarget.ServiceDetail ->
            "页面应该已经显示「$serviceLabel」，打开它右边的开关。如果看到的是列表，就在「已下载的应用 / 已安装的服务」里找它。"
        AccessibilitySettingsTarget.AccessibilityList ->
            "在「已下载的应用 / 已安装的服务」中找到「$serviceLabel」，打开右边的开关。"
        AccessibilitySettingsTarget.SystemSettings ->
            "进入「无障碍」，找到「$serviceLabel」，打开右边的开关。"
        AccessibilitySettingsTarget.None, null ->
            "找到「$serviceLabel」，打开它右边的开关（开关会变蓝或变绿）。"
    }

/** 打开本应用信息页：受限设置需要在「应用信息 → 右上角 ⋮」里解除。 */
private fun openAppInfo(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )
    if (context.findActivity() == null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

/** 人工兜底：把步骤发给家人，让家人电话或当面指导。 */
private fun shareSteps(context: Context, serviceLabel: String) {
    val text = buildString {
        appendLine("请帮我在手机上开启「$serviceLabel」：")
        appendLine("1. 打开手机「设置」")
        appendLine("2. 进入「无障碍」（有的手机叫「辅助功能」）")
        appendLine("3. 找到「$serviceLabel」，打开右边的开关")
        appendLine("4. 系统弹窗点「允许」")
        appendLine("5. 按返回键回到「clan」App")
    }
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    val chooser = Intent.createChooser(send, "把开启方法发给家人")
    if (context.findActivity() == null) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(chooser) }
}

private fun Context.findActivity(): Activity? =
    generateSequence(this) { (it as? ContextWrapper)?.baseContext }
        .firstOrNull { it is Activity } as? Activity

private val GuideGreen = Color(0xFF2E7D32)

/** 各品牌无障碍入口的大致位置（不同系统版本文字可能略有差异）。 */
private val OemHints = listOf(
    "小米 / Redmi" to "设置 → 更多设置 → 无障碍 → 已下载的应用",
    "华为 / 荣耀" to "设置 → 辅助功能 → 无障碍 → 已安装的服务",
    "OPPO / 一加" to "设置 → 其他设置 → 无障碍 → 已安装的服务",
    "vivo / iQOO" to "设置 → 快捷与辅助 → 无障碍 → 已安装的服务",
    "三星" to "设置 → 辅助功能 → 已安装的应用",
    "其他 / 原生" to "设置 → 无障碍 → 已下载的应用",
)
