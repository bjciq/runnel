package io.github.xccxcxxc.runnel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import io.github.xccxcxxc.runnel.data.Settings
import io.github.xccxcxxc.runnel.ui.components.RunnelIcons
import io.github.xccxcxxc.runnel.ui.components.PullFromBottom
import io.github.xccxcxxc.runnel.ui.components.SwipeAction
import io.github.xccxcxxc.runnel.ui.components.SwipeRow
import io.github.xccxcxxc.runnel.ui.theme.RunnelTheme
import io.github.xccxcxxc.runnel.ui.components.rememberPullFromBottom
import io.github.xccxcxxc.runnel.ui.theme.listFling
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/**
 * “从底部上拉”在真实的组合里（Miuix 橡皮筋 + 下拉刷新 + 自定义惯性，和列表页一样）：
 * 拉到底继续上拉过阈值后松手，必须触发一次。
 */
@RunWith(RobolectricTestRunner::class)
// 开发机是 arm64 Linux，Robolectric 的原生运行时不支持，用纯 Java 的旧实现
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class PullFromBottomTest {
    @get:Rule
    val rule = createComposeRule()

    private var released = 0
    private lateinit var pull: PullFromBottom

    @OptIn(ExperimentalMaterial3Api::class)
    private fun setList(rows: Int) {
        rule.setContent {
            // 和 App 一样：RunnelTheme 提供 iOS 惯性和 Miuix 橡皮筋；每行是可左右滑的 SwipeRow
            RunnelTheme(Settings()) {
                val listState = rememberLazyListState()
                pull = rememberPullFromBottom(listState, enabled = true) { released++ }
                Box(Modifier.fillMaxSize().nestedScroll(pull.connection)) {
                    PullToRefreshBox(isRefreshing = false, onRefresh = {}) {
                        LazyColumn(Modifier.fillMaxSize().testTag("list"), state = listState, flingBehavior = listFling(), overscrollEffect = pull.overscroll) {
                            items(rows) {
                                SwipeRow(start = SwipeAction(RunnelIcons.Circle, Color.Blue) {}, end = SwipeAction(RunnelIcons.Star, Color.Yellow) {}) {
                                    Box(Modifier.fillMaxWidth().height(120.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** 手指从下往上慢慢拉（不甩），停住后松手。 */
    private fun slowPullUp(distanceFraction: Float = 0.7f) {
        rule.onNodeWithTag("list").performTouchInput {
            down(bottomCenter.copy(y = height * 0.9f))
            val steps = 40
            val dy = height * distanceFraction / steps
            repeat(steps) { moveBy(androidx.compose.ui.geometry.Offset(0f, -dy), delayMillis = 16) }
            // 停住一会儿再松手：速度接近 0，和截图里的操作一样
            repeat(5) { moveBy(androidx.compose.ui.geometry.Offset.Zero, delayMillis = 16) }
            up()
        }
        rule.waitForIdle()
    }

    @Test
    fun shortListFiresOnRelease() {
        setList(rows = 3) // 内容不满一屏：从一开始就在底部
        slowPullUp()
        assertEquals(1, released)
    }

    @Test
    fun longListAtBottomFiresOnRelease() {
        setList(rows = 30)
        rule.onNodeWithTag("list").performTouchInput { swipeUp(durationMillis = 200) }
        rule.waitForIdle()
        repeat(6) {
            rule.onNodeWithTag("list").performTouchInput { swipeUp(durationMillis = 100) }
            rule.waitForIdle()
        }
        released = 0
        slowPullUp()
        assertEquals(1, released)
    }

    @Test
    fun smallPullDoesNothing() {
        setList(rows = 3)
        slowPullUp(distanceFraction = 0.05f)
        assertEquals(0, released)
    }

    /** 慢慢上拉约 145dp 就该触发（阈值原来是 150dp，单手够不着，2026-10-20 改为 120dp）。 */
    @Test
    fun moderatePullFires() {
        setList(rows = 3)
        slowPullUp(distanceFraction = 0.18f)
        assertEquals(1, released)
    }

    /** 到底后快速往上一划（约 100dp，以前 96dp 的阈值就触发）：更粘、阈值更远，不该误触发（2026-10-20）。 */
    @Test
    fun quickShortSwipeDoesNothing() {
        setList(rows = 3)
        rule.onNodeWithTag("list").performTouchInput {
            down(bottomCenter.copy(y = height * 0.9f))
            val dy = 100.dp.toPx() / 6
            repeat(6) { moveBy(androidx.compose.ui.geometry.Offset(0f, -dy), delayMillis = 16) }
            up()
        }
        rule.waitForIdle()
        assertEquals(0, released)
    }
}
