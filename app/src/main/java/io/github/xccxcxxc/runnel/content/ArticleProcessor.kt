package io.github.xccxcxxc.runnel.content

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist
import kotlin.math.max
import kotlin.math.roundToInt

data class ProcessedContent(
    val html: String,
    val summary: String,
    val thumbUrl: String?,
    val readingMinutes: Int,
)

/**
 * 同步时对正文做一次性处理（在后台线程），阅读和滚动时不再解析 HTML：
 * - 懒加载图片的 data-src 等属性还原成 src；有 srcset 时挑一个适合手机屏幕的写进 src（阅读页只用 src，离线图片也只下 src）
 * - 去掉跟踪像素、脚本、内联样式（内联颜色会破坏深色模式）
 * - 相对链接转绝对地址；iframe 只保留常见视频站的播放器和文章所在网站自己的嵌入（[embeddable]）
 * - 生成纯文本摘要、挑缩略图、估算阅读时长
 */
object ArticleProcessor {
    private const val SUMMARY_LENGTH = 200

    private val lazySrcAttrs = listOf("data-src", "data-original", "data-lazy-src", "data-actualsrc", "data-url", "data-hi-res-src")
    private val trackerHints = listOf(
        "feeds.feedburner.com/~r", "feedburner.com/~ff", "pixel.wp.com", "stats.wordpress.com",
        "/feed-stats", "doubleclick.net", "google-analytics.com", "/pixel.gif", "/tracking/", "feedsportal.com",
    )
    /** 主机名后缀，可带路径前缀（weibo.com/tv）。 */
    private val allowedIframeHosts = listOf(
        "youtube.com", "youtube-nocookie.com", "player.vimeo.com", "player.bilibili.com", "w.soundcloud.com",
        // 国内视频站：喷嚏图卦等常嵌微博视频（2026-10-23）
        "video.weibo.com", "weibo.com/tv", "v.qq.com", "player.youku.com", "ixigua.com", "open.iqiyi.com",
        "player.douyin.com", "open.douyin.com",
    )

    private val safelist: Safelist = Safelist.relaxed()
        .addTags(
            "figure", "figcaption", "picture", "source", "video", "audio", "iframe", "hr", "del", "ins",
            "mark", "small", "abbr", "time", "details", "summary", "s", "kbd", "var", "samp",
        )
        .addAttributes("img", "srcset", "sizes", "width", "height", "alt", "title")
        .addAttributes("source", "src", "srcset", "type", "media", "sizes")
        .addAttributes("video", "src", "poster", "controls", "width", "height", "preload", "playsinline")
        .addAttributes("audio", "src", "controls")
        .addAttributes("iframe", "src", "width", "height", "allowfullscreen", "frameborder", "allow")
        .addAttributes("code", "class")
        .addAttributes("pre", "class")
        .addAttributes("td", "colspan", "rowspan")
        .addAttributes("th", "colspan", "rowspan")
        .addProtocols("iframe", "src", "http", "https")
        .addProtocols("video", "src", "http", "https")
        .addProtocols("video", "poster", "http", "https")
        .addProtocols("audio", "src", "http", "https")
        .addProtocols("source", "src", "http", "https")

    fun process(rawHtml: String?, baseUrl: String?, enclosureImage: String? = null): ProcessedContent {
        val dirty = Jsoup.parseBodyFragment(rawHtml.orEmpty(), baseUrl.orEmpty())
        prepare(dirty)
        val clean = Cleaner(safelist).clean(dirty)
        clean.outputSettings().prettyPrint(false)
        val body = clean.body()
        val text = body.text()
        return ProcessedContent(
            html = body.html(),
            summary = summarize(text),
            thumbUrl = pickThumbnail(body) ?: enclosureImage,
            readingMinutes = readingMinutes(text),
        )
    }

    /** 标题里常有 `&amp;` 之类实体或残留标签。 */
    fun cleanTitle(raw: String?): String =
        raw?.let { Parser.unescapeEntities(Jsoup.parse(it).text(), false).trim() }.orEmpty()

    private fun prepare(doc: Document) {
        doc.select("script, style, link, meta, form, input, button, select, textarea, object, embed, svg").remove()
        doc.select("noscript").unwrap()
        for (img in doc.select("img")) {
            val lazy = lazySrcAttrs.firstNotNullOfOrNull { img.attr(it).takeIf(String::isNotBlank) }
            val src = img.attr("src")
            if (lazy != null && (src.isBlank() || src.startsWith("data:"))) img.attr("src", lazy)
            if (img.attr("srcset").isBlank()) img.attr("data-srcset").takeIf(String::isNotBlank)?.let { img.attr("srcset", it) }
            pickSrcset(img.attr("srcset"))?.let { img.attr("src", it) }
            if (isTracker(img)) img.remove()
        }
        doc.select("img:not([src]), img[src=''], img[src^=data:]").remove()
        for (iframe in doc.select("iframe")) {
            // 协议相对地址（//player.bilibili.com/…）没有原文地址时也补成 https
            var src = iframe.absUrl("src").ifBlank { iframe.attr("src").takeIf { it.startsWith("//") }?.let { "https:$it" }.orEmpty() }
            // 视频站都支持 https；喷嚏的文章地址是 http，协议相对的 B 站地址会跟着变成 http
            if (src.startsWith("http://") && runCatching { java.net.URI(src) }.getOrNull()?.let { isVideoSite(it.host.orEmpty().lowercase(), it.rawPath.orEmpty()) } == true) {
                src = "https://" + src.removePrefix("http://")
            }
            if (embeddable(src, doc.location())) {
                iframe.attr("src", src)
                iframe.attr("allowfullscreen", "")
            } else if (src.isNotBlank()) {
                iframe.replaceWith(Element("p").appendElement("a").attr("href", src).text(EMBED_LINK_TEXT).parent()!!)
            } else {
                iframe.remove()
            }
        }
        // 只剩空白的段落、空链接
        doc.select("p:matchesOwn(^\\s*$):not(:has(*))").remove()
    }

