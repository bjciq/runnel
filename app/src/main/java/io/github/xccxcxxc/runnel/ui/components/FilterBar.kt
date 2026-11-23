package io.github.xccxcxxc.runnel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.xccxcxxc.runnel.data.FilterMode
import io.github.xccxcxxc.runnel.ui.theme.Runnel

/**
 * 底部筛选条：星标 / 未读 / 全部 三个图标居中，选中的那个变成带文字的灰色胶囊；
 * 两侧可以放额外按钮（列表页：左边搜索，右边全部已读）。
 */
@Composable
fun FilterBottomBar(
    current: FilterMode,
    onChange: (FilterMode) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Runnel.colors
    BottomToolbar(modifier) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { leading?.invoke() }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            for ((mode, icon, label) in listOf(
                Triple(FilterMode.STARRED, RunnelIcons.Star, "星标"),
                Triple(FilterMode.UNREAD, RunnelIcons.Circle, "未读"),
                Triple(FilterMode.ALL, RunnelIcons.List, "全部"),
            )) {
                val selected = mode == current
                Row(
                    Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) colors.highlight else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(remember { MutableInteractionSource() }, indication = null) { onChange(mode) }
                        .padding(horizontal = if (selected) 14.dp else 12.dp)
                        .testTag("filter_${mode.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, label, tint = colors.icon, modifier = Modifier.size(if (selected) 16.dp else 20.dp))
                    if (selected) {
                        Spacer(Modifier.width(7.dp))
                        Text(label, style = Runnel.type.footnote, color = colors.icon)
                    }
                }
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { trailing?.invoke() }
    }
}
