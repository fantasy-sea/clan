package com.fantaysea.clan.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fantaysea.clan.core.di.LocalNavigator
import com.fantaysea.clan.core.navigation.HomeDestination
import com.fantaysea.clan.data.model.HomeMenuCardInfo

/**
 * 产品入口页: 品牌区 + 两个入口大卡片(远程控制 / 共享屏幕)。
 * 暖色渐变背景 + 装饰光斑 + 交错入场 + 按压缩放。
 */
@Preview(name = "开屏", showBackground = true)
@Composable
fun HomeMenuScreen(
    isPlayAnimation: Boolean = true
) {
    val navigator = LocalNavigator.current
    val colorScheme = MaterialTheme.colorScheme
    var entered by remember(isPlayAnimation) { mutableStateOf(!isPlayAnimation) }
    LaunchedEffect(Unit) {
        if (isPlayAnimation) entered = true
    }
    // 描述对象(以及捕获 navigator 的回调)身份稳定, MenuCard 才能按 equals 命中跳过重组,
    // 否则每次重组都新建两个 HomeMenuCardInfo, 两张卡片会被整棵重组。
    val moduleList = remember(navigator, colorScheme) {
        listOf(
            HomeMenuCardInfo(
                "1",
                "远程控制",
                "操作已配对的家人设备",
                Icons.AutoMirrored.Outlined.ScreenShare,
                120,
                colorScheme.primaryContainer,
                colorScheme.primary.copy(alpha = 0.75f),
            ) { navigator.navigateTo(HomeDestination.ControlHome) },
            HomeMenuCardInfo(
                "2",
                "共享屏幕",
                "让家人协助操作",
                Icons.AutoMirrored.Outlined.ScreenShare,
                240,
                colorScheme.tertiaryContainer,
                colorScheme.tertiary.copy(alpha = 0.75f),
            ) { navigator.navigateTo(HomeDestination.Share) },
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        // 装饰光斑
        Box(
            modifier = Modifier
                .offset(x = (-40).dp, y = (-30).dp)
                .size(200.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 50.dp, y = 120.dp)
                .size(160.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            // 产品名展示
            //AnimatedVisibility 是个带动画的显隐容器
            AnimatedVisibility(
                visible = entered,
                enter = fadeIn(tween(400)) +
                        slideInVertically(tween(400)) { it / 8 },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary,
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ScreenShare,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(modifier = Modifier.size(14.dp))
                    Column {
                        Text(
                            text = "家助",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "家人的手机，随时相助",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            // 入口卡片(交错入场)
            moduleList.forEach {
                Spacer(modifier = Modifier.height(20.dp))
                MenuCard(
                    visible = entered,
                    info = it,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "安全加密 · 连接需双方确认",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 24.dp),
            )
        }
    }
}
@Composable
private fun MenuCard(
    visible: Boolean,
    info: HomeMenuCardInfo
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(120),
        label = "cardScale",
    )
    // EnterTransition / Brush 都不是 data class, 每次重组新建会让 AnimatedVisibility 的
    // transitionSpec 与 background 的 Brush 引用反复变化, 这里按参数缓存起来
    val enter = remember(info.delay) {
        fadeIn(tween(400, delayMillis = info.delay)) +
                slideInVertically(tween(400, delayMillis = info.delay)) { it / 8 }
    }
    val gradient = remember(info.gradientStart, info.gradientEnd) {
        Brush.linearGradient(listOf(info.gradientStart, info.gradientEnd))
    }

    AnimatedVisibility(
        visible = visible,
        enter = enter,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(136.dp)
                .scale(scale)
                .clip(RoundedCornerShape(24.dp))
                .background(gradient)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = info.event,
                )
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = info.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = info.desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
