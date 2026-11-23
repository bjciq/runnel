package io.github.xccxcxxc.runnel.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * 列表拉到底后继续上拉（Pull from bottom）：过阈值震动并 [armed]，松手调用 onRelease。
 * 到底后上拉更粘：橡皮筋只跟走手指的 [STICKY_FOLLOW]，阈值按手指距离算也更远，快速一划不容易误触发。
 * 把 [connection] 挂到列表外层的 `Modifier.nestedScroll`，[overscroll] 交给列表的 `overscrollEffect`。
 */
@Stable
class PullFromBottom internal constructor() {
    var armed by mutableStateOf(false)
        internal set
    /** 拉了阈值的几成（0..1），给提示图标做渐显；只在 graphicsLayer 里读，拉动时不重组。 */
    var progress by mutableFloatStateOf(0f)
        internal set
    lateinit var connection: NestedScrollConnection
        internal set
    var overscroll: OverscrollEffect? = null
        internal set
    /** StickyBottomOverscroll 正在把缩过的位移往下传。 */
    internal var sticky = false
}

@Composable
fun rememberPullFromBottom(
    listState: LazyListState,
    enabled: Boolean,
    // 150dp 单手够不着（2026-10-20 用户反馈），快速一划仍靠橡皮筋粘滞挡住
    threshold: Dp = 120.dp,
    onRelease: () -> Unit,
): PullFromBottom {
    val view = LocalView.current
    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    val isEnabled by rememberUpdatedState(enabled)
    val release by rememberUpdatedState(onRelease)
    val inner = rememberOverscrollEffect()
    return remember(listState, thresholdPx, inner) {
        PullFromBottom().apply {
            overscroll = inner?.let { StickyBottomOverscroll(it, listState, this) { isEnabled } }
            connection = object : NestedScrollConnection {
                var pulled = 0f
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    if (!isEnabled || listState.canScrollForward) {
                        pulled = 0f
                        armed = false
                        progress = 0f
                        return Offset.Zero
                    }
                    // 经过 StickyBottomOverscroll 的位移是缩过的，换回手指距离（列表滚不动时 Compose 不走橡皮筋，收到的就是原值）
                    val finger = if (sticky) available.y / STICKY_FOLLOW else available.y
                    pulled = (pulled - finger).coerceAtLeast(0f)
                    progress = (pulled / thresholdPx).coerceAtMost(1f)
                    val now = pulled > thresholdPx
                    if (now != armed) {
                        armed = now
                        view.performHapticFeedback(if (now) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.CLOCK_TICK)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    val fire = armed
                    pulled = 0f
                    armed = false
                    progress = 0f
                    if (fire) release()
                    return Velocity.Zero
                }
            }
        }
    }
}

/** 到底后上拉时橡皮筋跟手的比例。 */
private const val STICKY_FOLLOW = 0.5f

/**
 * 包住 Miuix 橡皮筋：列表到底后手指往上拉，只把位移的 [STICKY_FOLLOW] 交给它（往回拉、惯性、其它方向原样）。
 * 不能在 NestedScrollConnection 里消费一部分代替：Miuix 见到上层消费过就几乎不拉伸（2026-10-20 仪器测试）。
 */
private class StickyBottomOverscroll(
    private val inner: OverscrollEffect,
    private val listState: LazyListState,
    private val pull: PullFromBottom,
    private val enabled: () -> Boolean,
) : OverscrollEffect by inner {
    override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
        if (source != NestedScrollSource.UserInput || delta.y >= 0f || listState.canScrollForward || !enabled()) {
            return inner.applyToScroll(delta, source, performScroll)
        }
        pull.sticky = true
        try {
            return inner.applyToScroll(delta * STICKY_FOLLOW, source, performScroll) / STICKY_FOLLOW
        } finally {
            pull.sticky = false
        }
    }
}