    /** 放不进阅读页的 iframe 换成的链接文字；[io.github.xccxcxxc.runnel.ui.reader.ReaderHtml] 显示时会把现在能放的换回播放器。 */
    const val EMBED_LINK_TEXT = "嵌入内容 ↗"

    /**
     * 这个 iframe 能不能留在正文里：常见视频站的播放器，或者和文章同一个主机的嵌入
     * （喷嚏图卦的音频卡片和文章都在 www.dapenti.com 上）。只认 http(s)。
     */
    fun embeddable(src: String, articleUrl: String?): Boolean {
        val uri = runCatching { java.net.URI(src) }.getOrNull() ?: return false
        if (uri.scheme != "http" && uri.scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        if (isVideoSite(host, uri.rawPath.orEmpty())) return true
        val articleHost = articleUrl?.let { runCatching { java.net.URI(it).host }.getOrNull() }?.lowercase() ?: return false
        return host.removePrefix("www.") == articleHost.removePrefix("www.")
    }

    private fun isVideoSite(host: String, path: String) = allowedIframeHosts.any { entry ->
        val h = entry.substringBefore('/')
        (host == h || host.endsWith(".$h")) && path.startsWith(entry.removePrefix(h))
    }

    /**
     * 从 srcset 里挑一张：宽度描述（640w）取不小于 [SRCSET_MIN_WIDTH] 的最小一张，都不够就取最大的；
     * 密度描述（2x）取不小于 2x 的最小一张，都不够就取最大的。手机屏宽约 1080–1440 像素，再大只是浪费流量。
     */
    fun pickSrcset(srcset: String?): String? {
        if (srcset.isNullOrBlank()) return null
        val candidates = srcsetCandidates(srcset).mapNotNull { (url, descriptor) ->
            val d = descriptor.substringBefore(' ')
            when {
                d.endsWith("w") -> d.dropLast(1).toFloatOrNull()?.let { Triple(url, it, 'w') }
                d.endsWith("x") -> d.dropLast(1).toFloatOrNull()?.let { Triple(url, it, 'x') }
                d.isEmpty() -> Triple(url, 1f, 'x')
                else -> null
            }
        }
        val kind = candidates.firstOrNull()?.third ?: return null
        val same = candidates.filter { it.third == kind }.sortedBy { it.second }
        val enough = if (kind == 'w') SRCSET_MIN_WIDTH else 2f
        return (same.firstOrNull { it.second >= enough } ?: same.last()).first
    }

    /** 按 HTML 规范拆 srcset：地址到空白为止（地址里可以有逗号，比如 Cloudinary 的 w_640,c_fill），描述到逗号为止。 */
    private fun srcsetCandidates(s: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        var i = 0
        while (i < s.length) {
            while (i < s.length && (s[i].isWhitespace() || s[i] == ',')) i++
            val start = i
            while (i < s.length && !s[i].isWhitespace()) i++
            var url = s.substring(start, i)
            var descriptor = ""
            if (url.endsWith(',')) {
                url = url.trimEnd(',')
            } else {
                val d = i
                while (i < s.length && s[i] != ',') i++
                descriptor = s.substring(d, i).trim()
            }
            if (url.isNotEmpty()) out += url to descriptor
        }
        return out
    }

    private const val SRCSET_MIN_WIDTH = 1200f

    private fun isTracker(img: Element): Boolean {
        val w = img.attr("width").toIntOrNull()
        val h = img.attr("height").toIntOrNull()
        if ((w != null && w <= 2) || (h != null && h <= 2)) return true
        val src = img.attr("src")
        return trackerHints.any { src.contains(it, ignoreCase = true) }
    }

    private fun pickThumbnail(body: Element): String? = body.select("img[src]").firstOrNull { img ->
        val w = img.attr("width").toIntOrNull()
        val src = img.attr("src")
        (w == null || w >= 120) && !src.endsWith(".svg", true) && !src.contains("emoji", true) &&
            !src.contains("avatar", true) && !src.contains("gravatar", true)
    }?.attr("src")

    private fun summarize(text: String): String {
        val compact = text.replace(Regex("\\s+"), " ").trim()
        return if (compact.length <= SUMMARY_LENGTH) compact else compact.take(SUMMARY_LENGTH).trimEnd() + "…"
    }

    /** 中文按 400 字/分钟，英文按 220 词/分钟。 */
    private fun readingMinutes(text: String): Int {
        var cjk = 0
        for (ch in text) if (Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.HAN) cjk++
        val words = Regex("[A-Za-z0-9']+").findAll(text).count()
        return max(1, (cjk / 400.0 + words / 220.0).roundToInt())
    }
}
