package io.github.xccxcxxc.runnel

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.xccxcxxc.runnel.data.Settings
import io.github.xccxcxxc.runnel.ui.components.SheetPageHost
import io.github.xccxcxxc.runnel.ui.components.SheetPages
import io.github.xccxcxxc.runnel.ui.components.SliderRow
import io.github.xccxcxxc.runnel.ui.theme.RunnelTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/**
 * 阅读设置面板里的滑块（面板本身能上下滚动，和 ReaderStylePanel 一样）：
 * 按在圆点上拖，手指稍微斜一点也必须拖滑块、不能变成滚动面板（2026-10-20 真机反馈）。
 */
@RunWith(RobolectricTestRunner::class)
// 开发机是 arm64 Linux，Robolectric 的原生运行时不支持，用纯 Java 的旧实现
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class SliderRowTest {
    @get:Rule
    val rule = createComposeRule()

    private var value by mutableFloatStateOf(21f)
    private lateinit var scroll: ScrollState

    private fun setPanel() {
        rule.setContent {
            RunnelTheme(Settings()) {
                scroll = rememberScrollState()
                Column(Modifier.fillMaxWidth().height(300.dp).verticalScroll(scroll)) {
                    Spacer(Modifier.height(100.dp))
                    SliderRow("字号", value, 14f..32f, steps = 17, display = value.toInt().toString()) { value = it }
                    Spacer(Modifier.height(800.dp))
                }
            }
        }
    }

    private fun slider() = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))

    /** 圆点中心的横坐标（圆点 38dp 宽，轨道两端各缩进半个圆点）。 */
    private fun TouchInjectionScope.thumbX(): Float {
        val f = (value - 14f) / (32f - 14f)
        return 19.dp.toPx() + f * (width - 38.dp.toPx())
    }

    @Test
    fun diagonalDragFromThumbMovesSliderNotPanel() {
        setPanel()
        slider().performTouchInput {
            down(Offset(thumbX(), centerY))
            // 起手先往上偏了一点（竖向先过 touch slop），然后横着拖
            moveBy(Offset(2.dp.toPx(), -12.dp.toPx()), delayMillis = 16)
            repeat(8) { moveBy(Offset(10.dp.toPx(), 0f), delayMillis = 16) }
            up()
        }
        rule.waitForIdle()
        assertEquals(0, scroll.value)
        assertTrue("value=$value", value >= 26f)
    }

    @Test
    fun verticalSwipeOnTrackAwayFromThumbScrollsPanel() {
        setPanel()
        slider().performTouchInput {
            down(Offset(width * 0.92f, centerY))
            repeat(10) { moveBy(Offset(0f, -10.dp.toPx()), delayMillis = 16) }
            up()
        }
        rule.waitForIdle()
        assertTrue("scroll=${scroll.value}", scroll.value > 0)
        assertEquals(21f, value)
    }

    @Test
    fun tapOnTrackJumpsToStep() {
        setPanel()
        slider().performTouchInput { click(Offset(width - 2f, centerY)) }
        rule.waitForIdle()
        assertEquals(32f, value)
    }

    /** 设置 → 阅读是表单里的二级页，可以任意位置右滑返回：在滑块上往右拖（按在圆点或轨道上）不能退回上一页。 */
    @Test
    fun dragRightOnSheetSubPageMovesSliderNotPage() {
        lateinit var pages: SheetPages<String>
        rule.setContent {
            RunnelTheme(Settings()) {
                val scope = rememberCoroutineScope()
                pages = remember { SheetPages("root", scope).also { it.push("reader") } }
                SheetPageHost(pages) { page ->
                    if (page == "reader") Column(Modifier.fillMaxSize()) {
                        Spacer(Modifier.height(100.dp))
                        SliderRow("字号", value, 14f..32f, steps = 17, display = value.toInt().toString()) { value = it }
                    } else Box(Modifier.fillMaxSize())
                }
            }
        }
        rule.waitForIdle()
        // 从圆点往右拖
        slider().performTouchInput {
            down(Offset(thumbX(), centerY))
            repeat(6) { moveBy(Offset(10.dp.toPx(), 0f), delayMillis = 16) }
            up()
        }
        rule.waitForIdle()
        assertEquals(2, pages.stack.size)
        val afterThumb = value
        assertTrue("value=$value", afterThumb > 21f)
        // 从圆点左边的轨道往右拖
        value = 30f
        rule.waitForIdle()
        slider().performTouchInput {
            down(Offset(width * 0.15f, centerY))
            repeat(6) { moveBy(Offset(10.dp.toPx(), 0f), delayMillis = 16) }
            up()
        }
        rule.waitForIdle()
        assertEquals(2, pages.stack.size)
        assertTrue("value=$value", value < 30f)
        // 滑块以外的地方右滑照样返回
        rule.onRoot().performTouchInput {
            down(Offset(width * 0.2f, 40.dp.toPx()))
            repeat(10) { moveBy(Offset(width * 0.07f, 0f), delayMillis = 16) }
            up()
        }
        rule.waitForIdle()
        assertEquals(1, pages.stack.size)
    }
}
