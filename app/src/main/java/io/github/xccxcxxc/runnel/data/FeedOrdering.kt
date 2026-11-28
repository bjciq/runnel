package io.github.xccxcxxc.runnel.data

import androidx.compose.runtime.Immutable
import io.github.xccxcxxc.runnel.data.db.FeedEntity
import io.github.xccxcxxc.runnel.data.db.FolderEntity
import java.text.Collator
import java.util.Locale

/**
 * 首页与“下一个订阅源”共用的排序快照：从首页进入列表时带上当时的视觉顺序，
 * 即使阅读过程中当前源的未读数变化（按未读数排序时名次会动），继续上拉仍严格按首页从上到下的顺序前进。
 */
@Immutable
data class SourceSequence(
    val filter: FilterMode,
    val sortSubs: SortSubs,
    val ids: List<String>,
)

data class OrderedFolder(
    val folder: FolderEntity,
    val feeds: List<FeedEntity>,
    val count: Int,
)

data class OrderedSubs(
    val folders: List<OrderedFolder>,
    val loose: List<FeedEntity>,
    val feedsFirst: Boolean,
) {
    /** 首页从上到下的全部订阅源顺序（含文件夹内和未归类）。 */
    val orderedFeeds: List<FeedEntity>
        get() = if (feedsFirst) loose + folders.flatMap { it.feeds } else folders.flatMap { it.feeds } + loose

    /** 首页从上到下的全部文件夹顺序。 */
    val orderedFolders: List<FolderEntity>
        get() = folders.map { it.folder }
}

/** 首页展示和“下一个订阅源”共用的订阅源/文件夹排序逻辑，确保两处顺序完全一致。 */
object FeedOrdering {
    fun order(
        folders: List<FolderEntity>,
        feeds: List<FeedEntity>,
        counts: Map<String, Int>,
        sortSubs: SortSubs,
        hideEmpty: Boolean = false,
        locale: Locale = Locale.getDefault(),
    ): OrderedSubs {
        val baseFolders = folders.sortedWith(compareBy({ it.sortIndex }, { it.title.lowercase(locale) }))
        val baseFeeds = feeds.sortedWith(compareBy({ it.sortIndex }, { it.title.lowercase(locale) }))
        val visibleFeeds = if (hideEmpty) baseFeeds.filter { (counts[it.id] ?: 0) > 0 } else baseFeeds
        val byFolder = visibleFeeds.groupBy { it.folderId }
        val folderIds = baseFolders.mapTo(HashSet()) { it.id }

        var orderedFolders = baseFolders.mapNotNull { folder ->
            val list = byFolder[folder.id].orEmpty()
            if (list.isEmpty()) null
            else OrderedFolder(folder, list, list.sumOf { counts[it.id] ?: 0 })
        }
        var orderedLoose = visibleFeeds.filter { it.folderId == null || it.folderId !in folderIds }

        when (sortSubs) {
            SortSubs.ALPHA -> {
                val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
                val byTitle = Comparator<String> { a, b ->
                    val c = collator.compare(a, b)
                    if (c != 0) c else a.lowercase(locale).compareTo(b.lowercase(locale))
                }
                orderedFolders = orderedFolders
                    .map { it.copy(feeds = it.feeds.sortedWith(compareBy(byTitle) { f -> f.title })) }
                    .sortedWith(compareBy(byTitle) { it.folder.title })
                orderedLoose = orderedLoose.sortedWith(compareBy(byTitle) { it.title })
            }
            SortSubs.UNREAD -> {
                orderedFolders = orderedFolders
                    .map { it.copy(feeds = it.feeds.sortedByDescending { f -> counts[f.id] ?: 0 }) }
                    .sortedByDescending { it.count }
                orderedLoose = orderedLoose.sortedByDescending { counts[it.id] ?: 0 }
            }
            SortSubs.FEEDS_FIRST, SortSubs.FOLDERS_FIRST -> Unit
        }

        return OrderedSubs(
            folders = orderedFolders,
            loose = orderedLoose,
            feedsFirst = sortSubs == SortSubs.FEEDS_FIRST,
        )
    }

    /**
     * 找出 [current] 之后第一个有内容（[hasCounts] > 0）的订阅源或文件夹。
     * 优先使用进入列表时从首页带过来的 [sequence]（同筛选、同排序方式），避免阅读中未读数变动导致顺序错乱；
     * 否则按 [sortSubs] 与 [sortCounts] 现算，顺序仍与首页一致。
     */
    fun nextSource(
        current: ArticleSource,
        filter: FilterMode,
        folders: List<FolderEntity>,
        feeds: List<FeedEntity>,
        hasCounts: Map<String, Int>,
        sortCounts: Map<String, Int>,
        sortSubs: SortSubs,
        sequence: SourceSequence? = null,
        locale: Locale = Locale.getDefault(),
    ): ArticleSource? {
        if (current is ArticleSource.All) return null
        fun has(feedId: String) = (hasCounts[feedId] ?: 0) > 0
        fun FeedEntity.toSource() = ArticleSource.Feed(id, title, iconUrl ?: localIcon?.takeIf(String::isNotEmpty))

        val activeSequence = sequence?.takeIf { it.filter == filter && it.sortSubs == sortSubs }
        return when (current) {
            is ArticleSource.Feed -> {
                if (activeSequence != null) {
                    val idx = activeSequence.ids.indexOf(current.id)
                    if (idx >= 0) {
                        val byId = feeds.associateBy { it.id }
                        return activeSequence.ids.drop(idx + 1)
                            .mapNotNull { byId[it] }
                            .firstOrNull { has(it.id) }
                            ?.toSource()
                    }
                }
                val ordered = order(folders, feeds, sortCounts, sortSubs, hideEmpty = false, locale = locale).orderedFeeds
                val idx = ordered.indexOfFirst { it.id == current.id }
                if (idx < 0) return null
                ordered.drop(idx + 1).firstOrNull { has(it.id) }?.toSource()
            }
            is ArticleSource.Folder -> {
                val byFolder = feeds.groupBy { it.folderId }
                fun folderHas(folderId: String) = byFolder[folderId].orEmpty().any { has(it.id) }
                if (activeSequence != null) {
                    val idx = activeSequence.ids.indexOf(current.id)
                    if (idx >= 0) {
                        val byId = folders.associateBy { it.id }
                        return activeSequence.ids.drop(idx + 1)
                            .mapNotNull { byId[it] }
                            .firstOrNull { folderHas(it.id) }
                            ?.let { ArticleSource.Folder(it.id, it.title) }
                    }
                }
                val ordered = order(folders, feeds, sortCounts, sortSubs, hideEmpty = false, locale = locale).orderedFolders
                val idx = ordered.indexOfFirst { it.id == current.id }
                if (idx < 0) return null
                ordered.drop(idx + 1).firstOrNull { folderHas(it.id) }
                    ?.let { ArticleSource.Folder(it.id, it.title) }
            }
            ArticleSource.All -> null
        }
    }
}
