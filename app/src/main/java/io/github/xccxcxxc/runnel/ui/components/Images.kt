package io.github.xccxcxxc.runnel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import io.github.xccxcxxc.runnel.ui.theme.Runnel
import kotlin.math.abs

val LocalGrayIcons = androidx.compose.runtime.staticCompositionLocalOf { false }

/** 站点的 favicon 地址：origin + /favicon.ico。 */
private fun faviconOf(site: String?): String? {
    if (site.isNullOrBlank()) return null
    return runCatching { java.net.URI(site).let { u -> "${u.scheme}://${u.host}${if (u.port > 0) ":${u.port}" else ""}/favicon.ico" } }.getOrNull()
}

/**
 * 订阅源图标：先画灰色圆角方块加订阅源名称的首字；服务端给的图标加载成功后盖在上面，
 * 服务端没有或加载失败就退而求其次用站点自己的 /favicon.ico，都没有就保持首字方块。
 * 设置里可以打开“灰度图标”，把图标统一变灰。
 */
@Composable
fun FeedIcon(title: String, url: String?, size: Dp = 16.dp, site: String? = null) {
    val colors = Runnel.colors
    val candidates = remember(url, site) { listOfNotNull(url?.takeIf { it.isNotBlank() }, faviconOf(site)).distinct() }
    var index by remember(candidates) { mutableIntStateOf(0) }
    val gray = LocalGrayIcons.current
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 4.5f)).background(colors.highlight),
        contentAlignment = Alignment.Center,
    ) {
        val letterSize = with(LocalDensity.current) { (size * 0.58f).toSp() }
        Text(
            initialOf(title),
            color = colors.secondaryLabel,
            fontSize = letterSize,
            lineHeight = letterSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        val current = candidates.getOrNull(index)
        if (current != null) {
            val px = with(LocalDensity.current) { size.roundToPx() }
            val ctx = LocalPlatformContext.current
            val request = remember(current, px) { ImageRequest.Builder(ctx).data(current).size(px * 2).build() }
            AsyncImage(
                request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = if (gray) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
                onError = { index++ },
                modifier = Modifier.fillMaxSize().background(colors.background),
            )
        }
    }
}

/** 名称的第一个字（按码点取，emoji 不会被劈开），字母转大写；名称为空时不显示。 */
internal fun initialOf(title: String): String {
    val t = title.trim()
    if (t.isEmpty()) return ""
    return String(Character.toChars(t.codePointAt(0))).uppercase()
}

/** 列表缩略图：按显示尺寸解码，不做淡入（滚动时淡入反而显得慢）。 */
@Composable
fun Thumbnail(url: String, size: Dp, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val ctx = LocalPlatformContext.current
    val request = remember(url, px) { ImageRequest.Builder(ctx).data(url).size(px).build() }
    AsyncImage(
        request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(RoundedCornerShape(5.dp)).background(Runnel.colors.highlight),
    )
}
