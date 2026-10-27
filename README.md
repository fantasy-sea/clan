# clan(家'助')

> 获取被控手机屏幕内容，控制方可以远程操作被控手机

## 项目背景

> 当父母不会操作 Android 手机时，子女可以通过自己的手机远程看到父母手机的实时画面，并帮助父母完成点击、滑动、返回、Home 等操作，从而远程解决手机使用问题。

## 功能特性

- 实时远程操作（受限于网络环境）
- 被控方、控制方身份一键切换
- 首页双入口：`远程控制`（已配对设备列表 / 左滑唤出删除 / 6 位配对码添加设备）与 `共享屏幕`（授权门控 / 6 位匹配码 / 60 秒倒计时自动刷新 / 配对与控制请求确认弹窗）
- 已接入真实服务端：设备注册、绑定、配对与 WebSocket 实时信令由服务端驱动；控制会话接口尚未开放，相关入口按后端能力隐藏

## 快速开始

### 设备要求

- Android 版本 >= 12

## 技术说明

> MediaProjection（获取屏幕内容） WebSocket+WebRTC AccessibilityService（无障碍服务）

### 当前实现（UI、服务端接入与预留层）

- **UI 流程**：`ui/home`（导航宿主）→ `ui/control`（远程控制）｜`ui/share`（共享屏幕）｜`ui/session`（控制/被控页）
- **服务端接入**：`data/repository/RealClanRepository` 通过 REST（`data/remote/ClanApi.kt`）与 WebSocket（`data/ws/WsProtocol.kt`、`data/ws/OkHttpWebSocketClient.kt`）连接真实服务端；当前已支持设备注册、设备绑定、配对与实时信令，控制会话接口尚未开放。
- **预留接口**：
  - 数据模型 `data/model`：`Device`（Devices 表）、`PairingCode`（PairingCode 表）、`DeviceBinding`（DeviceBinding 表），字段与表一一对应；
  - 屏幕捕获：`media/ScreenCaptureManager.kt`（授权管理）、`service/ScreenCaptureService.kt`（前台服务骨架）、`media/WebRtcManager.kt`（视频流接口，待引入 org.webrtc 实现）。
- **说明**：屏幕捕获 / WebRTC / 无障碍服务为预留骨架；真实画面传输与远程操作将在依赖就绪后在这些接口上实现。
