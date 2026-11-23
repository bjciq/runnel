package io.github.xccxcxxc.runnel.ui.theme

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.PI
import kotlin.math.abs

/**
 * 动画参数集中在这里。iOS 的弹簧用 (response, dampingFraction) 描述，
 * 换算到 Compose：stiffness = (2π / response)²，dampingRatio = dampingFraction（质量取 1）。
 * 例如 iOS 默认 spring(response 0.5, damping 1.0) ≈ spring(dampingRatio = 1f, stiffness ≈ 158f)。
 */
object Motion {
    /** 设置 → 动画 → 速度：所有弹簧的 response 都除以它（越大越快）。 */
    @Volatile var speed = 1f

    fun <T> ios(response: Float, dampingFraction: Float = 1f, visibilityThreshold: T? = null): SpringSpec<T> {
        val k = (2 * PI / (response / speed)).let { it * it }.toFloat()
        return spring(dampingRatio = dampingFraction, stiffness = k, visibilityThreshold = visibilityThreshold)
    }

    /** 页面推入/返回：接近 UIKit 导航转场的手感。 */
    val navigation: SpringSpec<Float> get() = ios(0.42f, 1f, 0.0005f)

    /** 侧滑返回松手后：带一点手指速度，略快。 */
    val navigationRelease: SpringSpec<Float> get() = ios(0.35f, 1f, 0.0005f)

    /** 底部表单（page sheet）弹出/收起：接近 UIKit 的 sheet 转场。 */
    val sheet: SpringSpec<Float> get() = ios(0.5f, 1f, 0.0005f)

    /** 下拉表单松手后：带手指速度，回位或收起。 */
    val sheetRelease: SpringSpec<Float> get() = ios(0.4f, 1f, 0.0005f)

    /** 表单被推开时下层页面缩到的比例（iOS 约 0.92）。 */
    const val SHEET_BEHIND_SCALE = 0.92f

    /** 行侧滑回弹、按钮等小动作。 */
    val snappy: SpringSpec<Float> get() = ios(0.3f, 0.86f, 0.5f)

    /**
     * iOS UIScrollView 正常减速率是每毫秒 0.998：v(t) = v0 · 0.998^(t ms) = v0 · e^(-2.002 t s)。
     * Compose 的 exponentialDecay 衰减系数是 -4.2 × frictionMultiplier，所以取 2.002 / 4.2 ≈ 0.477。
     */
    val iosDecay: DecayAnimationSpec<Float> = exponentialDecay(frictionMultiplier = 0.477f, absVelocityThreshold = 0.5f)
}

@Composable
fun rememberIosFlingBehavior(): FlingBehavior = remember { IosFlingBehavior(Motion.iosDecay) }

/** 和 Compose 默认 fling 相同的结构，只是换成 iOS 的减速曲线。剩余速度交给回弹效果。 */
private class IosFlingBehavior(private val decay: DecayAnimationSpec<Float>) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (abs(initialVelocity) <= 1f) return initialVelocity
        var velocityLeft = initialVelocity
        var lastValue = 0f
        AnimationState(initialValue = 0f, initialVelocity = initialVelocity).animateDecay(decay) {
            val delta = value - lastValue
            val consumed = scrollBy(delta)
            lastValue = value
            velocityLeft = velocity
            if (abs(delta - consumed) > 0.5f) cancelAnimation()
        }
        return velocityLeft
    }
}
