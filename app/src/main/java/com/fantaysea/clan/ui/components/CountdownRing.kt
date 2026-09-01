package com.fantaysea.clan.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 匹配码有效时间环形指示器: 圆环按剩余比例平滑收缩, 环心显示剩余秒数。
 * 颜色随剩余时间变化: >30s 主题色 → ≤30s 琥珀 → ≤10s 错误红(缩放脉冲提示即将过期)。
 */
@Composable
fun CountdownRing(
    fraction: Float,
    remainingSeconds: Int,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
) {
    val ringColor = when {
        remainingSeconds <= 10 -> MaterialTheme.colorScheme.error
        remainingSeconds <= 30 -> Color(0xFFFFB300)
        else -> MaterialTheme.colorScheme.primary
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val urgent = remainingSeconds <= 10
    val scale by animateFloatAsState(
        targetValue = if (urgent) 1.08f else 1f,
        animationSpec = tween(500),
        label = "ringPulse",
    )

    Box(modifier = modifier.size(size).scale(scale), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = Offset(inset, inset),
                size = arcSize,
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = Offset(inset, inset),
                size = arcSize,
            )
        }
        Text(
            text = remainingSeconds.coerceAtLeast(0).toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = ringColor,
        )
    }
}
