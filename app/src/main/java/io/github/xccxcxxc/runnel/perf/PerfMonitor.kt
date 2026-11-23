package io.github.xccxcxxc.runnel.perf

import android.app.Activity
import android.os.Build
import android.os.StrictMode
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import io.github.xccxcxxc.runnel.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 帧统计（JankStats，release 包也开着）：设置页能看到本次启动以来的卡顿比例，
 * 用户反馈“卡”的时候有数字可看。debug 包另外开 StrictMode，主线程读写磁盘/网络会在 logcat 报出来。
 */
object PerfMonitor {
    data class Stats(val total: Long, val janky: Long, val worstMs: Double, val refreshRate: Float) {
        val jankPercent: Double get() = if (total == 0L) 0.0 else janky * 100.0 / total
    }

    data class ScreenStats(var total: Long = 0, var janky: Long = 0, var worstNs: Long = 0)

    /** 一次卡顿：发生时间、页面、当时在做什么、这一帧多长。 */
    data class JankEvent(val timeMillis: Long, val screen: String, val action: String, val ms: Double)

    /** 一次文章列表点击诊断记录：用于真机排查快速滑动时点击错位。 */
    data class ClickEvent(
        val timeMillis: Long,
        val outcome: String,
        val itemId: Long,
        val rowIndex: Int,
        val title: String,
        val visibleRange: String,
        val scrolling: Boolean,
        val prefetch: Boolean,
    )

    private val perScreen = java.util.concurrent.ConcurrentHashMap<String, ScreenStats>()
    private val perAction = java.util.concurrent.ConcurrentHashMap<String, ScreenStats>()
    private val recent = ArrayDeque<JankEvent>()
    private val clickEvents = ArrayDeque<ClickEvent>()
    private const val RECENT_MAX = 12
    private const val CLICK_LOG_MAX = 40
    private const val IDLE = "无操作"

    // 当前在做什么（主线程改）：页面转场、列表滚动（按列表计，可能有两层页面的列表）、后台同步
    private var transition: String? = null
    private val scrolling = HashSet<Any>()
    private var syncing = false
    private var total = 0L
    private var janky = 0L
    private var worstNs = 0L
    private var refreshRate = 60f
    private var jankStats: JankStats? = null
    private var metricsState: PerformanceMetricsState.Holder? = null

