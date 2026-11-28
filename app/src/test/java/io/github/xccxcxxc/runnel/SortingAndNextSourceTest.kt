package io.github.xccxcxxc.runnel

import android.content.Context
import androidx.compose.ui.test.performClick
import io.github.xccxcxxc.runnel.data.AccountPrefs
import kotlinx.coroutines.cancel
import io.github.xccxcxxc.runnel.data.AccountSettings
import io.github.xccxcxxc.runnel.data.ArticleSort
import io.github.xccxcxxc.runnel.data.ArticleSource
import io.github.xccxcxxc.runnel.data.FeedOrdering
import io.github.xccxcxxc.runnel.data.FilterMode
import io.github.xccxcxxc.runnel.data.GroupBy
import io.github.xccxcxxc.runnel.data.Settings
import io.github.xccxcxxc.runnel.data.SortSubs
import io.github.xccxcxxc.runnel.data.SourceSequence
import io.github.xccxcxxc.runnel.data.db.ArticleListItem
import io.github.xccxcxxc.runnel.data.db.FeedEntity
import io.github.xccxcxxc.runnel.data.db.FolderEntity
import io.github.xccxcxxc.runnel.ui.list.ArticleListState
import io.github.xccxcxxc.runnel.ui.list.ListRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import java.util.Locale

class FeedOrderingAndSortTest {
    private val fTech = FolderEntity("folder/tech", "Tech", sortIndex = 0)
    private val fNews = FolderEntity("folder/news", "News", sortIndex = 1)

    // 模拟真实场景：Linux.do sortIndex 靠后且未读数很多，验证各种排序下都不会乱跳到 Linux.do
    private val feedZeta = FeedEntity("feed/zeta", "Zeta Blog", fTech.id, null, null, null, sortIndex = 0)
    private val feedAlpha = FeedEntity("feed/alpha", "Alpha Dev", fTech.id, null, null, null, sortIndex = 1)
    private val feedBeta = FeedEntity("feed/beta", "Beta News", fNews.id, null, null, null, sortIndex = 2)
    private val feedLinuxDo = FeedEntity("feed/linuxdo", "LINUX DO", null, null, null, null, sortIndex = 3)
    private val feedMaple = FeedEntity("feed/maple", "Maple Notes", null, null, null, null, sortIndex = 4)

    private val folders = listOf(fTech, fNews)
    private val feeds = listOf(feedZeta, feedAlpha, feedBeta, feedLinuxDo, feedMaple)

    private fun src(f: FeedEntity) = ArticleSource.Feed(f.id, f.title, f.iconUrl)
    private fun folderSrc(f: FolderEntity) = ArticleSource.Folder(f.id, f.title)

    @Test
    fun feedsFirstFollowsHomeOrderAndNeverJumpsBackToLoose() {
        val counts = mapOf(
            feedLinuxDo.id to 50,
            feedMaple.id to 3,
            feedZeta.id to 4,
            feedAlpha.id to 2,
            feedBeta.id to 1,
        )
        val ordered = FeedOrdering.order(folders, feeds, counts, SortSubs.FEEDS_FIRST, locale = Locale.US)
        assertTrue(ordered.feedsFirst)
        assertEquals(
            listOf(feedLinuxDo.id, feedMaple.id, feedZeta.id, feedAlpha.id, feedBeta.id),
            ordered.orderedFeeds.map { it.id },
        )

        // 从未归类最后一个源继续上拉 → 进入第一个文件夹里的第一个源
        assertEquals(
            feedZeta.id,
            (FeedOrdering.nextSource(src(feedMaple), FilterMode.UNREAD, folders, feeds, counts, counts, SortSubs.FEEDS_FIRST, locale = Locale.US) as ArticleSource.Feed).id,
        )
        // 在文件夹最后一个源继续上拉 → 返回 null（不乱跳回 Linux.do）
        assertNull(
            FeedOrdering.nextSource(src(feedBeta), FilterMode.UNREAD, folders, feeds, counts, counts, SortSubs.FEEDS_FIRST, locale = Locale.US),
        )
    }

