package io.github.xccxcxxc.runnel.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.xccxcxxc.runnel.ui.theme.Runnel
import io.github.xccxcxxc.runnel.ui.theme.Motion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

@Immutable
data class SwipeAction(val icon: ImageVector, val color: Color, val onTrigger: () -> Unit)

/**
 * 行侧滑：向右滑露出 [start]（标已读），向左滑露出 [end]（加星标）。
 * 拖过阈值时震动一下、背景变成动作颜色，松手执行并弹回。
 * 拖动只改位移（layout 阶段）和绘制，不触发重组；只有越过阈值那一刻改一次状态。
 */
@Composable
fun SwipeRow(
    start: SwipeAction?,
    end: SwipeAction?,
    modifier: Modifier = Modifier,
    key: Any? = Unit,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val colors = Runnel.colors
    val threshold = with(LocalDensity.current) { 76.dp.toPx() }
    val startAction = remember(key) { mutableStateOf(start) }.apply { value = start }
    val endAction = remember(key) { mutableStateOf(end) }.apply { value = end }
    val scope = rememberCoroutineScope()
    val offset = remember(key) { mutableFloatStateOf(0f) }
    val armed = remember(key) { mutableStateOf(false) }
    val settle = remember(key) { arrayOfNulls<Job>(1) }

    Box(
        modifier.pointerInput(key) {
            var finger = 0f
            fun finish(trigger: Boolean) {
                val action = if (offset.floatValue > 0) startAction.value else endAction.value
                if (trigger && armed.value) action?.onTrigger?.invoke()
                armed.value = false
                settle[0] = scope.launch {
                    animate(offset.floatValue, 0f, animationSpec = Motion.snappy) { v, _ -> offset.floatValue = v }
                }
            }
            detectHorizontalDragGestures(
                onDragStart = {
                    settle[0]?.cancel()
                    finger = offset.floatValue
                },
                onDragEnd = { finish(trigger = true) },
                onDragCancel = { finish(trigger = false) },
                onHorizontalDrag = { change, dx ->
                    finger += dx
                    if (finger > 0 && startAction.value == null || finger < 0 && endAction.value == null) finger = 0f
                    // 超过阈值后阻尼跟手
                    val a = abs(finger)
                    val shown = if (a <= threshold) a else threshold + sqrt(a - threshold) * 9f
                    offset.floatValue = shown * sign(finger)
                    change.consume()
                    val nowArmed = shown >= threshold
                    if (nowArmed != armed.value) {
                        armed.value = nowArmed
                        val feedback = when {
                            nowArmed && Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
                            nowArmed -> HapticFeedbackConstants.CONTEXT_CLICK
                            Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE
                            else -> HapticFeedbackConstants.CLOCK_TICK
                        }
                        view.performHapticFeedback(feedback)
                    }
                },
            )
        },
    ) {
        // 动作层：只在拖动时画
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    val x = offset.floatValue
                    if (x == 0f) return@drawBehind
                    val action = (if (x > 0) startAction.value else endAction.value) ?: return@drawBehind
                    val base = if (colors.isDark) Color(0xFF3A3A3C) else Color(0xFFC7C7CC)
                    drawRect(if (armed.value) action.color else lerp(base, action.color, 0.25f))
                },
        )
        start?.let { action ->
            Icon(
                action.icon, null, tint = Color.White,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 26.dp)
                    .size(26.dp)
                    .graphicsLayer {
                        val p = (offset.floatValue / threshold).coerceIn(0f, 1.2f)
                        alpha = if (offset.floatValue > 0) p.coerceAtMost(1f) else 0f
                        val s = if (armed.value) 1.15f else 0.7f + 0.3f * p.coerceAtMost(1f)
                        scaleX = s; scaleY = s
                    },
            )
        }
        end?.let { action ->
            Icon(
                action.icon, null, tint = Color.White,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 26.dp)
                    .size(26.dp)
                    .graphicsLayer {
                        val p = (-offset.floatValue / threshold).coerceIn(0f, 1.2f)
                        alpha = if (offset.floatValue < 0) p.coerceAtMost(1f) else 0f
                        val s = if (armed.value) 1.15f else 0.7f + 0.3f * p.coerceAtMost(1f)
                        scaleX = s; scaleY = s
                    },
            )
        }
        Box(
            Modifier
                .offset { IntOffset(offset.floatValue.roundToInt(), 0) }
                .background(colors.background),
        ) { content() }
    }
}
