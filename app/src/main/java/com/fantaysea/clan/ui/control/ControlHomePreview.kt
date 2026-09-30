package com.fantaysea.clan.ui.control

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.repository.PairedDevice
import com.fantaysea.clan.ui.theme.ClanTheme

private val previewDeviceList = listOf(
    PairedDevice(
        device = Device(
            id = "dev_preview_mom",
            deviceName = "妈妈的手机",
            platform = "Android",
            model = "HUAWEI Mate 40",
            androidVersion = "12",
            lastOnlineAt = 0L,
            createdAt = 0L,
        ),
        online = true,
    ), PairedDevice(
        device = Device(
            id = "dev_preview_dad",
            deviceName = "爸爸的手机",
            platform = "Android",
            model = "Xiaomi 12",
            androidVersion = "12",
            lastOnlineAt = null,
            createdAt = 0L,
        ),
        online = false,
    )
)

@Composable
private fun ControlHomePreviewContent(state: ControlHomeUiState) {
    ClanTheme {
        ControlHomeScreenContent(
            state = state,
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onOpenPairDialog = {},
            onRetry = {},
            onPairDialogDismiss = {},
            onPairCodeChanged = {},
            onPairCodeComplete = {},
            onRequestControl = {},
            onDeleteDevice = {},
            onCancelControl = {},
        )
    }
}

@Preview(name = "设备列表", group = "页面状态", showBackground = true)
@Composable
fun ControlHomeScreenPreview() {
    ControlHomePreviewContent(
        state = ControlHomeUiState(
            pageState = DeviceListState.Success(previewDeviceList),
        ),
    )
}

@Preview(name = "加载中", group = "页面状态", showBackground = true)
@Composable
private fun ControlHomeLoadingPreview() {
    ControlHomePreviewContent(
        state = ControlHomeUiState(
            pageState = DeviceListState.Loading
        )
    )
}

@Preview(name = "空设备列表", group = "页面状态", showBackground = true)
@Composable
private fun ControlHomeEmptyPreview() {
    ControlHomePreviewContent(ControlHomeUiState(pageState = DeviceListState.Empty))
}

@Preview(name = "加载失败", group = "页面状态", showBackground = true)
@Composable
private fun ControlHomeErrorPreview() {
    ControlHomePreviewContent(
        state = ControlHomeUiState(
            pageState = DeviceListState.Error("网络连接失败，请稍后重试")
        ),
    )
}

@Preview(name = "等待控制确认", group = "页面状态", showBackground = true)
@Composable
private fun ControlHomeWaitingPreview() {
    ControlHomePreviewContent(
        state = ControlHomeUiState(
            pageState = DeviceListState.Success(previewDeviceList),
            pendingControl = previewDeviceList.first(),
        ),
    )
}

@Preview(
    name = "删除确认弹窗",
    group = "弹窗",
    showBackground = true,
    widthDp = 360,
    heightDp = 640
)
@Composable
private fun DeleteDeviceConfirmDialogPreview() {
    ClanTheme {
        DeleteDeviceConfirmDialog(
            target = previewDeviceList.first(),
            onConfirm = {},
            onDismiss = {},
        )
    }
}


@Composable
private fun PairCodeDialogPreview(state:PairPhase){
    ClanTheme() {
        PairCodeDialog(
            state = ControlHomeUiState(pairPhase = state),
            onDismiss = {},
            onCodeChanged = {}
        ) {}
    }
}

@Preview(
    name = "添加设备成功弹窗",
    group = "弹窗",
    showBackground = true,
    widthDp = 360,
    heightDp = 640
)
@Composable
private fun PairCodeDialogPreviewSuccess() {
    PairCodeDialogPreview(PairPhase.Success)
}

@Preview(
    name = "输入设备弹窗",
    group = "弹窗",
    showBackground = true,
    widthDp = 360,
    heightDp = 640
)
@Composable
private fun PairCodeDialogPreviewInput() {
   PairCodeDialogPreview(PairPhase.Input)
}

@Preview(
    name = "添加设备失败弹窗",
    group = "弹窗",
    showBackground = true,
    widthDp = 360,
    heightDp = 640
)
@Composable
private fun PairCodeDialogPreviewError() {
    PairCodeDialogPreview(PairPhase.Error)
}

@Preview(
    name = "添加设备等待弹窗",
    group = "弹窗",
    showBackground = true,
    widthDp = 360,
    heightDp = 640
)
@Composable
private fun PairCodeDialogPreviewWaiting() {
    PairCodeDialogPreview(PairPhase.Waiting)
}

@Preview(
    name = "添加设备验证弹窗",
    group = "弹窗",
    showBackground = true,
    widthDp = 360,
    heightDp = 640
)
@Composable
private fun PairCodeDialogPreviewValidating() {
    PairCodeDialogPreview(PairPhase.Validating)
}
