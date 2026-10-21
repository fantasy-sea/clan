package com.fantaysea.clan.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
<<<<<<< HEAD
import com.fantaysea.clan.EMPTY_FUNCTION
=======
import androidx.compose.ui.unit.dp
import com.fantaysea.clan.core.navigation.Onboarding
>>>>>>> ed8cc7a2bcc6e1a65cef8d6559532e18a32a3172
import com.fantaysea.clan.ui.theme.ClanTheme
import kotlinx.coroutines.launch

/**
 * 欢迎页(首次启动引导)框架。
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
<<<<<<< HEAD
    //获取引导页面列表
    val onboardingPages = remember {onboardingPages() }
    if (onboardingPages.isEmpty()) return
=======
    val pages = remember { onboardingPages() }
    if (pages.isEmpty()) return
>>>>>>> ed8cc7a2bcc6e1a65cef8d6559532e18a32a3172

    //用于构建页面装填配合Pager使用
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val coroutineScope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == onboardingPages.lastIndex

    Scaffold(modifier = modifier.fillMaxSize()) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // ── 上方固定区域: 当前步骤指示 + 右上角跳过 ──────────────
            OnboardingHeader(
                currentStep = pagerState.currentPage + 1,
                totalSteps = onboardingPages.size,
                onSkip = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(onboardingPages.lastIndex)
                    }
                },
            )
            // ── 中间内容区: 每页内容不同 ───────────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { pageIndex ->
                Box(modifier = Modifier.fillMaxSize()) {
                    onboardingPages[pageIndex].content()
                }
            }

            // ── 下方: 下一步 / 最后一页显示 开始使用 ────────────────
            OnboardingFooter(
                isLastPage = isLastPage,
                currentStep = pagerState.currentPage + 1,
                totalSteps = onboardingPages.size,
                onNext = {
                    if (isLastPage) {
                        onComplete()
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
            )
        }
    }
}

<<<<<<< HEAD


@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun OnboardingScreenPreview() {
    ClanTheme {
        OnboardingScreen(
            onComplete = EMPTY_FUNCTION,
        )
    }
}
=======
/** 上方固定区域: 步骤文字 + 进度圆点 + 跳过按钮 */
@Composable
private fun OnboardingHeader(
    currentStep: Int,
    totalSteps: Int,
    onSkip: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (currentStep != totalSteps) {
                Text(
                    text = "$currentStep/$totalSteps",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            // 最后一页按钮已变成"开始使用", 不再需要"跳过"
            if (currentStep < totalSteps) {
                TextButton(onClick = onSkip) {
                    Text("跳过")
                }
            }
        }

    }
}

/** 下方固定按钮: 下一步 → 开始使用 */
@Composable
private fun OnboardingFooter(
    isLastPage: Boolean,
    currentStep: Int,
    totalSteps: Int,
    onNext: () -> Unit,
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement
                .spacedBy(space = 8.dp, alignment = Alignment.CenterHorizontally)
        ) {
            repeat(totalSteps) { index ->
                val isCurrent = index == currentStep - 1
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(if (isCurrent) 24.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                )
            }
        }
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = if (isLastPage) "开始使用" else "下一步",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
>>>>>>> ed8cc7a2bcc6e1a65cef8d6559532e18a32a3172
