package io.github.xccxcxxc.runnel

import io.github.xccxcxxc.runnel.data.db.ArticleListItem
import io.github.xccxcxxc.runnel.ui.list.ArticleListState
import org.junit.Assert.assertEquals
import org.junit.Test

class ListFilterTest {
    private fun item(id: Long, title: String, summary: String = "", feed: String = "订阅") = ArticleListItem(
        id = id, feedId = "f", feedTitle = feed, feedIcon = null, feedSite = null, title = title,
        summary = summary, thumbUrl = null, publishedAt = 0, readingMinutes = 1, isRead = false, isStarred = false,
    )

    private val list = listOf(
        item(1, "GPT 模型 + Rust Agent 组合拳", "探讨 Codex 订阅", "LINUX DO - 最新话题"),
        item(2, "之前 antigravity 被封了", "大家都是用新号用 gemini 吗", "LINUX DO - 最新话题"),
        item(3, "Kotlin 2.4 released", "Compose news", "Blog"),
    )

    private fun ids(q: String) = ArticleListState.filterItems(list, q).map { it.id }

    @Test
    fun emptyQueryKeepsEverything() {
        assertEquals(listOf(1L, 2L, 3L), ids(""))
        assertEquals(listOf(1L, 2L, 3L), ids("   "))
    }

    @Test
    fun matchesTitleSummaryAndFeedCaseInsensitive() {
        assertEquals(listOf(1L), ids("rust"))
        assertEquals(listOf(2L), ids("GEMINI")) // 摘要
        assertEquals(listOf(3L), ids("blog")) // 订阅源名
        assertEquals(listOf(1L, 2L), ids("linux do"))
        assertEquals(listOf(1L), ids("模型")) // 中文没有分词，按子串匹配
    }

    @Test
    fun everyWordMustMatch() {
        assertEquals(listOf(1L), ids("rust codex"))
        assertEquals(emptyList<Long>(), ids("rust gemini"))
        assertEquals(listOf(1L), ids("rust　codex")) // 全角空格也算分隔
    }
}
