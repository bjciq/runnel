package io.github.xccxcxxc.runnel.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.xccxcxxc.runnel.ui.nav.BlockSwipeBack
import io.github.xccxcxxc.runnel.ui.theme.Motion
import io.github.xccxcxxc.runnel.ui.theme.Runnel

class TopMenuItem(
    val label: String,
    val checked: Boolean = false,
    val accent: Boolean = false,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

class TopMenuSection(
    val title: String? = null,
    val items: List<TopMenuItem>,
)

/**
 * iOS UIMenu 风格的顶栏右上角弹出菜单（参考 Reeder 的排序/分组菜单）：
 * 从右上角弹簧展开，分组间有柔和间隔，点外部或返回键收起。
 */
@Composable
fun TopMenuOverlay(
    visible: Boolean,
    sections: List<TopMenuSection>,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    BlockSwipeBack(visible)
    val colors = Runnel.colors
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val panelBg = if (colors.isDark) Color(0xFF2C2C2E) else Color(0xFFF7F6F4)
    val sectionGapBg = if (colors.isDark) Color(0xFF1F1F21) else Color(0xFFEAE8E5)
    val fg = if (colors.isDark) Color.White else Color(0xFF1C1C1E)
    val scrim = if (colors.isDark) Color.Black.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.12f)
    val shape = RoundedCornerShape(16.dp)

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(120)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(scrim)
                    .testTag("top_menu_scrim")
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(130)) + scaleIn(
                animationSpec = Motion.ios(0.32f, 0.86f),
                initialScale = 0.85f,
                transformOrigin = TransformOrigin(1f, 0f),
            ),
            exit = fadeOut(tween(110)) + scaleOut(
                animationSpec = tween(110),
                targetScale = 0.9f,
                transformOrigin = TransformOrigin(1f, 0f),
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = statusTop + BarHeight + 4.dp, end = 12.dp),
        ) {
            Column(
                Modifier
                    .width(236.dp)
                    .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.24f), spotColor = Color.Black.copy(alpha = 0.24f))
                    .clip(shape)
                    .background(panelBg)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .testTag("top_menu"),
            ) {
                sections.filter { it.items.isNotEmpty() }.forEachIndexed { sectionIndex, section ->
                    if (sectionIndex > 0) {
                        Box(Modifier.fillMaxWidth().height(6.dp).background(sectionGapBg))
                    }
                    if (section.title != null) {
                        Text(
                            section.title,
                            style = Runnel.type.caption,
                            color = colors.secondaryLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                        )
                    }
                    section.items.forEachIndexed { itemIndex, item ->
                        if (itemIndex > 0) {
                            Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(colors.separator))
                        }
                        val textColor = when {
                            item.destructive -> colors.destructive
                            item.accent -> colors.accent
                            else -> fg
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .pressHighlight {
                                    onDismiss()
                                    item.onClick()
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                item.label,
                                style = Runnel.type.callout,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (item.checked) {
                                Spacer(Modifier.width(10.dp))
                                Icon(RunnelIcons.Check, "已选", tint = colors.accent, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
