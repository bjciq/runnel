package io.github.xccxcxxc.runnel.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 冷启动（TTID/TTFD）。目标：TTID < 500ms。 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 8,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait(demoIntent())
        waitHome()
    }
}

/** 列表滚动帧时间。目标：frameOverrunMs P99 ≤ 0，卡顿帧 < 1%。 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test
    fun scrollArticleList() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startActivityAndWait(demoIntent())
            waitHome()
            openAllArticles()
        },
    ) {
        flingList(3)
    }
}

/** 打开文章 + 左右翻页 + 侧滑返回的帧时间。 */
@RunWith(AndroidJUnit4::class)
class ReaderBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test
    fun openAndSwipeArticles() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startActivityAndWait(demoIntent())
            waitHome()
            openAllArticles()
        },
    ) {
        openArticle(1)
        swipeArticles(3)
        device.pressBack()
        device.waitForIdle()
    }
}

/** 生成 Baseline Profile：scripts/perf.sh profile 会把结果拷到 app/src/main/baseline-prof.txt。 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait(demoIntent())
        waitHome()
        openAllArticles()
        flingList(2)
        openArticle(1)
        swipeArticles(2)
        device.pressBack()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }
}
