package io.github.xccxcxxc.runnel.benchmark

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val PACKAGE = "io.github.xccxcxxc.runnel"

/** 带演示数据参数启动：没有账号时自动载入 200 篇演示文章。 */
fun demoIntent(): Intent = Intent(Intent.ACTION_MAIN)
    .setClassName(PACKAGE, "$PACKAGE.MainActivity")
    .putExtra("runnel.demo", true)

fun MacrobenchmarkScope.waitHome() {
    // 第一次要生成演示数据，给足时间
    check(device.wait(Until.hasObject(By.res("home_list")), 90_000)) { "首页没有出现" }
    device.waitForIdle()
}

/** 首页切到“全部”，打开“全部文章”列表。 */
fun MacrobenchmarkScope.openAllArticles() {
    device.findObject(By.res("filter_all"))?.click()
    device.waitForIdle()
    device.findObject(By.res("row_all")).click()
    check(device.wait(Until.hasObject(By.res("article_list")), 10_000)) { "文章列表没有出现" }
    device.waitForIdle()
}

fun MacrobenchmarkScope.flingList(times: Int = 3) {
    val list = device.findObject(By.res("article_list"))
    list.setGestureMargin(device.displayWidth / 5)
    repeat(times) {
        list.fling(Direction.DOWN)
        device.waitForIdle()
    }
    repeat(times) {
        list.fling(Direction.UP)
        device.waitForIdle()
    }
}

/** 点开列表里第 [index] 篇文章（跳过大标题）。 */
fun MacrobenchmarkScope.openArticle(index: Int = 1) {
    val list = device.findObject(By.res("article_list"))
    val rows = list.children
    rows[(index + 1).coerceAtMost(rows.size - 1)].click()
    check(device.wait(Until.hasObject(By.res("reader_pager")), 10_000)) { "阅读页没有出现" }
    device.waitForIdle()
}

/** 在正文里上下快速滑动（测 WebView 滚动），再点“下一篇”切换 [times] 次（测竖向翻页转场）。 */
fun MacrobenchmarkScope.swipeArticles(times: Int = 3) {
    val pager = device.findObject(By.res("reader_pager"))
    pager.setGestureMargin(device.displayHeight / 6)
    repeat(times) {
        pager.fling(Direction.DOWN)
        device.waitForIdle()
        device.findObject(By.desc("下一篇"))?.click()
        device.waitForIdle()
    }
}
