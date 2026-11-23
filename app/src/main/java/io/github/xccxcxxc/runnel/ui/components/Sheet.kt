package io.github.xccxcxxc.runnel.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.xccxcxxc.runnel.ui.nav.SwipeBackGuard
import io.github.xccxcxxc.runnel.ui.theme.Runnel
import io.github.xccxcxxc.runnel.ui.theme.Motion
import io.github.xccxcxxc.runnel.ui.theme.listFling
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * iOS 式底部表单（page sheet）：从底部滑上来盖到状态栏下方，下层页面缩小、加圆角、变暗。
 * 同时只有一个表单；[content] 是当前（或正在收起的）表单，收起动画结束后才置空。
 * [progress]（0 = 收起，1 = 弹出）只在 graphicsLayer / drawBehind 里读，转场和下拉都不触发重组。
 */
@Stable
class SheetState<T : Any>(private val scope: CoroutineScope) {
    var content by mutableStateOf<T?>(null)
        private set
    var isOpen by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    internal var heightPx = 1f
    /** 这次手势里表单被往下拉过：之后往上推先把表单推回去，再滚动列表。 */
    private var dragged = false
    private var job: Job? = null

    fun show(what: T) {
        if (isOpen) return
        job?.cancel()
        content = what
        isOpen = true
        job = scope.launch {
            // 和页面推入一样：先让表单在屏幕外完成组合和布局（两帧），动画第一帧才不卡
            withFrameNanos { }
            withFrameNanos { }
            animate(progress, 1f, animationSpec = Motion.sheet) { v, _ -> progress = v }
        }
    }

    /** [velocity] 单位是进度/秒，收起方向为负。 */
    fun dismiss(velocity: Float = 0f) {
        if (!isOpen) return
        isOpen = false
        dragged = false
        animateTo(0f, velocity, if (velocity == 0f) Motion.sheet else Motion.sheetRelease) { content = null }
    }

    /** 不要动画直接收起（退出登录等整页替换时）。 */
    fun hideNow() {
        job?.cancel()
        isOpen = false
        dragged = false
        progress = 0f
        content = null
    }

    /** 手指下移 [dy] 像素（上移为负），返回表单实际跟着移动的像素。 */
    internal fun drag(dy: Float): Float {
        if (!isOpen || dy == 0f || (dy < 0f && !dragged)) return 0f
        job?.cancel()
        val old = progress
        progress = (old - dy / heightPx).coerceIn(0f, 1f)
        if (progress < 1f) dragged = true
        return (old - progress) * heightPx
    }

    /** 松手：[velocityY] 像素/秒，向下为正。拉得够远或甩得够快就收起，否则弹回。 */
    internal fun release(velocityY: Float) {
        if (!dragged || !isOpen) return
        dragged = false
        val v = velocityY / heightPx
        if (v > 1.2f || (progress < 0.65f && v > -0.3f)) dismiss(-v)
        else animateTo(1f, -v, Motion.sheetRelease) {}
    }

    private fun animateTo(target: Float, velocity: Float, spec: SpringSpec<Float>, then: () -> Unit) {
        job?.cancel()
        job = scope.launch {
            animate(progress, target, initialVelocity = velocity, animationSpec = spec) { v, _ -> progress = v }
            then()
        }
    }

    /** 表单里的列表滚到顶后继续下拉 → 拉动表单；拉过之后往上推 → 先把表单推回原位。 */
    internal val nestedScroll = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (source == NestedScrollSource.UserInput && available.y < 0f) Offset(0f, drag(available.y)) else Offset.Zero

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (source == NestedScrollSource.UserInput && available.y > 0f) Offset(0f, drag(available.y)) else Offset.Zero

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!dragged) return Velocity.Zero
            release(available.y)
            return available
        }
    }
}

private val SheetShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
private val BehindShape = RoundedCornerShape(12.dp)
val SheetBarHeight = 56.dp

/** 挂在表单下层的页面上：表单弹出时缩小、加圆角（iOS page sheet 的效果）。只改 graphicsLayer。 */
fun Modifier.behindSheet(state: SheetState<*>): Modifier = graphicsLayer {
    val p = state.progress
    val s = 1f - (1f - Motion.SHEET_BEHIND_SCALE) * p
    scaleX = s
    scaleY = s
    shape = BehindShape
    clip = p > 0f
}

/** 表单容器：放在页面栈之上，[SheetState.content] 为空时什么都不组合。 */
@Composable
fun <T : Any> SheetHost(state: SheetState<T>, content: @Composable (T) -> Unit) {
    val what = state.content ?: return
    BackHandler(enabled = state.isOpen) { state.dismiss() }
    val topGap = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp
    // 下拉手势挂在不随表单移动的外层上，用位移增量计算，坐标不会被表单自身的位移抵消
    Box(Modifier.fillMaxSize().sheetDrag(state)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = state.progress }
                .background(Color.Black.copy(alpha = 0.3f))
                .pointerInput(state) { detectTapGestures { state.dismiss() } },
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = topGap)
                .onSizeChanged { state.heightPx = it.height.toFloat().coerceAtLeast(1f) }
                .graphicsLayer {
                    translationY = (1f - state.progress) * size.height
                    shape = SheetShape
                    clip = true
                }
                .background(Runnel.colors.groupedBackground)
                .nestedScroll(state.nestedScroll)
                // 吃掉落在表单空白处的触摸，不让它穿到变暗层（点空白处不应收起）
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        ) { content(what) }
    }
}

