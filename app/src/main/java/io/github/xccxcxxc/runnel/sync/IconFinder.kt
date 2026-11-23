package io.github.xccxcxxc.runnel.sync

import io.github.xccxcxxc.runnel.data.db.RunnelDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URI

/**
 * 给没有图标的订阅源找网站自己的图标：
 * 读网站首页的 <link rel="apple-touch-icon"> / <link rel="icon">，优先高清的苹果触屏图标，
 * 都没有就用 /favicon.ico。结果存进 feeds.localIcon，只找一次。
 */
class IconFinder(private val http: OkHttpClient) {
    suspend fun fillMissing(db: RunnelDatabase) = withContext(Dispatchers.IO) {
        val dao = db.feedDao()
        val todo = dao.allFeeds().filter { it.iconUrl.isNullOrBlank() && it.localIcon == null }
        if (todo.isEmpty()) return@withContext
        val gate = Semaphore(4)
        coroutineScope {
            todo.map { feed ->
                async {
                    gate.withPermit {
                        val site = feed.siteUrl ?: feed.feedUrl ?: return@withPermit
                        val icon = runCatching { find(site) }.getOrNull()
                        dao.setLocalIcon(feed.id, icon ?: "")
                    }
                }
            }.awaitAll()
        }
    }

    fun find(site: String): String? {
        val origin = URI(site).let { "${it.scheme}://${it.host}${if (it.port > 0) ":${it.port}" else ""}" }
        val html = http.newCall(
            Request.Builder().url(origin).header("Accept", "text/html").build(),
        ).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body.source().let { src ->
                src.request(512 * 1024)
                src.buffer.clone().readUtf8()
            }
        }
        if (html != null) {
            val doc = Jsoup.parse(html, origin)
            val links = doc.select("link[rel][href]").mapNotNull { el ->
                val rel = el.attr("rel").lowercase()
                val href = el.absUrl("href")
                if (href.isBlank() || href.endsWith(".svg", true) || href.startsWith("data:")) return@mapNotNull null
                val size = el.attr("sizes").substringBefore('x').toIntOrNull() ?: 0
                when {
                    rel.contains("apple-touch-icon") -> href to 1000 + maxOf(size, 180)
                    rel.split(' ').contains("icon") -> href to size.coerceAtLeast(16)
                    else -> null
                }
            }
            links.maxByOrNull { it.second }?.let { return it.first }
        }
        return "$origin/favicon.ico"
    }
}
