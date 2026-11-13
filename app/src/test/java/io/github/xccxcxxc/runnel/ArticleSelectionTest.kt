package io.github.xccxcxxc.runnel

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ApplicationProvider
import io.github.xccxcxxc.runnel.data.AccountType
import io.github.xccxcxxc.runnel.data.ArticleSource
import io.github.xccxcxxc.runnel.data.FilterMode
import io.github.xccxcxxc.runnel.data.ImagePreview
import io.github.xccxcxxc.runnel.data.PullBottom
import io.github.xccxcxxc.runnel.data.SyncMode
import io.github.xccxcxxc.runnel.data.db.ArticleEntity
import io.github.xccxcxxc.runnel.data.db.FeedEntity
import io.github.xccxcxxc.runnel.ui.list.ArticleListScreen
import io.github.xccxcxxc.runnel.ui.list.ArticleListState
import io.github.xccxcxxc.runnel.ui.nav.LocalNavEntry
import io.github.xccxcxxc.runnel.ui.nav.NavEntry
import io.github.xccxcxxc.runnel.ui.nav.ReaderSession
import io.github.xccxcxxc.runnel.ui.nav.Route
import io.github.xccxcxxc.runnel.ui.reader.ReaderScreen
import io.github.xccxcxxc.runnel.ui.reader.ReaderState
import io.github.xccxcxxc.runnel.ui.theme.RunnelTheme
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/** 首次载入后快速滚动，让行经历预组合/复用，再以屏幕上显示的标题核对打开的文章 ID。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h800dp")
class ArticleSelectionTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var graph: AppGraph
    private lateinit var account: AccountSession
    private lateinit var entry: NavEntry
    private var opened: ReaderSession? = null

    @Before
    fun setUp() {
        Graph.init(ApplicationProvider.getApplicationContext())
        graph = Graph.get()
        val saved = graph.prefs.addAccount(AccountType.DEMO, "", "demo")
        account = graph.session(saved.id)!!
        account.prefs.update { it.copy(syncMode = SyncMode.MANUAL) }
        graph.prefs.updateSettings {
            it.copy(imagePreview = ImagePreview.NONE, showFavicons = false, pullBottom = PullBottom.NONE)
        }
        entry = NavEntry(Route.Articles(account.id, ArticleSource.All, FilterMode.ALL), 1)
        runBlocking {
            account.db.feedDao().upsertFeeds(listOf(FeedEntity("f", "测试源", null, null, null, null, 0)))
            account.db.articleDao().insertArticles((1L..300L).map { id ->
                ArticleEntity(id, "f", "文章 $id", null, null, id * 86_400_000L, "摘要".repeat((id % 5).toInt()), null, 1, false, false, 0)
            })
        }
    }

    @After
    fun tearDown() {
        entry.scope.cancel()
        account.close()
        graph.scope.cancel()
    }

    private fun showList() {
        rule.setContent {
            RunnelTheme(graph.prefs.settings.value) {
                CompositionLocalProvider(LocalAccount provides account, LocalNavEntry provides entry) {
                    ArticleListScreen(ArticleSource.All, FilterMode.ALL, {}, { opened = it }, { _, _ -> })
                }
            }
        }
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("300 篇文章")).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }

    @Test
    fun firstLoadFastScrollOpensDisplayedArticle() {
        showList()
        repeat(8) {
            rule.onNodeWithTag("article_list").performTouchInput { swipeUp(durationMillis = 80) }
            rule.waitForIdle()
        }
        val node = rule.onAllNodes(hasText("文章 ", substring = true)).fetchSemanticsNodes()
            .first { it.boundsInRoot.top > 100 && it.boundsInRoot.bottom < 650 }
        val title = node.config[SemanticsProperties.Text].first { it.text.startsWith("文章 ") }.text
        rule.onNodeWithText(title).performTouchInput { click() }
        rule.runOnIdle {
            val session = checkNotNull(opened)
            assertEquals(title.removePrefix("文章 ").toLong(), session.ids[session.currentIndex])
        }
    }

    @Test
    fun pendingClickKeepsArticleWhenSnapshotChanges() {
        showList()
        val target = 297L
        clickAsSnapshotChanges(target)
        rule.runOnIdle {
            val session = checkNotNull(opened)
            assertEquals(target, session.ids[session.currentIndex])
        }
    }

    @Test
    fun removedArticleDoesNotOpenFirstItem() {
        showList()
        clickAsSnapshotChanges(300L)
        rule.runOnIdle { assertNull(opened) }
    }

    /** 数据快照已更新、画面尚未来得及重组时触发原行的点击，重放异步载入与触摸到达同一帧的竞态。 */
    private fun clickAsSnapshotChanges(articleId: Long) {
        val click = rule.onNodeWithText("文章 $articleId").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action!!
        val state = entry.retain<ArticleListState>("list") { error("列表状态尚未创建") }
        val observer = SnapshotStateObserver { it() }
        var clicked = false
        rule.runOnIdle {
            observer.start()
            observer.observeReads(Any(), { _: Any -> clicked = true; click() }) { state.built }
            state.updateQuery("文章 29")
        }
        try {
            rule.waitUntil(15_000) {
                rule.onNodeWithTag("article_list").fetchSemanticsNode()
                state.built?.items?.size == 13
            }
            rule.runOnIdle { assertTrue("必须在新快照到达时触发原行的点击", clicked) }
        } finally {
            rule.runOnIdle {
                observer.stop()
                observer.clear()
            }
        }
    }

    @Test
    fun readerStartsAtSelectedArticleDeepInList() {
        val session = ReaderSession((300L downTo 1L).toList(), 173)
        entry.scope.cancel()
        entry = NavEntry(Route.Reader(account.id, session), 2)
        rule.setContent {
            RunnelTheme(graph.prefs.settings.value) {
                CompositionLocalProvider(LocalAccount provides account, LocalNavEntry provides entry) {
                    ReaderScreen(session, {})
                }
            }
        }
        val state = entry.retain<ReaderState>("reader") { error("阅读状态尚未创建") }
        rule.waitUntil(15_000) { state.currentId == 127L }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(173, session.currentIndex)
            assertEquals(127L, session.ids[session.currentIndex])
            assertEquals("首屏绘出前不创建相邻 WebView", setOf(127L), state.webViews.keys)
        }
    }
}
