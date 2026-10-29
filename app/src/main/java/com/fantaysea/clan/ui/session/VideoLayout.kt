package com.fantaysea.clan.ui.session

/** 视频内容在容器内的等比显示区域（像素）。 */
data class VideoRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * 计算视频等比显示区域（fit，不裁剪），居中。
 *
 * 控制端把 SurfaceViewRenderer 和触摸区域都限制在这个矩形内：
 * 画面不会被拉伸，也不会因黑边导致点击坐标偏移。
 */
fun fitVideoRect(
    videoWidth: Int,
    videoHeight: Int,
    containerWidth: Float,
    containerHeight: Float,
): VideoRect {
    val safeContainerWidth = containerWidth.coerceAtLeast(0f)
    val safeContainerHeight = containerHeight.coerceAtLeast(0f)
    if (videoWidth <= 0 || videoHeight <= 0 ||
        safeContainerWidth <= 0f || safeContainerHeight <= 0f
    ) {
        return VideoRect(0f, 0f, safeContainerWidth, safeContainerHeight)
    }
    val scale = minOf(
        safeContainerWidth / videoWidth,
        safeContainerHeight / videoHeight,
    )
    val width = (videoWidth * scale).coerceAtMost(safeContainerWidth)
    val height = (videoHeight * scale).coerceAtMost(safeContainerHeight)
    return VideoRect(
        left = (safeContainerWidth - width) / 2f,
        top = (safeContainerHeight - height) / 2f,
        width = width,
        height = height,
    )
}
