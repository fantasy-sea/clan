package com.fantaysea.clan.ui.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fantaysea.clan.ui.theme.ClanTheme
import kotlinx.coroutines.launch

/**
 * 欢迎页(首次启动引导)框架。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    pages: List<OnboardingPage>,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
) {
    if (pages.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── 上方固定区域: 当前步骤指示 + 右上角跳过 ──────────────
            OnboardingHeader(
                currentStep = pagerState.currentPage + 1,
                totalSteps = pages.size,
                onSkip = {
                    // 默认: 跳到最后一页让用户看到"开始使用";
                    // 若希望"跳过 = 直接完成", 把这里改成 onComplete()
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pages.lastIndex)
                    }
                },
            )

            // ── 中间内容区: 每页内容不同 ───────────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = userScrollEnabled,
            ) { pageIndex ->
                Box(modifier = Modifier.fillMaxSize()) {
                    pages[pageIndex].content()
                }
            }

            // ── 下方: 下一步 / 最后一页显示 开始使用 ────────────────
            OnboardingFooter(
                isLastPage = isLastPage,
                currentStep = pagerState.currentPage + 1,
                totalSteps = pages.size,
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
                .padding(start = 24.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$currentStep/$totalSteps",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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


    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
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

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun OnboardingScreenPreview() {
    ClanTheme {
        OnboardingScreen(
            pages = onboardingPages(),
            onComplete = {},
        )
    }
}
