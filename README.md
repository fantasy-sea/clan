# clan(家'助')

> 获取被控手机屏幕内容，控制方可以远程操作被控手机

## 项目背景

> 当父母不会操作 Android 手机时，子女可以通过自己的手机远程看到父母手机的实时画面，并帮助父母完成点击、滑动、返回、Home 等操作，从而远程解决手机使用问题。

## 功能特性

- 实时远程操作（受限于网络环境）
- 被控方、控制方身份一键切换
- 首页双入口：`远程控制`（已配对设备列表 / 左滑唤出删除 / 6 位配对码添加设备）与 `共享屏幕`（授权门控 / 6 位匹配码 / 60 秒倒计时自动刷新 / 配对与控制请求确认弹窗）
- 当前为**本地 Mock 驱动**的完整 UI 闭环：单机即可走通「生成码 → 输码 → 配对确认 → 新设备入列 → 发起控制 → 双方进入会话页」全流程

## 快速开始

### 设备要求

- Android 版本 >= 12

## 技术说明

> MediaProjection（获取屏幕内容） WebSocket+WebRTC AccessibilityService（无障碍服务）

### 当前实现（UI 与预留层）

- **UI 流程**：`ui/home`（导航宿主）→ `ui/control`（远程控制）｜`ui/share`（共享屏幕）｜`ui/session`（控制/被控页）
- **模拟服务端**：`data/mock/MockServer.kt` 以内存状态 + 事件流模拟配对/控制信令；接真实后端后只需替换 `data/repository/ClanRepository` 的实现，UI 与 ViewModel 不变。
- **预留接口**：
  - 数据模型 `data/model`：`Device`（Devices 表）、`PairingCode`（PairingCode 表）、`DeviceBinding`（DeviceBinding 表），字段与表一一对应；
  - REST：`data/remote/ClanApi.kt`（端点已在 KDoc 中约定）；
  - WebSocket 信令：`data/ws/WsProtocol.kt`（消息协议）、`data/ws/WebSocketClient.kt`（客户端接口）；
  - 屏幕捕获：`media/ScreenCaptureManager.kt`（授权管理）、`service/ScreenCaptureService.kt`（前台服务骨架）、`media/WebRtcManager.kt`（视频流接口，待引入 org.webrtc 实现）。
- **说明**：屏幕捕获 / WebRTC / 无障碍服务为预留骨架；真实画面传输与远程操作将在依赖就绪后在这些接口上实现。
