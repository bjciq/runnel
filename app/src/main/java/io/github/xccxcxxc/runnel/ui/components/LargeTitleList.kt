package io.github.xccxcxxc.runnel.ui.components

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.xccxcxxc.runnel.perf.PerfMonitor
import io.github.xccxcxxc.runnel.ui.theme.Runnel
import io.github.xccxcxxc.runnel.ui.theme.listFling

/**
 * iOS 大标题列表：大标题是列表第一项，随内容滚走；滚过之后顶栏淡入小标题。
 * 可选下拉刷新和底部工具栏，列表内容自动让出顶栏/工具栏/系统栏的空间。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeTitleList(
    title: String,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    listModifier: Modifier = Modifier,
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    background: Color = Runnel.colors.background,
    subtitle: String? = null,
    subtitleColor: Color? = null,
    titleStyle: TextStyle? = null,
    contentStart: Dp = 20.dp,
    navigation: @Composable (BoxScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    titleExtra: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val titleHeightPx = with(LocalDensity.current) { 50.dp.toPx() }
    val collapse = remember(listState) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / titleHeightPx).coerceIn(0f, 1f)
        }
    }
    // 帧统计：卡顿时是否正在滚动（含惯性）
    LaunchedEffect(listState) { snapshotFlow { listState.isScrollInProgress }.collect { PerfMonitor.setScrolling(listState, it) } }
    DisposableEffect(listState) { onDispose { PerfMonitor.setScrolling(listState, false) } }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 键盘弹出时（Filter 输入框）列表末尾要能滑到键盘上方；键盘收起后回到原来的底部留白
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val bottomPad = maxOf(navBottom + (if (bottomBar != null) ToolbarHeight else 0.dp), imeBottom) + 12.dp
    // 内容不满一屏时在末尾垫高到“比可视区多 1px”：Compose 只在列表能滚动时才做橡皮筋回弹，
    // 短列表（只有几篇的订阅源）拉不动、上拉松手直接跳走。iOS 的列表不管长短都回弹（alwaysBounceVertical）
    val fillerPx = remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val items = info.visibleItemsInfo.filter { it.key != FILLER_KEY }
            if (items.isEmpty() || items.first().index != 0 || items.size < info.totalItemsCount - 1) return@derivedStateOf 0
            val used = items.last().offset + items.last().size - items.first().offset
            val available = info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding
            (available - used + 1).coerceAtLeast(0)
        }
    }

    Box(modifier.fillMaxSize().background(background)) {
        val list: @Composable () -> Unit = {
            LazyColumn(
                state = listState,
                modifier = listModifier.fillMaxSize(),
                contentPadding = PaddingValues(top = statusTop + BarHeight, bottom = bottomPad),
                flingBehavior = listFling(),
                overscrollEffect = overscrollEffect,
            ) {
                item(key = "__large_title", contentType = "large_title") {
                    Column {
                        Column(Modifier.padding(start = contentStart, end = 16.dp, top = 2.dp, bottom = 10.dp)) {
                            Text(
                                title,
                                style = titleStyle ?: Runnel.type.largeTitle,
                                color = Runnel.colors.label,
                                maxLines = 2,
                            )
                            if (subtitle != null) {
                                Text(subtitle, style = Runnel.type.subhead, color = subtitleColor ?: Runnel.colors.secondaryLabel, maxLines = 1)
                            }
                        }
                        // 放在标题这一项里而不是单独一项：列表其余行的下标不变（阅读页回滚、滚动标已读都按下标算）
                        titleExtra?.invoke()
                    }
                }
                content()
                item(key = FILLER_KEY, contentType = FILLER_KEY) {
                    Spacer(Modifier.fillMaxWidth().height(with(LocalDensity.current) { fillerPx.value.toDp() }))
                }
            }
        }
        if (onRefresh != null) {
            val state = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = onRefresh,
                state = state,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = state,
                        isRefreshing = refreshing,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = statusTop + BarHeight),
                        containerColor = Runnel.colors.cell,
                        color = Runnel.colors.accent,
                    )
                },
            ) { list() }
        } else {
            list()
        }
        TopBar(title, collapse, Modifier.align(Alignment.TopCenter), navigation, actions)
        if (bottomBar != null) Box(Modifier.align(Alignment.BottomCenter)) { bottomBar() }
        overlay()
    }
}

private const val FILLER_KEY = "__bounce_filler"
