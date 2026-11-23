package io.github.xccxcxxc.runnel.ui.list

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 滚动时预取前方 [ahead] 行的缩略图（与 Thumbnail 同尺寸，命中同一个内存缓存），
 * 行滑进屏幕时图片已就绪。[rows] 的下标 + 1 就是 LazyColumn 的 item 下标（第 0 项是大标题）。
 */
@Composable
fun PrefetchThumbnails(listState: LazyListState, rows: List<ListRow>, size: Dp, ahead: Int = 14) {
    val context = LocalPlatformContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    LaunchedEffect(listState, rows, px) {
        val loader = SingletonImageLoader.get(context)
        val requested = HashSet<String>()
        snapshotFlow {
            val v = listState.layoutInfo.visibleItemsInfo
            // 向下滚预取下方，向上滚预取上方：简单起见两边都预取
            (v.firstOrNull()?.index ?: 0) to (v.lastOrNull()?.index ?: 0)
        }
            .distinctUntilChanged()
            .collect { (first, last) ->
                for (i in (first - ahead / 2)..(last + ahead)) {
                    val url = (rows.getOrNull(i - 1) as? ListRow.Article)?.item?.thumbUrl ?: continue
                    if (requested.add(url)) loader.enqueue(ImageRequest.Builder(context).data(url).size(px).build())
                }
            }
    }
}
