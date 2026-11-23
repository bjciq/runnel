package io.github.xccxcxxc.runnel.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.xccxcxxc.runnel.ui.theme.Runnel
import io.github.xccxcxxc.runnel.ui.theme.Motion
import kotlinx.coroutines.launch

class ContextMenuItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * iOS 式上下文菜单（长按文章）：被长按的那一行浮起成圆角卡片，
 * 其余部分变暗，卡片下方（放不下就上方，都放不下就把卡片挪开）弹出分组的带图标菜单。
 * 点卡片外面、返回键或选了某项后收起；收起动画结束卡片落回原位才调 [onClosed]——在那之前原来那一行要一直藏着。
 *
 * 放在页面最上层、和列表同一坐标系的 Box 里；[anchorTop] 是那一行在其中的顶边（px），[preview] 画同样的一行。
 */
@Composable
fun ContextMenuOverlay(
    anchorTop: Int,
    groups: List<List<ContextMenuItem>>,
    onClosed: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val colors = Runnel.colors
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { progress.animateTo(1f, Motion.ios(0.42f, 0.78f)) }
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            progress.animateTo(0f, Motion.ios(0.32f))
            onClosed()
        }
    }
    BackHandler(onBack = ::close)

    val scrim = if (colors.isDark) Color.Black.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.2f)
    // 按下就收起，并吃掉这一整次触摸：不让下面的列表滚动、也不触发页面的右滑返回
    Box(
        Modifier
            .fillMaxSize()
            .testTag("context_scrim")
            .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
            .background(scrim)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    close()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            },
    )

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Layout(
        content = {
            Box(Modifier.background(colors.background)) { preview() }
            ContextMenuPanel(groups) { item ->
                item.onClick()
                close()
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val margin = 16.dp.toPx()
        val gap = 12.dp.toPx()
        val card = measurables[0].measure(Constraints(minWidth = w, maxWidth = w))
        val menuW = minOf(250.dp.roundToPx(), w - 2 * margin.toInt())
        val menu = measurables[1].measure(Constraints(minWidth = menuW, maxWidth = menuW))
        // 卡片 = 那一行按比例缩小到左右各留 margin；缩放以中心为准，所以上下也各收进 inset
        val scale = (w - 2 * margin) / w
        val inset = card.height * (1 - scale) / 2
        val cardTop = anchorTop + inset
        val cardBottom = anchorTop + card.height - inset
        val topLimit = statusTop.toPx() + 8.dp.toPx()
        val bottomLimit = h - navBottom.toPx() - 8.dp.toPx()
        // 那一行被顶栏/底栏挡住一部分时先挪进来；菜单下方放不下放上方，都放不下就把卡片往上挪
        var shift = 0f
        if (cardTop < topLimit) shift = topLimit - cardTop
        if (cardBottom + shift > bottomLimit) shift = bottomLimit - cardBottom
        var below = cardBottom + shift + gap + menu.height <= bottomLimit
        val above = cardTop + shift - gap - menu.height >= topLimit
        if (!below && !above) {
            shift = maxOf(bottomLimit - gap - menu.height - cardBottom, topLimit - cardTop)
            below = true
        }
        val menuY = if (below) cardBottom + shift + gap else cardTop + shift - gap - menu.height
        val corner = 14.dp.toPx()
        val elevation = 12.dp.toPx()
        layout(w, h) {
            card.placeWithLayer(0, anchorTop) {
                val p = progress.value
                val s = 1f + (scale - 1f) * p
                scaleX = s
                scaleY = s
                translationY = shift * p
                shape = RoundedCornerShape(corner * p.coerceIn(0f, 1f) / scale)
                clip = true
                shadowElevation = elevation * p.coerceIn(0f, 1f)
            }
            menu.placeWithLayer(margin.toInt(), menuY.toInt()) {
                val p = progress.value
                alpha = p.coerceIn(0f, 1f)
                val s = 0.5f + 0.5f * p
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0.2f, if (below) 0f else 1f)
                // 卡片还在挪动时菜单跟着它走
                translationY = -shift * (1f - p)
            }
        }
    }
}

@Composable
private fun ContextMenuPanel(groups: List<List<ContextMenuItem>>, onSelect: (ContextMenuItem) -> Unit) {
    val colors = Runnel.colors
    val shape = RoundedCornerShape(22.dp)
    val panel = if (colors.isDark) Color(0xFF2C2C2E) else Color(0xFFF2F1EF)
    val fg = if (colors.isDark) Color.White else Color(0xFF1C1C1E)
    Column(
        Modifier
            .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
            .clip(shape)
            .background(panel)
            .padding(vertical = 8.dp)
            .testTag("context_menu"),
    ) {
        groups.forEachIndexed { i, group ->
            if (i > 0) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(1.dp).background(colors.separator))
                Spacer(Modifier.height(8.dp))
            }
            group.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .pressHighlight { onSelect(item) }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(item.icon, null, tint = fg, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(item.label, style = Runnel.type.body, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