    @Test
    fun alphaOrderMatchesHomeAndNextSource() {
        val counts = mapOf(
            feedZeta.id to 1,
            feedAlpha.id to 1,
            feedBeta.id to 1,
            feedLinuxDo.id to 99,
            feedMaple.id to 1,
        )
        // 按名称：文件夹 News < Tech；Tech 内 Alpha < Zeta；未归类 LINUX DO < Maple Notes
        val ordered = FeedOrdering.order(folders, feeds, counts, SortSubs.ALPHA, locale = Locale.US)
        assertEquals(
            listOf(feedBeta.id, feedAlpha.id, feedZeta.id, feedLinuxDo.id, feedMaple.id),
            ordered.orderedFeeds.map { it.id },
        )

        // 原 bug：从 Beta(sortIndex=2) 拉下一个会按 sortIndex 直接跳去 LINUX DO(sortIndex=3)
        // 修复后：严格按首页顺序跳到 Alpha Dev
        val next = FeedOrdering.nextSource(src(feedBeta), FilterMode.UNREAD, folders, feeds, counts, counts, SortSubs.ALPHA, locale = Locale.US)
        assertEquals(feedAlpha.id, (next as ArticleSource.Feed).id)
    }

    @Test
    fun unreadOrderMatchesHomeAndPreservesSequenceWhileReading() {
        val initialCounts = mapOf(
            feedBeta.id to 40, // fNews 总未读 40 > fTech 总未读 25
            feedAlpha.id to 20,
            feedZeta.id to 5,
            feedLinuxDo.id to 100,
            feedMaple.id to 10,
        )
        val ordered = FeedOrdering.order(folders, feeds, initialCounts, SortSubs.UNREAD, locale = Locale.US)
        val seq = SourceSequence(FilterMode.UNREAD, SortSubs.UNREAD, ordered.orderedFeeds.map { it.id })
        assertEquals(
            listOf(feedBeta.id, feedAlpha.id, feedZeta.id, feedLinuxDo.id, feedMaple.id),
            seq.ids,
        )

        // 阅读 Alpha 期间其未读掉到 0：带着首页顺序快照，仍正确跳到 Zeta，而不是因为未读归零跳过或乱跳
        val afterReadingAlpha = initialCounts + (feedAlpha.id to 0)
        val next = FeedOrdering.nextSource(
            current = src(feedAlpha),
            filter = FilterMode.UNREAD,
            folders = folders,
            feeds = feeds,
            hasCounts = afterReadingAlpha,
            sortCounts = afterReadingAlpha,
            sortSubs = SortSubs.UNREAD,
            sequence = seq,
            locale = Locale.US,
        )
        assertEquals(feedZeta.id, (next as ArticleSource.Feed).id)

        // 读到最后一个 Maple Notes：结束返回 null，不回跳到 Linux.do
        assertNull(
            FeedOrdering.nextSource(
                current = src(feedMaple),
                filter = FilterMode.UNREAD,
                folders = folders,
                feeds = feeds,
                hasCounts = afterReadingAlpha,
                sortCounts = afterReadingAlpha,
                sortSubs = SortSubs.UNREAD,
                sequence = seq,
                locale = Locale.US,
            ),
        )
    }

    @Test
    fun switchingFilterInsideListRecomputesNextSourceOrder() {
        val unreadSeq = SourceSequence(
            FilterMode.UNREAD,
            SortSubs.ALPHA,
            listOf(feedBeta.id, feedAlpha.id, feedZeta.id, feedLinuxDo.id, feedMaple.id),
        )
        val starredCounts = mapOf(feedBeta.id to 1, feedZeta.id to 2)
        // 切到 STARRED 后跳过没有星标的 feedAlpha，进入 feedZeta
        val next = FeedOrdering.nextSource(
            current = src(feedBeta),
            filter = FilterMode.STARRED,
            folders = folders,
            feeds = feeds,
            hasCounts = starredCounts,
            sortCounts = starredCounts,
            sortSubs = SortSubs.ALPHA,
            sequence = unreadSeq,
            locale = Locale.US,
        )
        assertEquals(feedZeta.id, (next as ArticleSource.Feed).id)
    }

