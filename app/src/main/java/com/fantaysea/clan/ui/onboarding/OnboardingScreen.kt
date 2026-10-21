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
import com.fantaysea.clan.EMPTY_FUNCTION
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
    //获取引导页面列表
    val onboardingPages = remember {onboardingPages() }
    if (onboardingPages.isEmpty()) return

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



@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun OnboardingScreenPreview() {
    ClanTheme {
        OnboardingScreen(
            onComplete = EMPTY_FUNCTION,
        )
    }
}
