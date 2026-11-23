package io.github.xccxcxxc.runnel.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.xccxcxxc.runnel.content.ArticleProcessor
import io.github.xccxcxxc.runnel.data.ReaderFont
import io.github.xccxcxxc.runnel.data.Settings
import io.github.xccxcxxc.runnel.data.db.ReaderArticle
import io.github.xccxcxxc.runnel.ui.theme.RunnelColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.net.URLEncoder
import java.util.Locale

/**
 * 阅读页 HTML 模板：灰色日期行 → 粗体大标题 → 强调色来源名 → 正文。
 * 排版全部由这里的 CSS 决定（正文内联样式在同步时已去掉），颜色跟随 App 主题。
 * 正文数值参考常见阅读器的样式：正文用浅一档的灰，加粗/小标题/链接用深色
 * （--text-color / --bold-text-color），链接不加下划线；引用竖线和分隔线是正文色 25% 透明度。
 * 不出现横向滚动：代码块自动换行、表格压缩，避免和右滑返回手势冲突。
 * 包着图片的链接整块可点（在浏览器打开），不再在图片下面显示“域名 →”（用户 2026-10-19 要求去掉）。
 */
object ReaderHtml {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE HH:mm", Locale.CHINA)

    /**
     * 正文里的 srcset 显示时去掉，WebView 只请求 src：离线图片预下载的就是 src（同步时已从 srcset 挑好），
     * 否则 WebView 按屏幕密度另挑一张，没网时就对不上缓存。
     */
    private val SRCSET = Regex("""\ssrcset="[^"]*"""")

    /** 同步时放不进来、被换成链接的 iframe（[ArticleProcessor.EMBED_LINK_TEXT]）。 */
    private val EMBED_LINK = Regex("""<a href="([^"]+)">${Regex.escape(ArticleProcessor.EMBED_LINK_TEXT)}</a>""")

    /**
     * 以前同步的文章里，现在能放的嵌入（比如微博视频，2026-10-23 前只放行 YouTube 等）还是链接：显示时换回播放器，
     * 不用重新同步。
     */
    fun restoreEmbeds(body: String, articleUrl: String?): String {
        if (!body.contains(ArticleProcessor.EMBED_LINK_TEXT)) return body
        return EMBED_LINK.replace(body) { m ->
            val src = m.groupValues[1]
            if (ArticleProcessor.embeddable(src.replace("&amp;", "&"), articleUrl)) "<iframe src=\"$src\" allowfullscreen></iframe>" else m.value
        }
    }

    fun build(
        article: ReaderArticle,
        body: String,
        settings: Settings,
        colors: RunnelColors,
        topPaddingDp: Int,
        bottomPaddingDp: Int,
    ): String {
        val font = when (settings.readerFont) {
            ReaderFont.SERIF -> "\"Noto Serif CJK SC\", \"Source Han Serif SC\", \"Noto Serif\", serif"
            ReaderFont.MONO -> "ui-monospace, \"JetBrains Mono\", \"Noto Sans Mono CJK SC\", monospace"
            ReaderFont.DEFAULT -> "system-ui, -apple-system, \"Noto Sans CJK SC\", sans-serif"
        }
        val titleFont = if (settings.titleSystemFont) "system-ui,sans-serif" else "inherit"
        val fontSize = if (settings.useDefaultFontSize) 20 else settings.readerFontSize
        // 正文 = 浅一档的灰（icon 色，浅色主题约 #6f6f6e），加粗/标题 = 深色（label，约 #4a4949）。
        // “增加对比度”：正文改用深色，加粗再进一步到纯黑/纯白
        val bold = if (settings.highContrast) (if (colors.isDark) "#fff" else "#000") else css(colors.label)
        val fg = if (settings.highContrast) css(colors.label) else css(colors.icon)
        val sec = if (settings.highContrast) css(colors.icon) else css(colors.secondaryLabel)
        val rule = css(if (settings.highContrast) colors.label else colors.icon, alpha = 0.25f)
        val linkColor = if (settings.coloredLinks) css(colors.accent) else "var(--bold)"
        val align = if (settings.titleCenter) "center" else "left"
        val date = Instant.ofEpochMilli(article.publishedAt).atZone(ZoneId.systemDefault()).format(dateFormat)
        // 点标题打开原文。不直接链到原文地址：页面的 base URL 就是原文，链到同一地址 WebView 不一定回调
        val title = "<h1 class=\"title\">${escape(article.title)}</h1>"
        val titleHtml = article.url?.let { "<a class=\"original\" href=\"${escape(openOriginalLink(it))}\">$title</a>" } ?: title
        val byline = "<span class=\"src\">${escape(article.feedTitle)}</span>" + (article.author?.takeIf { it.isNotBlank() }?.let { " · " + escape(it) } ?: "")
        return """<!DOCTYPE html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, viewport-fit=cover">
<style>
:root{--bg:${css(colors.background)};--fg:$fg;--bold:$bold;--sec:$sec;--ter:${css(colors.tertiaryLabel)};--rule:$rule;--accent:${css(colors.accent)};--code:${css(colors.highlight)};--pad-top:${topPaddingDp}px;--pad-bottom:${bottomPaddingDp}px;color-scheme:${if (colors.isDark) "dark" else "light"}}
html{-webkit-text-size-adjust:100%}
body{margin:0;padding:var(--pad-top) 20px var(--pad-bottom);background:var(--bg);color:var(--fg);font-family:$font;font-size:${fontSize}px;line-height:${"%.2f".format(Locale.US, settings.readerLineSpacing)};overflow-wrap:anywhere;-webkit-tap-highlight-color:transparent}
header{margin:6px 0 46px;text-align:$align}
.date{color:var(--ter);font-size:15px;letter-spacing:.02em;font-family:system-ui,sans-serif;margin-bottom:6px}
h1.title{font-size:${"%.2f".format(Locale.US, settings.titleScale)}em;line-height:1.2;margin:0 0 6px;color:var(--bold);font-weight:${if (settings.titleBold) 700 else 500};letter-spacing:-.01em;font-family:$titleFont${if (settings.titleUppercase) ";text-transform:uppercase" else ""}}
.feed{color:var(--ter);font-size:16px;font-family:system-ui,sans-serif}
.feed .src{color:var(--accent)}
article{max-width:720px;margin:0 auto}
article p{margin:0 0 1em}
article>*:first-child{margin-top:0}
article strong,article b,article h1,article h2,article h3,article h4,article h5,article h6{color:var(--bold);font-weight:600}
article h1,article h2,article h3,article h4,article h5,article h6{line-height:${"%.2f".format(Locale.US, minOf(1.2f, settings.readerLineSpacing))};margin:1.4em 0 .6em}
article img,article video{max-width:100%;height:auto;display:block;margin:1.2em auto 0}
${EMOJI_SELECTOR}{display:inline;width:auto;height:1.3em;margin:-.25em 0 -.05em;vertical-align:middle;border-radius:0}
article figure{margin:1.4em 0;color:var(--sec);font-size:.8em;line-height:${"%.2f".format(Locale.US, minOf(1.5f, settings.readerLineSpacing))}}
article figure *{font-size:1em}
article figcaption{margin-top:.4em}
a{color:$linkColor;text-decoration:none;font-weight:600;word-break:break-word}
a.original{display:block;color:inherit;font-weight:inherit;text-decoration:none}
a.imglink{display:block;text-decoration:none;text-align:center;margin:1.2em 0;font-weight:normal}
a.imglink img{margin-top:0}
blockquote{margin:1.2em 0 1.2em .5em;padding:0 0 0 .9em;border-left:.1em solid var(--rule)}
pre,code{font-family:ui-monospace,Menlo,"JetBrains Mono","Noto Sans Mono CJK SC",monospace;font-size:.9em}
pre{background:var(--code);padding:10px;border-radius:5px;white-space:pre-wrap;word-break:break-all;line-height:1.5}
code{background:var(--code);padding:0 3px 2px;border-radius:3px}
pre code{background:none;padding:0;font-size:.9em}
table{width:100%;table-layout:fixed;border-collapse:collapse;font-size:.85em;margin:1em 0}
th,td{border:1px solid var(--rule);padding:6px 8px;overflow-wrap:anywhere;vertical-align:top}
iframe{width:100%;aspect-ratio:16/9;height:auto;border:0;border-radius:6px;margin:1em 0;background:var(--code)}
hr{border:0;height:2px;border-radius:2px;background:var(--rule);margin:1.6em 0}
sup,sub{line-height:1;vertical-align:baseline;position:relative}
sup{bottom:.5ex}
sub{top:.5ex}
ul,ol{padding-left:1.4em}
li{margin:.3em 0}
</style></head><body>
<header><div class="date">$date</div>$titleHtml<div class="feed">$byline</div></header>
<article>${restoreEmbeds(body, article.url).replace(SRCSET, "")}</article>
<script>
document.querySelectorAll('article a').forEach(function(a){
  var img=a.querySelector('img');
  if(!img||img.matches('$EMOJI_SELECTOR'))return;
  a.classList.add('imglink');
});
// 嵌入播放器按原来的宽高比显示（喷嚏的音频卡片 640×220，视频 640×420），没写宽高的按 16:9
document.querySelectorAll('article iframe').forEach(function(f){
  var w=f.getAttribute('width'),h=f.getAttribute('height');
  if(/^\d+$/.test(w)&&/^\d+$/.test(h)&&+w>0&&+h>0)f.style.aspectRatio=w+'/'+h;
});
</script>
</body></html>"""
    }

    /**
     * 正文里的表情图片（Discourse / WordPress 的 `/emoji/` 路径、Twemoji、alt 是 `:sob:` 这种短码）：
     * 按 `img[rel="emoji"]` 行内显示、高 1.3em，不当成大图居中。
     * 用 CSS 认，不在同步时改 HTML：已经存进库的文章也生效（FreshRSS 会去掉 class，只能看 src / alt）。
     */
    private const val EMOJI_SELECTOR =
        "article img[src*=\"/emoji/\"],article img[src*=\"twemoji\"],article img[alt^=\":\"][alt\$=\":\"]"

    /** 系统栏变化（全屏看视频、转屏）后改正文上下留白，不重新载入页面。 */
    fun paddingScript(topPaddingDp: Int, bottomPaddingDp: Int): String =
        "document.documentElement.style.setProperty('--pad-top','${topPaddingDp}px');" +
            "document.documentElement.style.setProperty('--pad-bottom','${bottomPaddingDp}px')"

    /** 阅读页内部链接：点了打开原文（[WebViewPool] 的链接处理认这个 scheme）。 */
    const val ORIGINAL_SCHEME = "runnel"

    fun openOriginalLink(url: String): String = "$ORIGINAL_SCHEME://open?u=" + URLEncoder.encode(url, "UTF-8")

    private fun css(c: Color, alpha: Float? = null): String {
        val argb = c.toArgb()
        val a = alpha ?: (((argb ushr 24) and 0xFF) / 255f)
        return "rgba(${(argb shr 16) and 0xFF},${(argb shr 8) and 0xFF},${argb and 0xFF},${"%.3f".format(Locale.US, a)})"
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