    @Test
    fun folderNextSourceFollowsSortSubs() {
        val counts = mapOf(feedZeta.id to 3, feedBeta.id to 8)
        // 按名称：News 在 Tech 前面
        val next = FeedOrdering.nextSource(
            current = folderSrc(fNews),
            filter = FilterMode.UNREAD,
            folders = folders,
            feeds = feeds,
            hasCounts = counts,
            sortCounts = counts,
            sortSubs = SortSubs.ALPHA,
            locale = Locale.US,
        )
        assertEquals(fTech.id, (next as ArticleSource.Folder).id)
    }

    @Test
    fun effectiveSortAndGroupSupportGlobalAndPerSourceOverride() {
        val global = Settings(oldestFirstUnread = true, oldestFirstAll = false, groupBy = GroupBy.DATE)
        val sourceA = src(feedAlpha)
        val sourceB = src(feedBeta)
        var acct = AccountSettings()

        assertEquals(ArticleSort.OLDEST_FIRST, acct.effectiveSort(sourceA, FilterMode.UNREAD, global))
        assertEquals(ArticleSort.NEWEST_FIRST, acct.effectiveSort(sourceA, FilterMode.ALL, global))
        assertEquals(ArticleSort.NEWEST_FIRST, acct.effectiveSort(sourceA, FilterMode.STARRED, global))
        assertEquals(GroupBy.DATE, acct.effectiveGroupBy(sourceA, global))
        assertFalse(acct.hasListOverride(sourceA))

        // 只覆盖 sourceA，不影响 sourceB
        acct = acct.withSourceSort(sourceA, ArticleSort.NEWEST_FIRST).withSourceGroupBy(sourceA, GroupBy.NONE)
        assertTrue(acct.hasListOverride(sourceA))
        assertFalse(acct.hasListOverride(sourceB))
        assertEquals(ArticleSort.NEWEST_FIRST, acct.effectiveSort(sourceA, FilterMode.UNREAD, global))
        assertEquals(GroupBy.NONE, acct.effectiveGroupBy(sourceA, global))
        assertEquals(ArticleSort.OLDEST_FIRST, acct.effectiveSort(sourceB, FilterMode.UNREAD, global))
        assertEquals(GroupBy.DATE, acct.effectiveGroupBy(sourceB, global))

        // 恢复全局默认
        acct = acct.clearListOverride(sourceA)
        assertFalse(acct.hasListOverride(sourceA))
        assertEquals(ArticleSort.OLDEST_FIRST, acct.effectiveSort(sourceA, FilterMode.UNREAD, global))
        assertEquals(GroupBy.DATE, acct.effectiveGroupBy(sourceA, global))
    }

    @Test
    fun buildOrderedAppliesSortAndGroup() {
        // 数据库返回顺序：新 → 旧
        val dbItems = listOf(
            item(3L, "f1", "源1", 3_000L),
            item(2L, "f2", "源2", 2_000L),
            item(1L, "f1", "源1", 1_000L),
        )
        val newestNone = ArticleListState.buildOrdered(dbItems, ArticleSort.NEWEST_FIRST, GroupBy.NONE)
        assertEquals(listOf(3L, 2L, 1L), newestNone.items.map { it.id })
        assertTrue(newestNone.rows.all { it is ListRow.Article })

        val oldestNone = ArticleListState.buildOrdered(dbItems, ArticleSort.OLDEST_FIRST, GroupBy.NONE)
        assertEquals(listOf(1L, 2L, 3L), oldestNone.items.map { it.id })

        val oldestByFeed = ArticleListState.buildOrdered(dbItems, ArticleSort.OLDEST_FIRST, GroupBy.FEED)
        assertEquals(listOf(1L, 3L, 2L), oldestByFeed.items.map { it.id })
        assertEquals(listOf("h:源1", "a:1", "a:3", "h:源2", "a:2"), oldestByFeed.rows.map { it.key })
    }

