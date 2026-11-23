package io.github.xccxcxxc.runnel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.xccxcxxc.runnel.ui.nav.SwipeBackGuard
import io.github.xccxcxxc.runnel.ui.theme.Runnel
import io.github.xccxcxxc.runnel.ui.theme.Motion
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** iOS 设置页风格的圆角分组。 */
@Composable
fun GroupedSection(title: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (title != null) {
            Text(
                title,
                style = Runnel.type.footnote,
                color = Runnel.colors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 7.dp),
            )
        } else {
            Box(Modifier.padding(top = 22.dp))
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Runnel.colors.cell),
            content = content,
        )
        if (footer != null) {
            Text(
                footer,
                style = Runnel.type.footnote,
                color = Runnel.colors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
            )
        }
    }
}

@Composable
fun GroupedRow(
    title: String,
    value: String? = null,
    titleColor: Color = Runnel.colors.label,
    valueColor: Color = Runnel.colors.secondaryLabel,
    centered: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressHighlight(onClick) else Modifier)
            .heightIn(min = 46.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.SpaceBetween,
    ) {
        Text(title, style = Runnel.type.body, color = titleColor, modifier = Modifier.weight(1f, fill = false))
        if (value != null) {
            Text(
                value, style = Runnel.type.body, color = valueColor, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp),
            )
        }
        trailing?.invoke()
    }
}

/** 下拉选择行（如 “Default Browser ⌄”）：右侧显示当前值和小箭头，点开是菜单。 */
@Composable
fun <T> PickerRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = Runnel.colors
    GroupedRow(title, value = options.firstOrNull { it.first == selected }?.second, onClick = { open = true }) {
        Box {
            Icon(RunnelIcons.ChevronDown, null, tint = colors.tertiaryLabel, modifier = Modifier.padding(start = 6.dp).size(16.dp))
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.cell) {
                for ((value, label) in options) {
                    DropdownMenuItem(
                        text = { Text(label, style = Runnel.type.body, color = if (value == selected) colors.accent else colors.label) },
                        onClick = {
                            open = false
                            onSelect(value)
                        },
                    )
                }
            }
        }
    }
}

/** 滑块行（如 Size / Spacing）：左边名称，中间滑块，右边灰底小标签显示当前值。 */
@Composable
fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: String,
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
) {
    val colors = Runnel.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Runnel.type.body, color = if (enabled) colors.label else colors.tertiaryLabel, modifier = Modifier.width(88.dp))
        IosSlider(value, onChange, range, steps, enabled, Modifier.weight(1f))
        Box(
            Modifier
                .padding(start = 12.dp)
                .width(46.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (enabled) colors.secondaryLabel else colors.separator),
            contentAlignment = Alignment.Center,
        ) {
            Text(display, style = Runnel.type.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Color.White, maxLines = 1)
        }
    }
}

private val ThumbWidth = 38.dp
private val ThumbHeight = 24.dp
private val TrackHeight = 4.dp

/**
 * iOS 样式的滑块（阅读设置面板）：深灰细轨道 + 带阴影的白色胶囊圆点。
 * 手势照 UISlider：按在圆点上立刻接管，之后往哪个方向划都只拖滑块，外层不会滚动；
 * 按在轨道别处：点一下跳到那一档，横向拖过 touch slop 才开始拖，竖着划交给外层滚动。
 * 拖动时圆点连续跟手，数值按档位取；松手后圆点吸附到那一档。
 */
