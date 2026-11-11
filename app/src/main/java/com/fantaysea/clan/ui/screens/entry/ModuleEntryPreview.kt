package com.fantaysea.clan.ui.screens.entry

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * 产品入口页: 品牌区 + 两个入口大卡片(远程控制 / 共享屏幕)。
 * 暖色渐变背景 + 装饰光斑 + 交错入场 + 按压缩放。
 */
@Preview(name = "开屏", showBackground = true)
@Composable
fun ModuleEntryPreview(){
    ModuleEntryScreen(false)
}