    private fun item(id: Long, feedId: String, feedTitle: String, publishedAt: Long) = ArticleListItem(
        id = id,
        feedId = feedId,
        feedTitle = feedTitle,
        feedIcon = null,
        feedSite = null,
        title = "T$id",
        summary = "",
        thumbUrl = null,
        publishedAt = publishedAt,
        readingMinutes = 1,
        isRead = false,
        isStarred = false,
    )
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@Config(sdk = [35])
class AccountPrefsSortPersistenceTest {
    @Test
    fun roundTripsPerSourceOverridesIncludingQueryParamsInUrl() {
        val sp = RuntimeEnvironment.getApplication().getSharedPreferences("test_acct_sort", Context.MODE_PRIVATE)
        val feedWithParams = ArticleSource.Feed("feed/https://example.com/rss?a=1&b=2", "Example", null)
        val folder = ArticleSource.Folder("user/-/label/科技", "科技")
        val saved = AccountSettings(sortSubs = SortSubs.ALPHA)
            .withSourceSort(feedWithParams, ArticleSort.NEWEST_FIRST)
            .withSourceGroupBy(feedWithParams, GroupBy.NONE)
            .withSourceSort(folder, ArticleSort.OLDEST_FIRST)

        AccountPrefs.write(sp, saved, commit = true)
        val restored = AccountPrefs.read(sp)

        assertEquals(SortSubs.ALPHA, restored.sortSubs)
        assertEquals(ArticleSort.NEWEST_FIRST, restored.sourceSort[feedWithParams.overrideKey])
        assertEquals(GroupBy.NONE, restored.sourceGroupBy[feedWithParams.overrideKey])
        assertEquals(ArticleSort.OLDEST_FIRST, restored.sourceSort[folder.overrideKey])
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w400dp-h800dp")
class SortMenuUiTest {
    @get:org.junit.Rule
    val rule = androidx.compose.ui.test.junit4.createComposeRule()

    private lateinit var graph: AppGraph
    private lateinit var account: AccountSession
    private lateinit var entry: io.github.xccxcxxc.runnel.ui.nav.NavEntry

    @org.junit.Before
    fun setUp() {
        Graph.init(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        graph = Graph.get()
        val saved = graph.prefs.addAccount(io.github.xccxcxxc.runnel.data.AccountType.DEMO, "", "demo")
        account = graph.session(saved.id)!!
        account.prefs.update { it.copy(syncMode = io.github.xccxcxxc.runnel.data.SyncMode.MANUAL) }
        graph.prefs.updateSettings {
            it.copy(
                oldestFirstUnread = true,
                groupBy = GroupBy.NONE,
                imagePreview = io.github.xccxcxxc.runnel.data.ImagePreview.NONE,
                showFavicons = false,
                pullBottom = io.github.xccxcxxc.runnel.data.PullBottom.NONE,
            )
        }
        kotlinx.coroutines.runBlocking {
            account.db.feedDao().upsertFeeds(
                listOf(
                    FeedEntity("feed/a", "Beta Feed", null, null, null, null, 0),
                    FeedEntity("feed/b", "Alpha Feed", null, null, null, null, 1),
                ),
            )
            account.db.articleDao().insertArticles(
                listOf(
                    io.github.xccxcxxc.runnel.data.db.ArticleEntity(1L, "feed/a", "较旧文章", null, null, 1_000L, "", null, 1, false, false, 0),
                    io.github.xccxcxxc.runnel.data.db.ArticleEntity(2L, "feed/a", "最新文章", null, null, 2_000L, "", null, 1, false, false, 0),
                    io.github.xccxcxxc.runnel.data.db.ArticleEntity(3L, "feed/b", "另一源文章", null, null, 3_000L, "", null, 1, false, false, 0),
                ),
            )
        }
    }

    @org.junit.After
    fun tearDown() {
        if (::entry.isInitialized) entry.scope.cancel()
        account.close()
        graph.scope.cancel()
    }

    @Test
    fun articleListSortMenuOverridesImmediatelyAndCanResetToGlobal() {
        val source = ArticleSource.Feed("feed/a", "Beta Feed", null)
        entry = io.github.xccxcxxc.runnel.ui.nav.NavEntry(
            io.github.xccxcxxc.runnel.ui.nav.Route.Articles(account.id, source, FilterMode.UNREAD),
            1,
        )
        rule.setContent {
            io.github.xccxcxxc.runnel.ui.theme.RunnelTheme(graph.prefs.settings.value) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalAccount provides account,
                    io.github.xccxcxxc.runnel.ui.nav.LocalNavEntry provides entry,
                ) {
                    io.github.xccxcxxc.runnel.ui.list.ArticleListScreen(
                        source = source,
                        filter = FilterMode.UNREAD,
                        onBack = {},
                        onOpenArticle = {},
                        onReplace = { _, _, _ -> },
                    )
                }
            }
        }
        rule.waitUntil(10_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasText("较旧文章")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()

        // 初始：全局未读最旧优先，“较旧文章”在“最新文章”上方
        val oldTopBefore = rule.onNode(androidx.compose.ui.test.hasText("较旧文章")).fetchSemanticsNode().boundsInRoot.top
        val newTopBefore = rule.onNode(androidx.compose.ui.test.hasText("最新文章")).fetchSemanticsNode().boundsInRoot.top
        assertTrue(oldTopBefore < newTopBefore)

        // 点右上角排序按钮 → 选“最新优先”
        rule.onNode(androidx.compose.ui.test.hasTestTag("list_sort_button")).performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasText("最新优先")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(androidx.compose.ui.test.hasText("最新优先")).performClick()
        rule.waitUntil(5_000) {
            val newTop = rule.onNode(androidx.compose.ui.test.hasText("最新文章")).fetchSemanticsNode().boundsInRoot.top
            val oldTop = rule.onNode(androidx.compose.ui.test.hasText("较旧文章")).fetchSemanticsNode().boundsInRoot.top
            newTop < oldTop
        }
        assertTrue(account.prefs.settings.value.hasListOverride(source))
        assertTrue(graph.prefs.settings.value.oldestFirstUnread) // 全局未改动

        // 再点排序按钮 → “恢复全局默认” → 立刻变回最旧优先
        rule.onNode(androidx.compose.ui.test.hasTestTag("list_sort_button")).performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasText("恢复全局默认")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(androidx.compose.ui.test.hasText("恢复全局默认")).performClick()
        rule.waitUntil(5_000) {
            val newTop = rule.onNode(androidx.compose.ui.test.hasText("最新文章")).fetchSemanticsNode().boundsInRoot.top
            val oldTop = rule.onNode(androidx.compose.ui.test.hasText("较旧文章")).fetchSemanticsNode().boundsInRoot.top
            oldTop < newTop
        }
        assertFalse(account.prefs.settings.value.hasListOverride(source))
    }

    @Test
    fun homeSortMenuSwitchesSubscriptionOrderImmediately() {
        entry = io.github.xccxcxxc.runnel.ui.nav.NavEntry(
            io.github.xccxcxxc.runnel.ui.nav.Route.Home(account.id),
            2,
        )
        rule.setContent {
            io.github.xccxcxxc.runnel.ui.theme.RunnelTheme(graph.prefs.settings.value) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalAccount provides account,
                    io.github.xccxcxxc.runnel.ui.nav.LocalNavEntry provides entry,
                ) {
                    io.github.xccxcxxc.runnel.ui.home.HomeScreen(
                        onBack = {},
                        onOpen = { _, _, _ -> },
                        onSearch = {},
                        onAdd = {},
                        onEditFeed = {},
                    )
                }
            }
        }
        rule.waitUntil(10_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasText("Beta Feed")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()

        // 初始 FOLDERS_FIRST 按 sortIndex：Beta(0) 在 Alpha(1) 上面
        val betaBefore = rule.onNode(androidx.compose.ui.test.hasText("Beta Feed")).fetchSemanticsNode().boundsInRoot.top
        val alphaBefore = rule.onNode(androidx.compose.ui.test.hasText("Alpha Feed")).fetchSemanticsNode().boundsInRoot.top
        assertTrue(betaBefore < alphaBefore)

        // 点右上角排序按钮 → 按名称
        rule.onNode(androidx.compose.ui.test.hasTestTag("home_sort_button")).performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasText("按名称")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(androidx.compose.ui.test.hasText("按名称")).performClick()
        rule.waitUntil(5_000) {
            val betaAfter = rule.onNode(androidx.compose.ui.test.hasText("Beta Feed")).fetchSemanticsNode().boundsInRoot.top
            val alphaAfter = rule.onNode(androidx.compose.ui.test.hasText("Alpha Feed")).fetchSemanticsNode().boundsInRoot.top
            alphaAfter < betaAfter
        }
        assertEquals(SortSubs.ALPHA, account.prefs.settings.value.sortSubs)
    }
}
