package io.github.xccxcxxc.runnel.content

import org.jsoup.Jsoup
import org.jsoup.nodes.Entities

/**
 * 订阅源里没有播放器、视频只在原网页里的网站。目前只有虎嗅：视频文章（RSS 的 type 是 video_article）的正文
 * 只有一句摘要，mp4 地址在网页的 Nuxt 数据里（`video_info:{cover_path:…,sd_link:…,hd_link:…}`，
 * 手机版网页里地址的斜杠转义成了 Unicode 转义序列 u002F）。抓全文时换成 `<video>` + 简介。
 */
object PageVideo {
    private val MP4 = Regex("""https?:(?:\\u002F|/){2}[^"\s]+?\.mp4""")
    private val MEDIA = Regex("""<(?:img|video|iframe)[\s>]""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("<[^>]*>")
    private val COVER = Regex("""https?:(?:\\u002F|/){2}[^"\s]+?\.(?:jpe?g|png|webp)""")

    /** 虎嗅的文章（www / m 都算）。 */
    fun isHuxiu(url: String?): Boolean {
        val host = url?.let { runCatching { java.net.URI(it).host }.getOrNull() }?.lowercase() ?: return false
        return host == "huxiu.com" || host.endsWith(".huxiu.com")
    }

    /**
     * 打开文章时要不要自动抓原网页找视频：虎嗅的文章正文很短、没有图片和播放器（视频文章只给一句摘要；
     * 普通文章 RSS 里就是全文，不抓）。在主线程调用，只做字符串判断，不解析 HTML。
     */
    fun shouldFetch(url: String?, html: String?): Boolean {
        if (!isHuxiu(url)) return false
        val body = html.orEmpty()
        if (body.length > 3000) return false
        if (MEDIA.containsMatchIn(body)) return false
        return body.replace(TAG, "").trim().length < 300
    }

    /** 网页里文章自己的视频，拼成 `<video>`；没有就返回 null。 */
    fun find(url: String, pageHtml: String): String? {
        if (!isHuxiu(url)) return null
        // 只看 video_info 后面一段，不要拿到推荐列表里别的视频；电脑版是 "video_id":…，值在后面的数组里
        val start = listOf("video_info", "\"video_id\"").map { pageHtml.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: return null
        val window = pageHtml.substring(start, minOf(pageHtml.length, start + 4000))
        val videos = MP4.findAll(window).map { unescape(it.value) }.toList()
        // 720p 够手机看，原画太大；没有 720p 就用第一个
        val src = videos.firstOrNull { "/mp4720p/" in it } ?: videos.firstOrNull() ?: return null
        val poster = COVER.find(window)?.let { unescape(it.value) }
        return "<p><video controls preload=\"metadata\" playsinline src=\"${attr(src)}\"" +
            (poster?.let { " poster=\"${attr(it)}\"" } ?: "") + "></video></p>"
    }

    /**
     * 视频文章的全文：视频 + 网页的简介。不用 Readability：虎嗅视频页它会把网站 logo、重复的标题、
     * “下载虎嗅APP”都当成正文。不是视频文章返回 null，照常走 Readability。
     */
    fun page(url: String, pageHtml: String): String? {
        val video = find(url, pageHtml) ?: return null
        val description = Jsoup.parse(pageHtml).select("meta[property=og:description], meta[name=description]")
            .firstNotNullOfOrNull { it.attr("content").trim().takeIf(String::isNotEmpty) }
        return video + (description?.let { "<p>${Entities.escape(it)}</p>" } ?: "")
    }

    private fun unescape(s: String) = s.replace("\\u002F", "/")

    private fun attr(s: String) = s.replace("&", "&amp;").replace("\"", "&quot;")
}
