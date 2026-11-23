package io.github.xccxcxxc.runnel.content

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dankito.readability4j.Readability4J
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** 抓原网页并用 Readability 提取正文（订阅源只给摘要时用）；虎嗅视频文章改成视频 + 简介（[PageVideo]）。 */
class FullTextFetcher(private val http: OkHttpClient) {
    suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val html = http.newCall(
            Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36")
                .build(),
        ).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body.string()
        }
        withContext(Dispatchers.Default) {
            val content = PageVideo.page(url, html) ?: Readability4J(url, html).parse().let { it.contentWithUtf8Encoding ?: it.content }
                ?: throw IOException("没有识别出正文")
            ArticleProcessor.process(content, url).html
        }
    }
}
