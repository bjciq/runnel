package io.github.xccxcxxc.runnel

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import io.github.xccxcxxc.runnel.ui.reader.ArticleContentLoader
import io.github.xccxcxxc.runnel.ui.reader.WebViewPool
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h800dp")
class WebViewLoadingTest {
    private val controller = Robolectric.buildActivity(Activity::class.java)
    private val loader = ArticleContentLoader()
    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private val baseUrl = "https://example.com/article"

    @Before
    fun setUp() {
        val activity = controller.setup().visible().get()
        root = FrameLayout(activity)
        activity.setContentView(root)
        web = WebView(activity)
    }

    @After
    fun tearDown() {
        loader.cancel(web)
        root.removeAllViews()
        web.destroy()
        controller.pause().stop().destroy()
    }

    private fun attach() {
        root.addView(web, FrameLayout.LayoutParams(400, 800))
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 400, 800)
        size(400, 800)
        assertTrue(web.isAttachedToWindow)
    }

    private fun drain() = shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)

    /** ShadowWebView 的 provider 不实现 setFrame；补上真实布局提供的尺寸和布局事件。 */
    private fun size(width: Int, height: Int) {
        ReflectionHelpers.setField(web, "mRight", width)
        ReflectionHelpers.setField(web, "mBottom", height)
        shadowOf(web).onLayoutChangeListeners.toList().forEach { it.onLayoutChange(web, 0, 0, width, height, 0, 0, 0, 0) }
    }

    @Test
    fun coldContentWaitsForAttachmentAndLayout() {
        loader.load(web, "<p>首次正文</p>", baseUrl)
        drain()
        assertNull(shadowOf(web).lastLoadDataWithBaseURL)
        assertNull(web.tag)
        attach()
        drain()
        assertNotNull("挂载=${web.isAttachedToWindow}，尺寸=${web.width}×${web.height}", shadowOf(web).lastLoadDataWithBaseURL)
        assertEquals("<p>首次正文</p>", shadowOf(web).lastLoadDataWithBaseURL.data)
        assertEquals("<p>首次正文</p>", web.tag)
    }

    @Test
    fun onlyLatestDocumentLoadsWhenViewBecomesReady() {
        loader.load(web, "旧正文", baseUrl)
        loader.load(web, "新正文", "$baseUrl/new")
        attach()
        drain()
        assertEquals("新正文", shadowOf(web).lastLoadDataWithBaseURL.data)
        assertEquals("$baseUrl/new", shadowOf(web).lastLoadDataWithBaseURL.baseUrl)
    }

    @Test
    fun attachedViewStillWaitsForNonzeroSize() {
        attach()
        size(0, 0)
        loader.load(web, "正文", baseUrl)
        drain()
        assertNull(shadowOf(web).lastLoadDataWithBaseURL)
        size(400, 800)
        drain()
        assertEquals("正文", shadowOf(web).lastLoadDataWithBaseURL.data)
    }

    @Test
    fun recyclingCancelsEvenAnAlreadyPostedLoad() {
        loader.load(web, "已取消的正文", baseUrl)
        attach()
        loader.cancel(web)
        drain()
        assertNull(shadowOf(web).lastLoadDataWithBaseURL)
        assertNull(web.tag)
    }

    @Test
    fun sameDocumentDoesNotReload() {
        attach()
        loader.load(web, "正文", baseUrl)
        drain()
        val first = shadowOf(web).lastLoadDataWithBaseURL
        assertNotNull(first)
        loader.load(web, "正文", baseUrl)
        drain()
        assertSame(first, shadowOf(web).lastLoadDataWithBaseURL)
    }

    @Test
    fun firstObtainResumesWebViewJustLikeTakingParkedArticle() {
        web.destroy()
        web = WebViewPool.obtain(controller.get())
        assertTrue(shadowOf(web).wasOnResumeCalled())
    }
}
