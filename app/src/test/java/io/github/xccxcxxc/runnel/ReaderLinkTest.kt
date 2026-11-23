package io.github.xccxcxxc.runnel

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.xccxcxxc.runnel.data.OpenLinks
import io.github.xccxcxxc.runnel.ui.reader.ReaderHtml
import io.github.xccxcxxc.runnel.ui.reader.WebViewPool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/**
 * 阅读页里点链接走的是 [WebViewPool.handleLink]（WebView 的 shouldOverrideUrlLoading 直接调它）：
 * 点标题的内部链接要以原文地址打开浏览器。WebView 点击本身在开发机上测不了，要真机验证。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@Config(sdk = [35])
class ReaderLinkTest {
    private val app = ApplicationProvider.getApplicationContext<RunnelApp>()
    private val original = "https://ex.com/post?id=1&x=2"

    @Test
    fun titleLinkOpensOriginalInDefaultBrowser() {
        Graph.get().prefs.updateSettings { it.copy(openLinks = OpenLinks.DEFAULT_BROWSER) }
        assertTrue(WebViewPool.handleLink(app, Uri.parse(ReaderHtml.openOriginalLink(original))))
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(original, intent.dataString)
    }

    @Test
    fun titleLinkOpensOriginalInAppBrowser() {
        Graph.get().prefs.updateSettings { it.copy(openLinks = OpenLinks.IN_APP) }
        assertTrue(WebViewPool.handleLink(app, Uri.parse(ReaderHtml.openOriginalLink(original))))
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(original, intent.dataString)
    }

    @Test
    fun bodyLinksStillOpen() {
        Graph.get().prefs.updateSettings { it.copy(openLinks = OpenLinks.DEFAULT_BROWSER) }
        assertTrue(WebViewPool.handleLink(app, Uri.parse("https://other.example/x")))
        assertEquals("https://other.example/x", shadowOf(app).nextStartedActivity.dataString)
    }
}
