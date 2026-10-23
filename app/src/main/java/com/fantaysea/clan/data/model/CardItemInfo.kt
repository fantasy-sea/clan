package com.fantaysea.clan.data.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

sealed interface CardItemInfo {
    val id: String  // 公共字段，用于 LazyList 的 key
}

/**
 * 首页入口卡片描述。
 *
 * 显式标 [Immutable]: 字段全是 val, Compose 会按 equals 判等, 参数没变时跳过调用方(首页卡片)的重组。
 *
 * 渐变刻意用两个 [Color] 而不是 List<Color>: 集合类型不稳定, 会让整个类被推断为 unstable,
 * 跳过判断退化成实例比较 -- 只要描述对象被重建, 卡片就必须重组。
 */
@Immutable
data class HomeMenuCardInfo(
    override val id: String,
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val delay: Int,
    val gradientStart: Color,
    val gradientEnd: Color,
    val event: () -> Unit,
) : CardItemInfo