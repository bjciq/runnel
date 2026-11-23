package io.github.xccxcxxc.runnel.ui.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xccxcxxc.runnel.data.ImagePreview
import io.github.xccxcxxc.runnel.data.db.ArticleListItem
import io.github.xccxcxxc.runnel.ui.clockTime
import io.github.xccxcxxc.runnel.ui.components.RunnelIcons
import io.github.xccxcxxc.runnel.ui.components.FeedIcon
import io.github.xccxcxxc.runnel.ui.components.Thumbnail
import io.github.xccxcxxc.runnel.ui.components.pressHighlight
import io.github.xccxcxxc.runnel.ui.relativeTime
import io.github.xccxcxxc.runnel.ui.theme.Runnel

val ThumbSize = 72.dp

/** 设置里的“图片预览”对应的缩略图边长；关闭时为 null。 */
fun ImagePreview.thumbSize(): Dp? = when (this) {
    ImagePreview.NONE -> null
    ImagePreview.SMALL -> 52.dp
    ImagePreview.MEDIUM -> ThumbSize
    ImagePreview.LARGE -> 96.dp
}

/**
 * 文章行：左侧来源图标，右上角时间，来源名（灰小字）、标题、摘要；
 * 有缩略图时放在时间下方。没有分隔线，靠留白区分。已读的文章标题和摘要变淡。
 */
@Composable
fun ArticleRow(
    item: ArticleListItem,
    read: Boolean,
    starred: Boolean,
    showFeed: Boolean = true,
    clock: Boolean = true,
    showFavicon: Boolean = true,
    showPreview: Boolean = true,
    /** 缩略图边长；null = 不显示图片。 */
    thumbSize: Dp? = ThumbSize,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = Runnel.colors
    Row(
        Modifier
            .fillMaxWidth()
            .pressHighlight(onLongClick, item.id, onClick)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
    ) {
        if (showFavicon) {
            Column(Modifier.padding(top = 2.dp)) { FeedIcon(item.feedTitle, item.feedIcon, size = 18.dp, site = item.feedSite) }
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (showFeed) item.feedTitle else "",
                    style = Runnel.type.footnote,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (starred) {
                    Spacer(Modifier.width(5.dp))
                    Icon(RunnelIcons.Star, "星标", tint = colors.star, modifier = Modifier.size(12.dp))
                }
            }
            Spacer(Modifier.height(2.dp))
            // 标题和摘要占满这一列：宽度固定后 Compose 不再先量整段文字的“自然宽度”（真机进列表最重的一帧里约一半是它）
            Text(
                item.title,
                style = Runnel.type.body.copy(fontWeight = FontWeight.Medium, lineHeight = 23.sp),
                color = if (read) colors.secondaryLabel else colors.label,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            if (showPreview && item.summary.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    previewText(item.summary),
                    style = Runnel.type.subhead,
                    color = if (read) colors.tertiaryLabel else colors.secondaryLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (clock) clockTime(item.publishedAt) else relativeTime(item.publishedAt),
                style = Runnel.type.footnote,
                color = colors.secondaryLabel,
                maxLines = 1,
            )
            if (item.thumbUrl != null && thumbSize != null) {
                Spacer(Modifier.height(8.dp))
                Thumbnail(item.thumbUrl, size = thumbSize)
            }
        }
    }
}

/**
 * 摘要只显示两行：先截到两行肯定放得下的长度再排版，排版要先量整段（摘要最长 200 字），截短后进列表那一帧更轻。
 * 不在代理对中间截断。
 */
private fun previewText(summary: String): String {
    if (summary.length <= PREVIEW_CHARS) return summary
    val end = if (summary[PREVIEW_CHARS - 1].isHighSurrogate()) PREVIEW_CHARS - 1 else PREVIEW_CHARS
    return summary.substring(0, end)
}

/** 平板横屏一行也就六七十个汉字，两行 140 字足够，省略号照常出现。 */
private const val PREVIEW_CHARS = 140
