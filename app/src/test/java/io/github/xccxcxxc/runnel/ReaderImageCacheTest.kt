package io.github.xccxcxxc.runnel

import io.github.xccxcxxc.runnel.content.OfflineImages
import io.github.xccxcxxc.runnel.content.ReaderImageCache
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 阅读页图片缓存：有网时看过的图，没网时（服务器连不上）还能显示（2026-10-20 真机反馈）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderImageCacheTest {
    @get:Rule
    val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)

    @Test
    fun cachedImageShownOffline() {
        // 很多图床不让缓存或要求每次验证：照样要存下来
        server.enqueue(
            MockResponse.Builder()
                .headers(headersOf("Content-Type", "image/png", "Cache-Control", "no-cache, no-store", "Pragma", "no-cache"))
                .body(okio.Buffer().write(png))
                .build(),
        )
        val cache = ReaderImageCache(OkHttpClient(), tmp.newFolder("img"))
        val url = server.url("/a.png").toString()
        val headers = mapOf("Accept" to "image/avif,image/webp,*/*", "Referer" to "https://blog.example.com/post")

        val first = cache.fetch(url, headers)!!
        assertEquals("image/png", first.mimeType)
        assertArrayEquals(png, first.data.readBytes())
        assertEquals("https://blog.example.com/post", server.takeRequest().headers["Referer"])

        server.close() // 断网
        val offline = cache.fetch(url, headers)!!
        assertArrayEquals(png, offline.data.readBytes())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun offlineWithoutCacheFallsBackToWebView() {
        val cache = ReaderImageCache(OkHttpClient(), tmp.newFolder("img"))
        val url = server.url("/b.png").toString()
        server.close()
        assertNull(cache.fetch(url, emptyMap()))
    }

    @Test
    fun serverErrorFallsBackToWebView() {
        server.enqueue(MockResponse(code = 404))
        val cache = ReaderImageCache(OkHttpClient(), tmp.newFolder("img"))
        assertNull(cache.fetch(server.url("/c.png").toString(), emptyMap()))
    }

    @Test
    fun onlyImagesAreIntercepted() {
        val img = mapOf("Accept" to "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
        assertTrue(ReaderImageCache.isImageRequest("GET", "https", "/x", img))
        assertFalse(ReaderImageCache.isImageRequest("GET", "https", "/embed", mapOf("accept" to "text/html")))
        assertFalse(ReaderImageCache.isImageRequest("POST", "https", "/x.png", img))
        assertFalse(ReaderImageCache.isImageRequest("GET", "data", null, img))
        // 没有 Accept 时按扩展名
        assertTrue(ReaderImageCache.isImageRequest("GET", "http", "/p/IMG_1.JPG", emptyMap()))
        assertFalse(ReaderImageCache.isImageRequest("GET", "http", "/player.js", emptyMap()))
    }

    /** 离线图片：同步后预下载，断网后阅读页照样从缓存拿到；已缓存的不再下载。 */
    @Test
    fun prefetchedImageShownOffline() {
        server.enqueue(
            MockResponse.Builder()
                .headers(headersOf("Content-Type", "image/png", "Cache-Control", "no-store"))
                .body(okio.Buffer().write(png))
                .build(),
        )
        val cache = ReaderImageCache(OkHttpClient(), tmp.newFolder("img"))
        val url = server.url("/p.png").toString()
        assertTrue(cache.prefetch(url, "https://blog.example.com/post"))
        assertEquals("https://blog.example.com/post", server.takeRequest().headers["Referer"])
        assertTrue(cache.prefetch(url, "https://blog.example.com/post"))
        assertEquals(1, server.requestCount)
        assertTrue(cache.usedBytes() > 0)

        server.close() // 断网
        val offline = cache.fetch(url, mapOf("Accept" to "image/webp,*/*", "Referer" to "https://blog.example.com/post"))!!
        assertArrayEquals(png, offline.data.readBytes())
        assertFalse(cache.prefetch(server.url("/other.png").toString(), null))
    }

    /** 预下载的地址和 WebView 请求的一致：src 按文章地址补全，data: 和空的不要。 */
    @Test
    fun imageUrlsResolvedLikeWebView() {
        val html = """<p><img src="/a.png"><img src="b/c.jpg"><img src="https://cdn.ex.com/d.webp"><img src="data:image/png;base64,AAA"></p>"""
        assertEquals(
            listOf("https://ex.com/a.png", "https://ex.com/post/b/c.jpg", "https://cdn.ex.com/d.webp"),
            OfflineImages.imageUrls(html, "https://ex.com/post/1"),
        )
        assertEquals(emptyList<String>(), OfflineImages.imageUrls(null, "https://ex.com/"))
    }
}