    fun install(activity: Activity) {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectActivityLeaks().penaltyLog().build())
        }
        updateRefreshRate(activity)
        metricsState = PerformanceMetricsState.getHolderForHierarchy(activity.window.decorView)
        jankStats = JankStats.createAndTrack(activity.window) { frame ->
            total++
            val screen = frame.states.firstOrNull { it.key == "screen" }?.value ?: "?"
            val action = frame.states.firstOrNull { it.key == "action" }?.value ?: IDLE
            for (st in listOf(perScreen.getOrPut(screen) { ScreenStats() }, perAction.getOrPut(action) { ScreenStats() })) {
                st.total++
                if (frame.isJank) st.janky++
                if (frame.frameDurationUiNanos > st.worstNs) st.worstNs = frame.frameDurationUiNanos
            }
            if (frame.isJank) {
                janky++
                val ms = frame.frameDurationUiNanos / 1_000_000.0
                synchronized(recent) {
                    recent.addFirst(JankEvent(System.currentTimeMillis(), screen, action, ms))
                    if (recent.size > RECENT_MAX) recent.removeLast()
                }
                // release 包也打：模拟器/真机用 logcat 看卡顿发生在哪个操作
                Log.i("RunnelPerf", "jank %.1fms screen=%s action=%s".format(ms, screen, action))
            }
            if (frame.frameDurationUiNanos > worstNs) worstNs = frame.frameDurationUiNanos
        }
    }

    fun setTracking(enabled: Boolean) {
        jankStats?.isTrackingEnabled = enabled
    }

    /** 标记当前页面，卡顿日志里能看出发生在哪个页面。转场开始时就要调（新页面的首次组合算新页面的）。 */
    fun setScreen(name: String) {
        metricsState?.state?.putState("screen", name)
    }

    /** 页面转场开始（推入 / 返回 / 下一个订阅源）或结束（null）。 */
    fun setTransition(kind: String?) {
        transition = kind
        updateAction()
    }

    /** 某个列表开始/停止滚动（含惯性）。[list] 区分不同的列表。 */
    fun setScrolling(list: Any, scrolling: Boolean) {
        if (if (scrolling) this.scrolling.add(list) else this.scrolling.remove(list)) updateAction()
    }

    fun setSyncing(syncing: Boolean) {
        if (this.syncing == syncing) return
        this.syncing = syncing
        updateAction()
    }

    private fun updateAction() {
        val action = listOfNotNull(transition, "滚动".takeIf { scrolling.isNotEmpty() }, "同步".takeIf { syncing })
            .joinToString("+").ifEmpty { IDLE }
        metricsState?.state?.putState("action", action)
    }

    fun updateRefreshRate(activity: Activity) {
        refreshRate = ContextCompat.getDisplayOrDefault(activity).refreshRate
    }

    fun snapshot() = Stats(total, janky, worstNs / 1_000_000.0, refreshRate)

    /** 各页面的帧统计：页面名 → (总帧数, 卡顿帧, 最慢一帧 ms)。 */
    fun perScreenSnapshot(): List<Triple<String, ScreenStats, Double>> =
        perScreen.entries.sortedByDescending { it.value.total }.map { Triple(it.key, it.value.copy(), it.value.worstNs / 1_000_000.0) }

    /** 各操作的帧统计，卡顿多的在前。 */
    fun perActionSnapshot(): List<Triple<String, ScreenStats, Double>> =
        perAction.entries.sortedWith(compareByDescending<Map.Entry<String, ScreenStats>> { it.value.janky }.thenByDescending { it.value.total })
            .map { Triple(it.key, it.value.copy(), it.value.worstNs / 1_000_000.0) }

    /** 最近的卡顿，新的在前。 */
    fun recentJanks(): List<JankEvent> = synchronized(recent) { recent.toList() }

    /** 记录文章列表点击（包括正常打开和被滚动/越界拦截的情况），同时写入 logcat（tag: RunnelClick）。 */
    fun recordListClick(
        outcome: String,
        itemId: Long,
        rowIndex: Int,
        title: String,
        visibleRange: String,
        scrolling: Boolean,
        prefetch: Boolean,
    ) {
        val cleanTitle = title.replace('\n', ' ').trim().take(24)
        val event = ClickEvent(System.currentTimeMillis(), outcome, itemId, rowIndex, cleanTitle, visibleRange, scrolling, prefetch)
        synchronized(clickEvents) {
            clickEvents.addFirst(event)
            if (clickEvents.size > CLICK_LOG_MAX) clickEvents.removeLast()
        }
        val msg = "$outcome id=$itemId row=$rowIndex scroll=$scrolling prefetch=$prefetch $visibleRange title=\"$cleanTitle\""
        if (outcome.startsWith("BLOCK")) Log.w("RunnelClick", msg) else Log.i("RunnelClick", msg)
    }

    /** 最近的文章列表点击记录，新的在前。 */
    fun recentListClicks(): List<ClickEvent> = synchronized(clickEvents) { clickEvents.toList() }

    fun clearClickLog() {
        synchronized(clickEvents) { clickEvents.clear() }
    }

    /** 拼成纯文本，方便在“设置 → 手感与性能”一键复制发给开发者排查。 */
    fun formatClickLog(listPrefetch: Boolean, iosFling: Boolean): String {
        val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val events = recentListClicks()
        return buildString {
            appendLine("Runnel ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) | ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("settings: listPrefetch=$listPrefetch iosFling=$iosFling refreshRate=${"%.0f".format(Locale.US, refreshRate)}Hz")
            appendLine("events (${events.size}, newest first):")
            for (e in events) {
                append(fmt.format(Date(e.timeMillis)))
                append(" [").append(e.outcome).append("]")
                append(" id=").append(e.itemId)
                append(" row=").append(e.rowIndex)
                append(" scroll=").append(e.scrolling)
                append(" prefetch=").append(e.prefetch)
                append(' ').append(e.visibleRange)
                append(" title=\"").append(e.title).appendLine('"')
            }
        }
    }

    fun reset() {
        perScreen.clear()
        perAction.clear()
        synchronized(recent) { recent.clear() }
        total = 0
        janky = 0
        worstNs = 0
    }
}
