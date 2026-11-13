package io.github.xccxcxxc.runnel

import io.github.xccxcxxc.runnel.data.db.ReaderArticle
import io.github.xccxcxxc.runnel.ui.reader.ReaderArticleCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderArticleCacheTest {
    private fun article(id: Long) = ReaderArticle(id, "f", "来源", "文章 $id", null, null, 0, 1, false, false, "正文", null)

    @Test
    fun concurrentReadersShareOneDatabaseRequest() = runTest {
        var queries = 0
        val gate = CompletableDeferred<Unit>()
        val cache = ReaderArticleCache(this) { id -> queries++; gate.await(); article(id) }
        val page = async { cache.load(1) }
        val toolbar = async { cache.load(1) }
        val fullText = async { cache.load(1) }
        runCurrent()
        assertEquals(1, queries)
        gate.complete(Unit)
        assertSame(page.await(), toolbar.await())
        assertSame(page.await(), fullText.await())
        assertSame(page.await(), cache.load(1))
        assertEquals(1, queries)
    }

    @Test
    fun leavingOneConsumerDoesNotCancelTheSharedLoad() = runTest {
        var queries = 0
        val gate = CompletableDeferred<Unit>()
        val cache = ReaderArticleCache(this) { id -> queries++; gate.await(); article(id) }
        val first = async { cache.load(1) }
        val second = async { cache.load(1) }
        runCurrent()
        first.cancelAndJoin()
        gate.complete(Unit)
        assertEquals(1L, second.await()?.id)
        assertEquals(1, queries)
    }

    @Test
    fun completedLoadIsCachedEvenIfItsConsumerLeft() = runTest {
        var queries = 0
        val gate = CompletableDeferred<Unit>()
        val cache = ReaderArticleCache(this) { id -> queries++; gate.await(); article(id) }
        val first = async { cache.load(1) }
        runCurrent()
        first.cancelAndJoin()
        gate.complete(Unit)
        runCurrent()
        assertEquals(1L, cache.load(1)?.id)
        assertEquals(1, queries)
    }

    @Test
    fun cacheEvictsLeastRecentlyUsedArticle() = runTest {
        val queried = mutableListOf<Long>()
        val cache = ReaderArticleCache(this, capacity = 2) { id -> queried += id; article(id) }
        cache.load(1)
        cache.load(2)
        cache.load(1)
        cache.load(3)
        cache.load(2)
        assertEquals(listOf(1L, 2L, 3L, 2L), queried)
    }

    @Test
    fun missingArticleCanBeLoadedLater() = runTest {
        var queries = 0
        val cache = ReaderArticleCache(this) { id -> if (++queries == 1) null else article(id) }
        assertNull(cache.load(1))
        assertEquals(1L, cache.load(1)?.id)
        assertEquals(2, queries)
    }
}
