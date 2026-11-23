package io.github.xccxcxxc.runnel.sync.greader

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val GReaderJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

@Serializable
data class SubscriptionList(val subscriptions: List<Subscription> = emptyList())

@Serializable
data class Subscription(
    val id: String,
    val title: String = "",
    val categories: List<Category> = emptyList(),
    val url: String? = null,
    val htmlUrl: String? = null,
    val iconUrl: String? = null,
)

@Serializable
data class Category(val id: String, val label: String? = null)

@Serializable
data class ItemRefs(val itemRefs: List<ItemRef> = emptyList(), val continuation: String? = null)

@Serializable
data class ItemRef(val id: String)

@Serializable
data class StreamContents(val items: List<Item> = emptyList(), val continuation: String? = null)

@Serializable
data class Item(
    val id: String,
    val title: String? = null,
    val published: Long = 0,
    val updated: Long = 0,
    val crawlTimeMsec: String? = null,
    val author: String? = null,
    val canonical: List<Link> = emptyList(),
    val alternate: List<Link> = emptyList(),
    val summary: Content? = null,
    val content: Content? = null,
    val origin: Origin? = null,
    val categories: List<String> = emptyList(),
    val enclosure: List<Enclosure> = emptyList(),
)

@Serializable
data class Link(val href: String? = null, val type: String? = null)

@Serializable
data class Content(val content: String? = null)

@Serializable
data class Origin(val streamId: String? = null, val title: String? = null, val htmlUrl: String? = null)

@Serializable
data class Enclosure(val href: String? = null, val type: String? = null)

object Streams {
    const val READING_LIST = "user/-/state/com.google/reading-list"
    const val READ = "user/-/state/com.google/read"
    const val STARRED = "user/-/state/com.google/starred"
    const val LABEL_PREFIX = "user/-/label/"
}

/**
 * GReader 文章 id 有两种写法：长格式 `tag:google.com,2005:reader/item/<16 位十六进制>`，
 * 短格式是十进制。统一转成 Long（按无符号 64 位解析十六进制）。
 */
object ItemIds {
    private const val LONG_PREFIX = "tag:google.com,2005:reader/item/"

    fun parse(raw: String): Long =
        if (raw.startsWith(LONG_PREFIX)) java.lang.Long.parseUnsignedLong(raw.substring(LONG_PREFIX.length), 16)
        else raw.toLong()

    fun toLongForm(id: Long): String = LONG_PREFIX + java.lang.Long.toHexString(id).padStart(16, '0')
}
