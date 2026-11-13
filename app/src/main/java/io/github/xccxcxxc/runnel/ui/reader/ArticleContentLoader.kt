package io.github.xccxcxxc.runnel.ui.reader

import android.view.View
import android.webkit.WebView
import java.util.WeakHashMap

/** AndroidView 首次更新时还可能没有挂载、尺寸为 0；等容器就绪后再把最新正文交给 Chromium。 */
internal class ArticleContentLoader {
    private data class Document(val html: String, val baseUrl: String)
    private val pending = WeakHashMap<WebView, Document>()
    private val attach = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = schedule(view as WebView)
        override fun onViewDetachedFromWindow(view: View) = Unit
    }
    private val layout = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> schedule(view as WebView) }

    fun load(view: WebView, html: String, baseUrl: String) {
        if (view.tag == html) {
            cancel(view)
            return
        }
        val document = Document(html, baseUrl)
        if (pending[view] == document) return
        if (pending.put(view, document) == null) {
            view.addOnAttachStateChangeListener(attach)
            view.addOnLayoutChangeListener(layout)
        }
        schedule(view)
    }

    fun cancel(view: WebView) {
        pending.remove(view)
        view.removeOnAttachStateChangeListener(attach)
        view.removeOnLayoutChangeListener(layout)
    }

    private fun schedule(view: WebView) {
        if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return
        // 不在首次挂载/布局回调里启动 Chromium；切换正文、回收发生在这之前时，始终以最新请求为准。
        view.post {
            if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return@post
            val document = pending[view] ?: return@post
            cancel(view)
            view.loadDataWithBaseURL(document.baseUrl, document.html, "text/html", "utf-8", null)
            view.tag = document.html
        }
    }
}
