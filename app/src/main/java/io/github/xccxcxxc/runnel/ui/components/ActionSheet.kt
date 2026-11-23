package io.github.xccxcxxc.runnel.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.xccxcxxc.runnel.ui.theme.Runnel

class SheetAction(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * iOS 式操作菜单（Action sheet）：底部一组圆角按钮 + 单独的“取消”。点了某项先关闭再执行。
 * 用在列表行侧滑“操作菜单”和长按行。
 */
@Composable
fun ActionSheet(title: String?, actions: List<SheetAction>, onDismiss: () -> Unit) {
    val colors = Runnel.colors
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            AnimatedVisibility(
                visibleState = visible,
                enter = slideInVertically(spring(dampingRatio = 1f, stiffness = 300f)) { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.cell)) {
                        if (title != null) {
                            Text(
                                title,
                                style = Runnel.type.footnote,
                                color = colors.secondaryLabel,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            )
                            RowDivider()
                        }
                        actions.forEachIndexed { i, action ->
                            if (i > 0) RowDivider()
                            SheetButton(action.label, if (action.destructive) colors.destructive else colors.accent) {
                                onDismiss()
                                action.onClick()
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.cell)) {
                        SheetButton("取消", colors.accent, bold = true, onClick = onDismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetButton(label: String, color: androidx.compose.ui.graphics.Color, bold: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).pressHighlight(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Runnel.type.body.copy(fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal), color = color)
    }
}