/** 顶栏等不滚动的区域上下拉表单。列表自己滚动时会先消费事件，这里就不会响应。 */
private fun Modifier.sheetDrag(state: SheetState<*>): Modifier = pointerInput(state) {
    val tracker = VelocityTracker()
    var y = 0f
    detectVerticalDragGestures(
        onDragStart = {
            tracker.resetTracking()
            y = 0f
        },
        onDragEnd = { state.release(tracker.calculateVelocity().y) },
        onDragCancel = { state.release(0f) },
    ) { change, dy ->
        change.consume()
        y += dy
        tracker.addPosition(change.uptimeMillis, Offset(0f, y))
        state.drag(dy)
    }
}

/**
 * 表单里的二级页面栈（iOS 表单内的导航）：推入从右滑入、下层左移 30%，可从任意位置右滑返回。
 * 和 [io.github.xccxcxxc.runnel.ui.nav.NavHost] 一样只组合最上面两层，转场只改 graphicsLayer。
 */
@Stable
class SheetPages<P : Any>(root: P, private val scope: CoroutineScope) {
    val stack = mutableStateListOf(root)
    var progress by mutableFloatStateOf(1f)
        private set
    var transitioning by mutableStateOf(false)
        private set
    internal var widthPx = 1f
    val canPop: Boolean get() = stack.size > 1
    private var job: Job? = null

    fun push(page: P) {
        if (transitioning) return
        transitioning = true
        progress = 0f
        stack += page
        job = scope.launch {
            withFrameNanos { }
            animate(0f, 1f, animationSpec = Motion.navigation) { v, _ -> progress = v }
            transitioning = false
        }
    }

    fun pop() {
        if (stack.size <= 1 || transitioning) return
        transitioning = true
        job = scope.launch {
            animate(progress, 0f, animationSpec = Motion.navigation) { v, _ -> progress = v }
            removeTop()
        }
    }

    internal fun beginDrag(): Boolean {
        if (stack.size <= 1 || transitioning) return false
        transitioning = true
        return true
    }

    internal fun drag(dxPx: Float) {
        progress = (progress - dxPx / widthPx).coerceIn(0f, 1f)
    }

    /** [velocityX] 像素/秒，向右为正。 */
    internal fun endDrag(velocityX: Float) {
        val v = velocityX / widthPx
        val commit = v > 0.8f || (progress < 0.55f && v > -0.3f)
        job = scope.launch {
            if (commit) {
                animate(progress, 0f, initialVelocity = -v.coerceAtLeast(0f), animationSpec = Motion.navigationRelease) { p, _ -> progress = p }
                removeTop()
            } else {
                animate(progress, 1f, animationSpec = Motion.navigationRelease) { p, _ -> progress = p }
                transitioning = false
            }
        }
    }

    private fun removeTop() {
        stack.removeAt(stack.lastIndex)
        progress = 1f
        transitioning = false
    }
}

@Composable
fun <P : Any> SheetPageHost(pages: SheetPages<P>, content: @Composable (P) -> Unit) {
    // 比表单自己的返回处理后注册，优先级更高：有二级页面时先退回上一页
    BackHandler(enabled = pages.canPop) { pages.pop() }
    val background = Runnel.colors.groupedBackground
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { pages.widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pageSwipeBack(pages),
    ) {
        val stack = pages.stack
        val top = stack.last()
        val visible = if (stack.size >= 2) listOf(stack[stack.size - 2], top) else listOf(top)
        for (page in visible) {
            key(page) {
                val isTop = page == top
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = pages.progress
                            if (isTop) {
                                translationX = (1f - p) * size.width
                            } else {
                                translationX = -0.3f * p * size.width
                                alpha = if (p >= 0.999f && !pages.transitioning) 0f else 1f
                            }
                        }
                        .background(background)
                        .then(if (isTop) Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } } else Modifier),
                ) { content(page) }
            }
        }
    }
}

private fun Modifier.pageSwipeBack(pages: SheetPages<*>): Modifier = pointerInput(pages) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!pages.canPop) return@awaitEachGesture
        val tracker = VelocityTracker()
        var x = 0f
        tracker.addPosition(down.uptimeMillis, Offset.Zero)
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
                x += delta.x
                tracker.addPosition(change.uptimeMillis, Offset(x, 0f))
                if (!dragging) {
                    if (SwipeBackGuard.isClaimed(down.id)) return@awaitEachGesture
                    dx += delta.x
                    dy += delta.y
                    if (abs(dy) > slop && abs(dy) > abs(dx)) return@awaitEachGesture
                    if (dx < -slop) return@awaitEachGesture
                    if (dx > slop && dx > abs(dy) * 1.3f && pages.beginDrag()) dragging = true else continue
                }
                event.changes.forEach { it.consume() }
                pages.drag(delta.x)
            }
            if (dragging) {
                ended = true
                pages.endDrag(tracker.calculateVelocity().x)
            }
        } finally {
            if (dragging && !ended) pages.endDrag(0f)
        }
    }
}

/** 表单里的一页：顶栏（左右按钮 + 居中标题）+ 列表。列表滚动后顶栏下方出现分隔线。 */
@Composable
fun SheetPage(
    title: String,
    subtitle: String? = null,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val colors = Runnel.colors
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(SheetBarHeight)
                .drawBehind {
                    if (listState.canScrollBackward) {
                        drawLine(colors.separator, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
                    }
                },
        ) {
            Row(Modifier.align(Alignment.CenterStart).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically, content = leading)
            Column(Modifier.align(Alignment.Center).padding(horizontal = 88.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = Runnel.type.headline, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = Runnel.type.caption, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f).imePadding(),
            contentPadding = PaddingValues(bottom = navBottom + 24.dp),
            flingBehavior = listFling(),
            content = content,
        )
    }
}