@Composable
private fun IosSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = Runnel.colors
    val span = range.endInclusive - range.start
    fun fractionOf(v: Float) = if (span == 0f) 0f else ((v - range.start) / span).coerceIn(0f, 1f)
    fun snap(f: Float) = if (steps > 0) (f * (steps + 1)).roundToInt().toFloat() / (steps + 1) else f
    fun valueAt(f: Float) = range.start + snap(f.coerceIn(0f, 1f)) * span

    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onValueChange)
    val scope = rememberCoroutineScope()
    // 不拖时圆点的位置（0..1），数值从外面变了（点按、恢复默认）就弹过去
    val settled = remember { Animatable(fractionOf(value)) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableIntStateOf(0) }
    fun thumbFraction() = if (dragging) dragFraction else settled.value
    LaunchedEffect(value, range) {
        if (!dragging) settled.animateTo(fractionOf(value), Motion.ios(0.3f))
    }

    Box(
        modifier
            .height(44.dp)
            .onSizeChanged { widthPx = it.width }
            // 自成一个无障碍节点，不被外层可点击的面板合并掉（TalkBack 要能单独调）
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(value, range, steps)
                if (enabled) setProgress { v -> latestOnChange(valueAt(fractionOf(v))); true } else disabled()
            }
            .pointerInput(enabled, range, steps) {
                if (!enabled) return@pointerInput
                val thumbW = ThumbWidth.toPx()
                val grabSlack = 10.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // 按在滑块上（圆点或轨道）就不让全屏右滑返回接手
                    SwipeBackGuard.claim(down.id)
                    try {
                        val track = (size.width - thumbW).coerceAtLeast(1f)
                        fun fractionAtX(x: Float) = ((x - thumbW / 2) / track).coerceIn(0f, 1f)
                        val thumbCenter = thumbW / 2 + thumbFraction() * track
                        // 手指相对圆点中心的偏移：按住圆点拖时保持不变，圆点不会跳到手指正下方
                        var grab = 0f
                        val start = if (abs(down.position.x - thumbCenter) <= thumbW / 2 + grabSlack) {
                            down.consume()
                            grab = down.position.x - thumbCenter
                            down
                        } else {
                            awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        }
                        if (start == null) {
                            // 没拖起来：手指抬起且没被外层（滚动）抢走就是点按，跳到那一档
                            val up = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (up != null && up.changedToUp()) {
                                up.consume()
                                latestOnChange(valueAt(fractionAtX(up.position.x)))
                            }
                            return@awaitEachGesture
                        }
                        var lastStep = snap(fractionOf(latestValue))
                        fun moveTo(x: Float) {
                            dragFraction = fractionAtX(x - grab)
                            val step = snap(dragFraction)
                            if (step != lastStep) {
                                lastStep = step
                                latestOnChange(valueAt(step))
                            }
                        }
                        dragFraction = thumbFraction()
                        dragging = true
                        try {
                            moveTo(start.position.x)
                            drag(start.id) { moveTo(it.position.x); it.consume() }
                        } finally {
                            val from = dragFraction
                            scope.launch {
                                settled.snapTo(from)
                                dragging = false
                                settled.animateTo(fractionOf(latestValue), Motion.ios(0.3f))
                            }
                        }
                    } finally {
                        SwipeBackGuard.release(down.id)
                    }
                }
            }
            .drawBehind {
                val h = TrackHeight.toPx()
                val inset = ThumbWidth.toPx() / 2
                drawRoundRect(
                    if (enabled) colors.secondaryLabel else colors.separator,
                    topLeft = Offset(inset, (size.height - h) / 2),
                    size = Size(size.width - inset * 2, h),
                    cornerRadius = CornerRadius(h / 2),
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                // 只在布局阶段读位置：拖动时不重组
                .offset { IntOffset(((widthPx - ThumbWidth.roundToPx()) * thumbFraction()).roundToInt(), 0) }
                .size(ThumbWidth, ThumbHeight)
                .shadow(if (enabled) 3.dp else 1.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/** 一小组并排的方块按钮（如 B / Abc ABC / 左对齐 居中）：选中的深灰底白字。 */
@Composable
fun <T> SquareToggleGroup(options: List<T>, selected: T, onSelect: (T) -> Unit, label: @Composable (T, Boolean) -> Unit) {
    val colors = Runnel.colors
    Row(Modifier.clip(RoundedCornerShape(7.dp))) {
        for (o in options) {
            val on = o == selected
            Box(
                Modifier
                    .size(width = 48.dp, height = 34.dp)
                    .background(if (on) colors.secondaryLabel else colors.highlight)
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(o) },
                contentAlignment = Alignment.Center,
            ) { label(o, on) }
        }
    }
}

/** 进入下一级页面的行：右侧灰色箭头。 */
@Composable
fun NavRow(title: String, value: String? = null, onClick: () -> Unit) {
    GroupedRow(title, value = value, onClick = onClick) {
        Icon(RunnelIcons.ChevronRight, null, tint = Runnel.colors.tertiaryLabel, modifier = Modifier.padding(start = 6.dp).size(16.dp))
    }
}

/** 单选列表里的一行：选中时右侧打勾。 */
@Composable
fun CheckRow(title: String, checked: Boolean, onClick: () -> Unit) {
    GroupedRow(title, onClick = onClick) {
        if (checked) Icon(RunnelIcons.Check, "已选", tint = Runnel.colors.accent, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    GroupedRow(title, onClick = { onChange(!checked) }) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Runnel.colors.accent,
                checkedThumbColor = Color.White,
                checkedBorderColor = Color.Transparent,
                uncheckedTrackColor = Runnel.colors.highlight,
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/** 分段选择（选项少时用）。 */
@Composable
fun <T> SegmentedRow(title: String?, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val colors = Runnel.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (title != null) Text(title, style = Runnel.type.body, color = colors.label, modifier = Modifier.padding(bottom = 8.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(colors.highlight).padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for ((value, label) in options) {
                val on = value == selected
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (on) colors.cell else Color.Transparent)
                        .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(value) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = Runnel.type.footnote, color = if (on) colors.label else colors.secondaryLabel, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(Runnel.colors.separator))
}
