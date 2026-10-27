package com.fantaysea.clan.ui.control

import androidx.compose.runtime.Stable
import com.fantaysea.clan.data.repository.PairedDevice

/**
 * 远程设备列表页的全部交互回调。
 *
 * 收拢回调是为了让 [ControlHomeScreenContent] 只保留「状态 + 回调」两组参数;
 * 标注 [Stable] 让 Compose 把它当稳定类型, 配合 remember 后内容可正常跳过重组。
 * 默认空实现同时方便 @Preview 与测试直接构造。
 */
@Stable
internal class ControlHomeActions(
    val onBack: () -> Unit = {},
    val onOpenPairDialog: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onPairDialogDismiss: () -> Unit = {},
    val onPairCodeChanged: (String) -> Unit = {},
    val onPairCodeComplete: (String) -> Unit = {},
    val onRequestControl: (PairedDevice) -> Unit = {},
    val onAskDeleteDevice: (PairedDevice) -> Unit = {},
    val onDeleteDevice: (PairedDevice) -> Unit = {},
    val onDismissDeleteDevice: () -> Unit = {},
    val onCancelControl: () -> Unit = {},
    // ── 多选 / 批量删除 ──────────────────────────────────────
    /** 长按设备项进入多选 */
    val onLongPressDevice: (PairedDevice) -> Unit = {},
    /** 多选态下点击设备项切换勾选 */
    val onToggleDeviceSelection: (PairedDevice) -> Unit = {},
    /** 退出多选 */
    val onExitSelection: () -> Unit = {},
    /** 全选当前设备 */
    val onSelectAll: () -> Unit = {},
    /** 取消全选 */
    val onClearSelection: () -> Unit = {},
    /** 请求批量删除(弹确认框) */
    val onAskBatchDelete: () -> Unit = {},
    /** 确认批量删除 */
    val onConfirmBatchDelete: () -> Unit = {},
    /** 取消批量删除 */
    val onDismissBatchDelete: () -> Unit = {},
    // ── 设备编辑 ─────────────────────────────────────────────
    /** 打开编辑设备弹窗 */
    val onAskEditDevice: (PairedDevice) -> Unit = {},
    val onEditDeviceNameChanged: (String) -> Unit = {},
    val onConfirmEditDevice: () -> Unit = {},
    val onDismissEditDevice: () -> Unit = {},
)
