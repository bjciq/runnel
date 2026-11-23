package io.github.xccxcxxc.runnel

import io.github.xccxcxxc.runnel.content.ArticleProcessor
import io.github.xccxcxxc.runnel.content.PageVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 虎嗅视频文章：RSS 里只有一句摘要，视频地址在原网页的 Nuxt 数据里（2026-10-23 的网页原样截一段）。 */
class PageVideoTest {
    private val url = "https://www.huxiu.com/article/4899858.html?f=rss"

    /** 手机版网页（抓全文用手机 UA，会跳到 m.huxiu.com）：地址里的斜杠都转义成 Unicode 转义序列。 */
    private val mobilePage = (
        """window.__NUXT__=(function(a,b){return {data:[{url:"https://m.huxiu.com/article/4899858.html?type=video",is_open_reward:b,""" +
            """video_info:{video_id:103513,cover_path:"https://v2-img.huxiucdn.com/mda-sjnj2jmkue44bced/mda-sjnj2jmkue44bced.jpg",""" +
            """sd_link:"https://s2-video.huxiucdn.com/mda-sjnj2jmkue44bced/mp4360p/mda-sjnj2jmkue44bced.mp4",""" +
            """hd_link:"https://s2-video.huxiucdn.com/mda-sjnj2jmkue44bced/mp4720p/mda-sjnj2jmkue44bced.mp4",""" +
            """fhd_link:"https://s2-video.huxiucdn.com/mda-sjnj2jmkue44bced/mda-sjnj2jmkue44bced.mp4",width:1920,height:1080}}]}}(1,false))"""
        ).replace("/", "\\u002F").let { "<script>$it</script>" }

    /** 电脑版：键和值分开存，值在后面的数组里。 */
    private val desktopPage = """<script>[{"video_id":21,"cover_path":22,"sd_link":23,"hd_link":24,"fhd_link":25,"width":26,"height":27,"duration":28},103513,""" +
        """"https://v2-img.huxiucdn.com/mda-sjnj2jmkue44bced/mda-sjnj2jmkue44bced.jpg","https://s2-video.huxiucdn.com/mda-sjnj2jmkue44bced/mp4360p/mda-sjnj2jmkue44bced.mp4",""" +
        """"https://s2-video.huxiucdn.com/mda-sjnj2jmkue44bced/mp4720p/mda-sjnj2jmkue44bced.mp4","https://s2-video.huxiucdn.com/mda-sjnj2jmkue44bced/mda-sjnj2jmkue44bced.mp4",1920,1080]</script>"""

    @Test
    fun findsHuxiuVideoIn720p() {
        assertTrue(mobilePage, mobilePage.contains("hd_link:\"https:\\u002F\\u002Fs2-video"))
        val expected = """<p><video controls preload="metadata" playsinline src="https://s2-video.huxiucdn.com/mda-sjnj2jmkue44bced/mp4720p/mda-sjnj2jmkue44bced.mp4"""" +
            """ poster="https://v2-img.huxiucdn.com/mda-sjnj2jmkue44bced/mda-sjnj2jmkue44bced.jpg"></video></p>"""
        assertEquals(expected, PageVideo.find(url, mobilePage))
        assertEquals(expected, PageVideo.find("https://m.huxiu.com/article/4899858.html", desktopPage))
        assertNull(PageVideo.find("https://ex.com/a", mobilePage))
        assertNull(PageVideo.find(url, "<p>没有视频的文章</p>"))
    }

    /** 视频文章的全文 = 视频 + 网页简介，经过正文清洗后视频还在（全文就是这样存的）；不是视频文章返回 null，照常走 Readability。 */
    @Test
    fun videoPageBecomesVideoPlusDescription() {
        val page = """<html><head><meta property="og:description" content="改革不是抛弃第三空间，而是拯救它 &amp; 其它"></head>""" +
            """<body><img src="https://static.huxiucdn.com/common/image/logo/logo_200.png" alt="虎嗅logo">$mobilePage</body></html>"""
        val html = ArticleProcessor.process(PageVideo.page(url, page), url).html
        assertTrue(html, html.startsWith("""<p><video controls="" preload="metadata" playsinline="" src="https://s2-video.huxiucdn.com/"""))
        assertTrue(html, html.contains("""poster="https://v2-img.huxiucdn.com/"""))
        assertTrue(html, html.endsWith("""</video></p><p>改革不是抛弃第三空间，而是拯救它 &amp; 其它</p>"""))
        assertFalse(html, html.contains("logo"))
        assertNull(PageVideo.page(url, "<html><body><p>普通文章</p></body></html>"))
    }

    /** 只有虎嗅、正文只有一句话的文章才自动抓原网页；普通虎嗅文章 RSS 里就是全文，不抓。 */
    @Test
    fun fetchesOnlySummaryOnlyHuxiuArticles() {
        assertTrue(PageVideo.shouldFetch(url, """<p data-check-id="346678">改革不是抛弃第三空间，而是拯救它</p>"""))
        assertFalse(PageVideo.shouldFetch(url, """<p>正文</p><p><img src="https://img.huxiucdn.com/a.jpg"></p>"""))
        assertFalse(PageVideo.shouldFetch(url, "<p>" + "很长的正文。".repeat(100) + "</p>"))
        assertFalse(PageVideo.shouldFetch("https://ex.com/a", "<p>一句话</p>"))
        assertFalse(PageVideo.shouldFetch(null, "<p>一句话</p>"))
    }
}
