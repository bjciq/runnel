package io.github.xccxcxxc.runnel

import io.github.xccxcxxc.runnel.content.ArticleProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleProcessorTest {
    @Test
    fun cleansAndResolves() {
        val html = """
            <div style="color:#000"><script>alert(1)</script>
            <p>Hello <a href="/post/2">link</a></p>
            <img data-src="/img/a.jpg" src="data:image/gif;base64,R0lGOD" width="800" height="600">
            <img src="https://feeds.feedburner.com/~r/x/~4/abc" width="1" height="1">
            <iframe src="https://www.youtube.com/embed/xyz"></iframe>
            <iframe src="https://ads.example.com/frame"></iframe>
            <p>   </p></div>
        """.trimIndent()
        val p = ArticleProcessor.process(html, "https://example.com/blog/1")
        assertFalse(p.html.contains("<script"))
        assertFalse(p.html.contains("style="))
        assertTrue(p.html, p.html.contains("href=\"https://example.com/post/2\""))
        assertTrue(p.html, p.html.contains("src=\"https://example.com/img/a.jpg\""))
        assertFalse(p.html.contains("feedburner"))
        assertTrue(p.html.contains("youtube.com/embed/xyz"))
        assertFalse(p.html.contains("<iframe src=\"https://ads.example.com"))
        assertTrue(p.html.contains("https://ads.example.com/frame"))
        assertEquals("https://example.com/img/a.jpg", p.thumbUrl)
        assertEquals("Hello link 嵌入内容 ↗", p.summary)
    }

    @Test
    fun summaryAndReadingTime() {
        val zh = "<p>" + "中文内容".repeat(400) + "</p>"
        val p = ArticleProcessor.process(zh, null)
        assertEquals(201, p.summary.length)
        assertTrue(p.summary.endsWith("…"))
        assertEquals(4, p.readingMinutes)
        assertEquals(null, p.thumbUrl)
    }

    @Test
    fun titleEntities() {
        assertEquals("A & B <C>", ArticleProcessor.cleanTitle("A &amp; B &lt;C&gt;"))
        assertEquals("Bold title", ArticleProcessor.cleanTitle("<b>Bold</b> title"))
    }

    /** srcset 挑一张适合手机屏幕的写进 src（阅读页只用 src，离线图片也只下 src）。 */
    @Test
    fun picksSrcsetCandidateIntoSrc() {
        assertEquals("b.jpg", ArticleProcessor.pickSrcset("a.jpg 640w, b.jpg 1280w, c.jpg 2560w"))
        assertEquals("c.jpg", ArticleProcessor.pickSrcset("a.jpg 300w, c.jpg 1000w"))
        assertEquals("b.jpg", ArticleProcessor.pickSrcset("a.jpg 1x, b.jpg 2x, c.jpg 3x"))
        assertEquals("a.jpg", ArticleProcessor.pickSrcset("a.jpg"))
        // 地址里带逗号（Cloudinary）不能被拆开
        assertEquals(
            "https://res.cloudinary.com/x/image/upload/w_1400,c_fill/a.jpg",
            ArticleProcessor.pickSrcset("https://res.cloudinary.com/x/image/upload/w_700,c_fill/a.jpg 700w, https://res.cloudinary.com/x/image/upload/w_1400,c_fill/a.jpg 1400w"),
        )
        assertEquals("b.jpg", ArticleProcessor.pickSrcset("a.jpg, b.jpg 2x"))
        assertEquals(null, ArticleProcessor.pickSrcset("  "))
        val p = ArticleProcessor.process(
            """<p><img src="/s.jpg" srcset="/m.jpg 800w, /l.jpg 1600w" alt="x"><img data-src="/lazy.jpg" data-srcset="/lz.jpg 1300w"></p>""",
            "https://ex.com/post/1",
        )
        assertTrue(p.html, p.html.contains("src=\"https://ex.com/l.jpg\""))
        assertTrue(p.html, p.html.contains("src=\"https://ex.com/lz.jpg\""))
    }

    /**
     * 喷嚏图卦的视频（2026-10-23 的 RSS 原样）：微博视频、协议相对地址的 B 站播放器、喷嚏自己网站上的音频卡片
     * 都要留成 iframe，不能换成“嵌入内容 ↗”链接（以前只放行 YouTube 等，微博视频点不开）。
     */
    @Test
    fun keepsVideoEmbedsFromDapenti() {
        val html = """
            <p><iframe src="https://h5.video.weibo.com/show/1034:5356719388426257" scrolling="no" border="0" frameborder="no" framespacing="0" width="640" height="420"></iframe></p>
            <p><iframe src="//player.bilibili.com/player.html?isOutside=true&aid=117487639268448&bvid=BV1Bsns6REaf&cid=42970908677&p=1" scrolling="no" border="0" frameborder="no" framespacing="0" width="640" height="420"></iframe></p>
            <p><iframe src="https://www.dapenti.com/blog/audio-card-v2.html?v=20260901-fixed2&title=x&src=https%3A%2F%2Freader.dapenti.com%2Fa.mp3" scrolling="no" width="640" height="220" loading="lazy"></iframe></p>
            <p><iframe src="https://ads.example.com/frame"></iframe></p>
        """.trimIndent()
        val p = ArticleProcessor.process(html, "https://www.dapenti.com/blog/more.asp?name=xilei&id=200000")
        assertTrue(p.html, p.html.contains("""<iframe src="https://h5.video.weibo.com/show/1034:5356719388426257" frameborder="no" width="640" height="420" allowfullscreen>"""))
        assertTrue(p.html, p.html.contains("""<iframe src="https://player.bilibili.com/player.html?isOutside=true&amp;aid=117487639268448"""))
        assertTrue(p.html, p.html.contains("""<iframe src="https://www.dapenti.com/blog/audio-card-v2.html?"""))
        assertTrue(p.html, p.html.contains("""height="220""""))
        // 别的网站的 iframe 照旧换成链接
        assertTrue(p.html, p.html.contains("""<a href="https://ads.example.com/frame">嵌入内容 ↗</a>"""))
        assertEquals(1, Regex("嵌入内容").findAll(p.html).count())
        // 没有原文地址时，协议相对的播放器地址也补成 https
        val noBase = ArticleProcessor.process("""<iframe src="//player.bilibili.com/player.html?bvid=BV1"></iframe>""", null)
        assertTrue(noBase.html, noBase.html.contains("""<iframe src="https://player.bilibili.com/player.html?bvid=BV1""""))
        // 喷嚏的文章地址是 http：协议相对的播放器地址跟着成了 http，改成 https
        val httpBase = ArticleProcessor.process("""<iframe src="//player.bilibili.com/player.html?bvid=BV1"></iframe>""", "http://www.dapenti.com/blog/more.asp?id=1")
        assertTrue(httpBase.html, httpBase.html.contains("""<iframe src="https://player.bilibili.com/player.html?bvid=BV1""""))
    }

    /** 放行看的是主机名，不是地址里随便哪里出现了视频站的名字。 */
    @Test
    fun embedAllowlistMatchesHostOnly() {
        assertTrue(ArticleProcessor.embeddable("https://www.youtube.com/embed/x", null))
        assertTrue(ArticleProcessor.embeddable("https://h5.video.weibo.com/show/1", null))
        assertTrue(ArticleProcessor.embeddable("https://weibo.com/tv/show/1", null))
        assertFalse(ArticleProcessor.embeddable("https://weibo.com/u/123", null))
        assertFalse(ArticleProcessor.embeddable("https://evil.com/?u=youtube.com", null))
        assertFalse(ArticleProcessor.embeddable("https://notyoutube.com/embed/x", null))
        assertFalse(ArticleProcessor.embeddable("javascript:alert(1)", null))
        // 和文章同一个主机（不管 www.）才算自己网站的嵌入，同域名的其它子域名（广告）不算
        assertTrue(ArticleProcessor.embeddable("https://www.dapenti.com/blog/audio-card-v2.html?x=1", "http://www.dapenti.com/blog/more.asp?id=1"))
        assertTrue(ArticleProcessor.embeddable("https://dapenti.com/card", "https://www.dapenti.com/blog/1"))
        assertFalse(ArticleProcessor.embeddable("https://ads.dapenti.com/card", "https://www.dapenti.com/blog/1"))
        assertFalse(ArticleProcessor.embeddable("https://www.dapenti.com/card", null))
    }
}
