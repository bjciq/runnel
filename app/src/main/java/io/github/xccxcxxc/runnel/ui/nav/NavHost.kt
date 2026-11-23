package io.github.xccxcxxc.runnel.ui.nav

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * iOS 式页面栈：同时组合最上面三层。下层页面一直保留在组合里（不可见时 alpha = 0），
 * 所以侧滑返回一开始就能露出下层，不会在手势开始那一帧现组合整页。再下面一层（例如阅读页打开时的首页）
 * 也留着：从阅读页返回列表、或“下一个订阅源”替换掉旧列表后，新露出的下层不用在转场结束那一帧现组合
 * （模拟器上量到这一帧 UI 线程 14–20 ms，真机约 2–4 倍）。
 * 转场只改 graphicsLayer 的位移/透明度，全部在绘制阶段完成，不触发重组。
 */
@Composable
fun NavHost(navigator: Navigator, content: @Composable (NavEntry) -> Unit) {
    val holder = rememberSaveableStateHolder()
    SideEffect { navigator.onEntryRemoved = { holder.removeState(it.id) } }

    PredictiveBackHandler(enabled = navigator.canPop) { events -> navigator.handleBackGesture(events) }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { navigator.widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .fullScreenSwipeBack(navigator),
    ) {
        val stack = navigator.stack
        val top = stack.last()
        val under = stack.getOrNull(stack.size - 2)
        val vertical = top.enterFromBottom
        for (entry in stack.takeLast(3)) {
            key(entry.id) {
                val isTop = entry === top
                val isUnder = entry === under
                // 位移和透明度都放在最外层的 graphicsLayer 里；阴影/变暗是单独的小图层，
                // 转场中只改它们的 alpha，页面内容本身不会因此重新绘制。
                val container = if (isTop) {
                    Modifier
                        .graphicsLayer {
                            if (vertical) translationY = (1f - navigator.progress) * size.height
                            else translationX = (1f - navigator.progress) * size.width
                        }
                        // 吃掉落在顶层页面上的所有触摸，避免穿透到下层
                        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                } else if (!isUnder) {
                    // 第三层：只留在组合里，不画，也不进无障碍树（TalkBack 不该读到看不见的页面）
                    Modifier.graphicsLayer { alpha = 0f }.clearAndSetSemantics {}
                } else {
                    Modifier.graphicsLayer {
                        val p = navigator.progress
                        // 从下往上进场时，下层整页跟着往上顶走（像阅读页上下翻篇）；横向时只退 30%（iOS 推入）
                        if (vertical) translationY = -p * size.height else translationX = -0.3f * p * size.width
                        alpha = if (p >= 0.999f && !navigator.transitioning) 0f else 1f
                    }
                }
                Box(Modifier.fillMaxSize().then(container)) {
                    holder.SaveableStateProvider(entry.id) {
                        CompositionLocalProvider(LocalNavEntry provides entry, LocalNavigator provides navigator) { content(entry) }
                    }
                    if (isTop && !vertical) {
                        // 左边缘阴影：画在页面左侧外面（负偏移），整体随页面一起移动
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = (-10).dp)
                                .width(10.dp)
                                .fillMaxHeight()
                                .graphicsLayer { alpha = if (navigator.progress < 1f) 1f else 0f }
                                .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.10f)))),
                        )
                    } else if (isUnder) {
                        // 下层页面随转场变暗
                        Box(
                            Modifier
                                .matchParentSize()
                                .graphicsLayer { alpha = 0.12f * navigator.progress }
                                .background(Color.Black),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 页面任意位置向右滑返回（iOS 很多 App 的做法）。挂在不随转场移动的容器上，
 * 坐标稳定，页面才能严格跟手。只对 [Route.fullScreenBack] 的页面生效（[BlockSwipeBack] 期间暂停）；纵向手势立即放手。
 */
private fun Modifier.fullScreenSwipeBack(navigator: Navigator): Modifier = pointerInput(navigator) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!navigator.canPop || !navigator.top.route.fullScreenBack || navigator.top.swipeBackBlockers > 0) return@awaitEachGesture
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var dx = 0f
        var dy = 0f
        var dragging = false
        var ended = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val delta = change.positionChange()
                tracker.addPosition(change.uptimeMillis, change.position)
                if (!dragging) {
                    if (SwipeBackGuard.isClaimed(down.id)) return@awaitEachGesture
                    dx += delta.x
                    dy += delta.y
                    if (abs(dy) > slop && abs(dy) > abs(dx)) return@awaitEachGesture
                    if (dx < -slop) return@awaitEachGesture
                    if (dx > slop && dx > abs(dy) * 1.3f && navigator.beginDragBack()) dragging = true else continue
                }
                event.changes.forEach { it.consume() }
                navigator.dragBack((change.position.x - down.position.x - slop) / size.width)
            }
            if (dragging) {
                ended = true
                navigator.endDragBack(tracker.calculateVelocity().x / size.width)
            }
        } finally {
            // 手势被取消（例如页面被移除）时也要收尾，否则 transitioning 会一直为 true
            if (dragging && !ended) navigator.endDragBack(0f)
        }
    }
}
