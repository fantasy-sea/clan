package com.fantaysea.clan.media

/**
 * WebRTC 会话管理接口(预留, 尚未实现)。
 *
 * 职责: 在控制端与被控端之间建立 PeerConnection, 把被控端屏幕视频流
 * (来自 MediaProjection 的 Surface/VirtualDisplay)作为视频轨发送给控制端。
 *
 * 落地建议: 引入 org.webrtc:google-webrtc, 结合信令(WsMessage 中的 SDP/ICE 交换)。
 * 本接口只描述能力边界, 真实依赖就绪后再补实现。
 */
interface WebRtcManager {

    /** 创建 PeerConnection 并注册屏幕视频轨 */
    fun startScreenStream(onLocalSdp: (String) -> Unit)

    /** 处理对端 SDP Offer/Answer */
    fun onRemoteSdp(sdp: String)

    /** 处理对端 ICE Candidate */
    fun onRemoteIce(candidate: String)

    /** 控制端: 收到远端视频轨后的回调(渲染到 SurfaceViewRenderer) */
    fun onRemoteTrack(handler: (track: Any) -> Unit)

    /** 发送触摸/按键输入事件(远程操作) */
    fun sendInput(event: Any)

    /** 结束会话并释放连接 */
    fun dispose()
}
