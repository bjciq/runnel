package io.github.xccxcxxc.runnel.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri

/** 文章级的系统动作：分享、用默认浏览器打开、复制链接（列表侧滑、操作菜单、阅读页共用）。 */
object ArticleActions {
    fun share(context: Context, title: String, url: String?) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_TEXT, listOfNotNull(title, url).joinToString("\n"))
        runCatching { context.startActivity(Intent.createChooser(send, null)) }
    }

    /** “Open in Default Browser”：不管“打开链接”设置，总是交给系统默认浏览器。 */
    fun openInDefaultBrowser(context: Context, url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Toast.makeText(context, "没有可以打开链接的浏览器", Toast.LENGTH_SHORT).show() }
    }

    fun copyLink(context: Context, url: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("link", url))
        Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
    }
}
