package io.github.xccxcxxc.runnel.perf

import android.app.Activity
import androidx.core.content.ContextCompat

/**
 * 请求屏幕支持的最高刷新率（同分辨率下）。有些厂商系统默认把第三方 App 限制在 60Hz，
 * 设置 preferredDisplayModeId 后系统会按 App 的偏好切到 120Hz/144Hz。关掉设置则交还系统决定。
 */
object RefreshRate {
    fun apply(activity: Activity, preferMax: Boolean) {
        val display = ContextCompat.getDisplayOrDefault(activity)
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate }
        val target = if (preferMax && best != null) best.modeId else 0
        val lp = activity.window.attributes
        if (lp.preferredDisplayModeId != target) {
            lp.preferredDisplayModeId = target
            activity.window.attributes = lp
        }
    }
}
