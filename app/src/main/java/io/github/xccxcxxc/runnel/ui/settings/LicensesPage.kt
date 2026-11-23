package io.github.xccxcxxc.runnel.ui.settings

import android.content.Intent
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.xccxcxxc.runnel.R
import io.github.xccxcxxc.runnel.ui.components.GroupedRow
import io.github.xccxcxxc.runnel.ui.components.GroupedSection
import io.github.xccxcxxc.runnel.ui.components.RowDivider
import io.github.xccxcxxc.runnel.ui.theme.Runnel

private const val RUNNEL_URL = "https://github.com/xccxcxxc/runnel"

private class Dependency(val name: String, val license: String, val url: String)

// 手写列表，不用依赖 Play 服务的 oss-licenses 插件。新增依赖时到这里补一行。
private val dependencies = listOf(
    Dependency("Miuix", "Apache-2.0", "https://github.com/compose-miuix-ui/miuix"),
    Dependency("Readability4J", "Apache-2.0", "https://github.com/dankito/Readability4J"),
    Dependency("OkHttp", "Apache-2.0", "https://github.com/square/okhttp"),
    Dependency("Coil", "Apache-2.0", "https://github.com/coil-kt/coil"),
    Dependency("jsoup", "MIT", "https://jsoup.org/"),
    Dependency("AndroidX（Core、Lifecycle、Activity、WorkManager、Room 等）", "Apache-2.0", "https://developer.android.com/jetpack/androidx"),
    Dependency("Jetpack Compose", "Apache-2.0", "https://developer.android.com/jetpack/compose"),
    Dependency("Kotlin 与 kotlinx（协程、序列化）", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
)

/** 设置 → 开源许可：Runnel 自己的许可证（点开是源码仓库）、依赖列表（点一行打开项目主页）、随 APK 附上的许可证全文。 */
@Composable
internal fun LicensesPage() {
    val context = LocalContext.current
    Column {
        GroupedSection(
            footer = "Runnel 是自由软件，按 GNU 通用公共许可证第 3 版（GPL-3.0）发布。你可以自由使用、修改和分发，" +
                "分发修改版时需要同样以 GPL-3.0 公开源代码。本程序不提供任何担保。",
        ) {
            GroupedRow("Runnel", value = "GPL-3.0") {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, RUNNEL_URL.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
        GroupedSection("使用的开源项目") {
            dependencies.forEachIndexed { i, d ->
                if (i > 0) RowDivider()
                GroupedRow(d.name, value = d.license) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, d.url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
        }
        LicenseText("Apache License 2.0", R.raw.license_apache_2_0)
        LicenseText("MIT License（jsoup）", R.raw.license_mit_jsoup)
        LicenseText("GNU General Public License v3.0（Runnel）", R.raw.license_gpl_3_0)
    }
}

@Composable
private fun LicenseText(title: String, @RawRes res: Int) {
    val context = LocalContext.current
    val text = remember(res) { context.resources.openRawResource(res).bufferedReader().use { it.readText() } }
    GroupedSection(title) {
        Text(
            text,
            style = Runnel.type.caption,
            color = Runnel.colors.secondaryLabel,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}
