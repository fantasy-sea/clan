package com.fantaysea.clan.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.repository.RemoteEvent
import com.fantaysea.clan.media.RemoteVideoSize
import com.fantaysea.clan.media.RemoteVideoState
import com.fantaysea.clan.media.WebRtcState
import com.fantaysea.clan.ui.theme.ClanTheme

/**
 * ControlSessionScreen 的 Compose 预览。
 *
 * 预览直接渲染无副作用的 [ControlSessionScreenContent]，不读取 CompositionLocal，
 * 也不创建真实 WebRTC/SurfaceViewRenderer；远端画面用静态占位代替。
 */

private val previewPeer = Device(
    id = "dev_preview_mom",
    deviceName = "妈妈的手机",
    platform = "Android",
    model = "HUAWEI Mate 40",
    androidVersion = "12",
    lastOnlineAt = 0L,
    createdAt = 0L,
)

private val previewVideoSize = RemoteVideoSize(width = 1080, height = 2400)

private val previewPermissions = RemoteEvent.PermissionStatus(
    sessionId = "session_preview",
    screenCapture = true,
    accessibility = true,
)

@Composable
private fun PreviewFrame(state: ControlSessionUiState) {
    ClanTheme {
        ControlSessionScreenContent(
            state = state,
            snackbarHostState = remember { SnackbarHostState() },
            video = { modifier -> PreviewRemoteScreen(modifier) },
            onRequestEnd = {},
            onConfirmEnd = {},
            onDismissEnd = {},
            onTap = { _, _ -> },
            onSwipe = { _, _, _, _ -> },
            onBackCommand = {},
            onHomeCommand = {},
            onRecentsCommand = {},
        )
    }
}

/** 预览用的远端画面占位；真实渲染由 RemoteVideoSurface 注入。 */
@Composable
private fun PreviewRemoteScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color(0xFF23272E)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.TouchApp,
                contentDescription = null,
                tint = Color(0xFF4A5058),
                modifier = Modifier.size(64.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "远端画面占位",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF9AA0A8),
            )
            Text(
                text = "1080 × 2400",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7178),
            )
        }
    }
}

@Preview(name = "连接中", group = "画面状态", showSystemUi = true)
@Composable
private fun ControlSessionConnectingPreview() {
    PreviewFrame(
        ControlSessionUiState(
            peer = previewPeer,
            connected = false,
            mediaState = WebRtcState.Negotiating,
        )
    )
}

@Preview(name = "等待首帧", group = "画面状态", showSystemUi = true)
@Composable
private fun ControlSessionWaitingFirstFramePreview() {
    PreviewFrame(
        ControlSessionUiState(
            peer = previewPeer,
            connected = true,
            mediaState = WebRtcState.Connected,
            remoteVideoState = RemoteVideoState.WaitingFirstFrame,
        )
    )
}

@Preview(name = "已连接", group = "画面状态", showSystemUi = true)
@Composable
private fun ControlSessionConnectedPreview() {
    PreviewFrame(
        ControlSessionUiState(
            peer = previewPeer,
            connected = true,
            mediaState = WebRtcState.Connected,
            remoteVideoState = RemoteVideoState.Streaming,
            remoteVideoSize = previewVideoSize,
            permissionStatus = previewPermissions,
            seconds = 125,
        )
    )
}

@Preview(name = "权限部分可用", group = "画面状态", showSystemUi = true)
@Composable
private fun ControlSessionPartialPermissionPreview() {
    PreviewFrame(
        ControlSessionUiState(
            peer = previewPeer,
            connected = true,
            mediaState = WebRtcState.Connected,
            remoteVideoState = RemoteVideoState.Streaming,
            remoteVideoSize = previewVideoSize,
            permissionStatus = previewPermissions.copy(accessibility = false),
            seconds = 42,
        )
    )
}

@Preview(name = "画面重连中", group = "画面状态", showSystemUi = true)
@Composable
private fun ControlSessionReconnectingPreview() {
    PreviewFrame(
        ControlSessionUiState(
            peer = previewPeer,
            connected = true,
            mediaState = WebRtcState.Reconnecting,
            remoteVideoState = RemoteVideoState.WaitingFirstFrame,
            remoteVideoSize = previewVideoSize,
            permissionStatus = previewPermissions,
            seconds = 305,
        )
    )
}

@Preview(name = "画面中断（操作锁定）", group = "画面状态", showSystemUi = true)
@Composable
private fun ControlSessionStalledPreview() {
    PreviewFrame(
        ControlSessionUiState(
            peer = previewPeer,
            connected = true,
            mediaState = WebRtcState.Connected,
            remoteVideoState = RemoteVideoState.Stalled,
            remoteVideoSize = previewVideoSize,
            permissionStatus = previewPermissions,
            seconds = 87,
        )
    )
}

@Preview(name = "媒体失败", group = "画面状态", showSystemUi = true)
@Composable
private fun ControlSessionFailedPreview() {
    PreviewFrame(
        ControlSessionUiState(
            peer = previewPeer,
            connected = true,
            mediaState = WebRtcState.Failed("ICE 连接超时，请检查两端网络后重新发起控制"),
        )
    )
}

@Preview(name = "结束确认弹窗", group = "弹窗", showSystemUi = true)
@Composable
private fun ControlSessionEndConfirmPreview() {
    PreviewFrame(
        ControlSessionUiState(
            peer = previewPeer,
            connected = true,
            mediaState = WebRtcState.Connected,
            remoteVideoState = RemoteVideoState.Streaming,
            remoteVideoSize = previewVideoSize,
            permissionStatus = previewPermissions,
            seconds = 68,
            endConfirmVisible = true,
        )
    )
}

@Preview(name = "会话已结束", group = "页面状态", showBackground = true)
@Composable
private fun SessionUnavailablePreview() {
    ClanTheme {
        SessionUnavailable(
            onBack = {},
            reason = "媒体连接失败，远程控制会话已结束。",
        )
    }
}
