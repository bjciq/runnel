package io.github.xccxcxxc.runnel.content

import android.webkit.WebResourceResponse
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.blackholeSink
import java.io.File
import java.io.IOException

/**
 * 阅读页图片的磁盘缓存：WebView 的图片请求都从这里走（[io.github.xccxcxxc.runnel.ui.reader.WebViewPool] 拦截）。
 * 文章里的图片基本不会变，所以不管服务器给的缓存头，一律当成一年内有效存下来：看过的文章没网时图片照样显示，
 * 再次打开也不用重新下载；勾选了离线图片的订阅源，同步后还会预先下载（[OfflineImages]）。
 * WebView 自己的 HTTP 缓存太小，图片带 no-cache 之类的头时没网就不显示（2026-10-20 用户反馈）。
 */
class ReaderImageCache(http: OkHttpClient, dir: File, maxBytes: Long = 500L * 1024 * 1024) {
    private val cache = Cache(dir, maxBytes)
    private val client = http.newBuilder()
        .cache(cache)
        .addNetworkInterceptor { chain ->
            val resp = chain.proceed(chain.request())
            if (!resp.isSuccessful) return@addNetworkInterceptor resp
            resp.newBuilder()
                .removeHeader("Pragma")
                .removeHeader("Expires")
                .removeHeader("Vary")
                .header("Cache-Control", "max-age=31536000")
                .build()
        }
        .build()

    /**
     * 取图片 [url]，[headers] 是 WebView 原请求带的头（Referer、User-Agent 等，有的图床靠 Referer 防盗链）。
     * 没网又没缓存、或服务器返回错误时返回 null，交回 WebView 自己处理。
     */
    fun fetch(url: String, headers: Map<String, String>): WebResourceResponse? {
        val request = request(url, headers) ?: return null
        val resp = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            return null
        }
        if (!resp.isSuccessful) {
            resp.close()
            return null
        }
        val type = resp.body.contentType()
        return WebResourceResponse(
            type?.let { "${it.type}/${it.subtype}" } ?: "image/*",
            type?.charset()?.name(),
            resp.code,
            resp.message.ifBlank { "OK" },
            resp.headers.toMultimap().mapValues { it.value.joinToString(", ") },
            resp.body.byteStream(),
        )
    }

    /**
     * 预先下载 [url] 存进缓存（离线图片用，[referer] 是文章地址，和 WebView 打开文章时带的一样）。
     * 已经缓存过的不再下载。返回 false 表示连不上，调用方据此判断是不是没网了。
     */
    fun prefetch(url: String, referer: String?): Boolean {
        val headers = buildMap {
            put("Accept", IMAGE_ACCEPT)
            if (referer != null) put("Referer", referer)
        }
        val request = request(url, headers) ?: return true
        return try {
            client.newCall(request).execute().use { resp ->
                // 读到底才会写进缓存；缓存命中时 networkResponse 为 null，不用读
                if (resp.isSuccessful && resp.networkResponse != null) resp.body.source().readAll(blackholeSink())
            }
            true
        } catch (e: IOException) {
            false
        }
    }

    /** [download] 的结果：图片字节和服务器给的类型（可能为空，也可能不是图片类型）。 */
    class Downloaded(val bytes: ByteArray, val mime: String?)

    /**
     * 取整张图片的字节（长按保存/分享用）：先走缓存，看过的图不用再下载。阻塞，要在后台线程调用；
     * 连不上或服务器返回错误时返回 null。
     */
    fun download(url: String, referer: String?): Downloaded? {
        val headers = buildMap {
            put("Accept", IMAGE_ACCEPT)
            if (referer != null) put("Referer", referer)
        }
        val request = request(url, headers) ?: return null
        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val type = resp.body.contentType()
                Downloaded(resp.body.bytes(), type?.let { "${it.type}/${it.subtype}" })
            }
        } catch (e: IOException) {
            null
        }
    }

    /** 缓存已用的字节数。 */
    fun usedBytes(): Long = runCatching { cache.size() }.getOrDefault(0L)

    val maxBytes: Long get() = cache.maxSize()

    private fun request(url: String, headers: Map<String, String>): Request? = try {
        Request.Builder().url(url).apply {
            for ((k, v) in headers) {
                // 压缩和分段交给 OkHttp 自己处理，否则拿到的是没解压/不完整的数据
                if (k.equals("Accept-Encoding", true) || k.equals("Range", true)) continue
                header(k, v)
            }
        }.build()
    } catch (e: IllegalArgumentException) {
        null
    }

    companion object {
        /** Chromium 请求 `<img>` 时带的 Accept。 */
        private const val IMAGE_ACCEPT = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"

        private val IMAGE_EXT = Regex("""\.(jpe?g|png|gif|webp|avif|svg|bmp|ico)$""", RegexOption.IGNORE_CASE)

        /** WebView 的这个请求是不是图片：Chromium 请求 `<img>` 时 Accept 以 image/ 开头；没有 Accept 时看扩展名。 */
        fun isImageRequest(method: String, scheme: String?, path: String?, headers: Map<String, String>): Boolean {
            if (method != "GET" || (scheme != "http" && scheme != "https")) return false
            val accept = headers.entries.firstOrNull { it.key.equals("Accept", true) }?.value
            return if (accept != null) accept.startsWith("image/") else path?.let { IMAGE_EXT.containsMatchIn(it) } == true
        }
    }
}
