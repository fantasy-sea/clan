# clan(家'助')

> 获取被控手机屏幕内容，控制方可以远程操作被控手机

## 项目背景

> 当父母不会操作 Android 手机时，子女可以通过自己的手机远程看到父母手机的实时画面，并帮助父母完成点击、滑动、返回、Home 等操作，从而远程解决手机使用问题。

## 功能特性

- 实时远程操作（受限于网络环境）
- 被控方、控制方身份一键切换
- 首页双入口：`远程控制`（已配对设备列表 / 左滑唤出删除 / 6 位配对码添加设备）与 `共享屏幕`（屏幕共享 + 无障碍控制双权限门控 / 6 位匹配码 / 60 秒倒计时自动刷新 / 配对与控制请求确认弹窗）
- 已接入真实服务端：设备注册、绑定、配对、远程会话（发起/接受/拒绝/结束）与 WebSocket 实时信令由服务端驱动；控制指令、权限状态与 WebRTC 信令已按 P4 契约接入
- 已接入 WebRTC 媒体链路：被控端通过 MediaProjection 采集屏幕、以 P2P SRTP 发送给控制端，控制端在会话页实时渲染；支持 STUN/TURN 配置
- 已接入无障碍触控执行：控制端的轻触/滑动/返回/Home/最近任务由被控端通过 `dispatchGesture` / `performGlobalAction` 执行，并按结果回传 `CONTROL_RESULT`
- 操作前置条件：只有服务端会话 `CONNECTED`、WebRTC 已连通、控制端收到首帧且最近 5 秒内持续收帧时才解锁操作；画面未就绪/停滞/重连时触摸层与底部按钮全部锁定

## 快速开始

### 设备要求

- Android 版本 >= 12

## 技术说明

> MediaProjection（获取屏幕内容） WebSocket+WebRTC AccessibilityService（无障碍服务）

### 当前实现（UI、服务端接入与 WebRTC 媒体）

- **UI 流程**：`ui/home`（导航宿主）→ `ui/control`（远程控制）｜`ui/share`（共享屏幕）｜`ui/session`（控制/被控页）
- **服务端接入**：`data/repository/RealClanRepository` 通过 REST（`data/remote/ClanApi.kt`）与 WebSocket（`data/ws/WsProtocol.kt`、`data/ws/OkHttpWebSocketClient.kt`）连接真实服务端；已支持设备注册、绑定、配对、远程会话与控制/信令消息。
- **P4 会话协议**（与 `clan-service/docs/websocket.md` 对齐）：
  - REST：`POST /api/v1/sessions`、`GET /api/v1/sessions/{id}`、`POST /api/v1/sessions/{id}/accept|reject|end`；
  - WebSocket：`WEBRTC_OFFER/ANSWER`、`ICE_CANDIDATE`、`CONTROL`、`CONTROL_RESULT`、`PERMISSION_STATUS`；
  - 服务端事件：`CONTROL_REQUEST`、`SESSION_STARTED`、`SESSION_CONNECTED`、`SESSION_ENDED/FAILED`、`ERROR`；
  - 控制权在 MVP 中属于控制方；`SESSION_CONNECTED` 只在 `PeerConnection` 真正连通后由 `media/WebRtcManager` 上报（服务端幂等）。
- **WebRTC 媒体**：
  - `media/WebRtcManager.kt`：应用级媒体管理器，负责 offer/answer/ICE、PeerConnection、远端画面尺寸、失败收敛与会话结束后释放；
  - `media/ScreenCaptureManager.kt`：进程级一次性 MediaProjection 授权存储；`service/ScreenCaptureService.kt`：`mediaProjection` 类型前台服务，先 `startForeground` 再创建 MediaProjection；
  - `ui/session/RemoteVideoView.kt` + `VideoLayout.kt`：控制端 `SurfaceViewRenderer` 等比渲染，触摸坐标只在画面区域归一化；
  - 信令在 PeerConnection 就绪前按 `sessionId` 缓冲，ICE 在 remote description 前排队（`WebRtcSignalBuffer`、`IceCandidateBuffer`）。
  - **双端必须安装同一构建**：旧控制端会显示“媒体画面待 WebRTC 接入”且不发 `WEBRTC_OFFER`，新被控端会停在“正在建立画面连接…”。新版在控制端 90 秒、被控端 20 秒未完成 SDP 协商时会给出明确的版本提示并结束会话。
- **ICE / TURN 配置**（构建时通过 Gradle 属性注入）：
  - `CLAN_STUN_URLS`：逗号分隔的 STUN 地址，默认 `stun:stun.l.google.com:19302`，可置空；
  - `CLAN_TURN_URL`、`CLAN_TURN_USERNAME`、`CLAN_TURN_PASSWORD`：三者齐全才启用 TURN；
  - 示例：`.\gradlew.bat :app:assembleDebug -PCLAN_STUN_URLS=stun:stun.example.com:3478 -PCLAN_TURN_URL=turn:turn.example.com:3478 -PCLAN_TURN_USERNAME=clan -PCLAN_TURN_PASSWORD=secret`。
  - 同局域网/NAT 通常可由 host/STUN 候选直连；跨运营商对称 NAT 需要独立部署 TURN，服务端不下发 TURN 凭据。
- **无障碍控制**：`service/ClanAccessibilityService.kt` 维护服务实时连接状态；`service/AccessibilityControlExecutor.kt` 负责把 `CONTROL` 指令映射为 `dispatchGesture` / `performGlobalAction` 并等待执行结果。`ui/share` 提供无障碍分步引导：优先通过 `EXTRA_COMPONENT_NAME` 直达服务详情页，开启后自动回到 App，未开启时给出各品牌入口/「受限设置」排障。
- **视频就绪门控**：`media/RemoteVideoHealth.kt` + `media/WebRtcManager.kt` 维护控制端远端画面状态（等待首帧/正常/停滞）；`ui/session/ControlSessionScreen.kt` 仅在 `canControl` 为 true 时识别触摸并启用返回/Home/最近任务，发送前还会按实时状态复核一次。被控端在执行前也会确认会话、媒体和无障碍服务均就绪，失败时回传原因。
