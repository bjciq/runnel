package io.github.xccxcxxc.runnel.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xccxcxxc.runnel.Graph
import io.github.xccxcxxc.runnel.data.ReaderFont
import io.github.xccxcxxc.runnel.data.Settings
import io.github.xccxcxxc.runnel.data.ThemeMode
import io.github.xccxcxxc.runnel.ui.components.RunnelIcons
import io.github.xccxcxxc.runnel.ui.components.GroupedRow
import io.github.xccxcxxc.runnel.ui.components.GroupedSection
import io.github.xccxcxxc.runnel.ui.components.PickerRow
import io.github.xccxcxxc.runnel.ui.components.RowDivider
import io.github.xccxcxxc.runnel.ui.components.SliderRow
import io.github.xccxcxxc.runnel.ui.components.SquareToggleGroup
import io.github.xccxcxxc.runnel.ui.components.SwitchRow
import io.github.xccxcxxc.runnel.ui.theme.Runnel

/**
 * 阅读样式的设置项：设置 → 阅读，和阅读页右上角“…”弹出的面板（两处是同一组设置，改了文章立刻跟着变）。
 * [compact] = 阅读页面板：不要分组标题，加上主题（面板第一项就是 Theme）。
 */
@Composable
fun ReaderStyleSections(compact: Boolean) {
    val prefs = Graph.get().prefs
    val settings by prefs.settings.collectAsStateWithLifecycle()
    val colors = Runnel.colors
    fun title(t: String) = if (compact) null else t

    if (compact) {
        GroupedSection {
            PickerRow("主题", listOf(ThemeMode.SYSTEM to "跟随系统", ThemeMode.LIGHT to "浅色", ThemeMode.DARK to "深色"), settings.theme) { v ->
                prefs.updateSettings { it.copy(theme = v) }
            }
        }
        GroupedSection {
            SwitchRow("自动隐藏/显示栏", settings.autoHideBars) { v -> prefs.updateSettings { it.copy(autoHideBars = v) } }
            RowDivider()
            SwitchRow("彩色链接", settings.coloredLinks) { v -> prefs.updateSettings { it.copy(coloredLinks = v) } }
            RowDivider()
            SwitchRow("增加对比度", settings.highContrast) { v -> prefs.updateSettings { it.copy(highContrast = v) } }
        }
    } else {
        GroupedSection("工具栏") {
            SwitchRow("自动隐藏/显示栏", settings.autoHideBars) { v -> prefs.updateSettings { it.copy(autoHideBars = v) } }
        }
        GroupedSection("颜色") {
            SwitchRow("彩色链接", settings.coloredLinks) { v -> prefs.updateSettings { it.copy(coloredLinks = v) } }
            RowDivider()
            SwitchRow("增加对比度", settings.highContrast) { v -> prefs.updateSettings { it.copy(highContrast = v) } }
        }
    }
    GroupedSection(title("文字")) {
        PickerRow(
            "字体",
            listOf(ReaderFont.DEFAULT to "默认", ReaderFont.SERIF to "衬线", ReaderFont.MONO to "等宽"),
            settings.readerFont,
        ) { v -> prefs.updateSettings { it.copy(readerFont = v) } }
        RowDivider()
        SwitchRow("使用默认字号", settings.useDefaultFontSize) { v -> prefs.updateSettings { it.copy(useDefaultFontSize = v) } }
        RowDivider()
        SliderRow(
            if (compact) "字号" else "大小", settings.readerFontSize.toFloat(), 14f..32f, steps = 17,
            display = settings.readerFontSize.toString(), enabled = !settings.useDefaultFontSize,
        ) { v -> prefs.updateSettings { it.copy(readerFontSize = v.toInt()) } }
        RowDivider()
        SliderRow(
            "行距", settings.readerLineSpacing, 1.2f..2.4f, steps = 11,
            display = "%.1f".format(settings.readerLineSpacing),
        ) { v -> prefs.updateSettings { it.copy(readerLineSpacing = Math.round(v * 10) / 10f) } }
    }
    GroupedSection(title("标题")) {
        SwitchRow("标题使用系统字体", settings.titleSystemFont) { v -> prefs.updateSettings { it.copy(titleSystemFont = v) } }
        RowDivider()
        SliderRow(
            if (compact) "标题大小" else "大小", settings.titleScale, 1.0f..2.2f, steps = 11,
            display = "%.1f".format(settings.titleScale),
        ) { v -> prefs.updateSettings { it.copy(titleScale = Math.round(v * 10) / 10f) } }
        RowDivider()
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SquareToggleGroup(listOf(true), settings.titleBold, { prefs.updateSettings { it.copy(titleBold = !it.titleBold) } }) { _, on ->
                Text("B", style = Runnel.type.headline.copy(fontWeight = FontWeight.Black), color = if (on) Color.White else colors.secondaryLabel)
            }
            SquareToggleGroup(listOf(false, true), settings.titleUppercase, { v -> prefs.updateSettings { it.copy(titleUppercase = v) } }) { upper, on ->
                Text(if (upper) "ABC" else "Abc", style = Runnel.type.callout, color = if (on) Color.White else colors.secondaryLabel)
            }
            SquareToggleGroup(listOf(false, true), settings.titleCenter, { v -> prefs.updateSettings { it.copy(titleCenter = v) } }) { center, on ->
                Icon(
                    if (center) RunnelIcons.AlignCenter else RunnelIcons.AlignLeft, if (center) "居中" else "左对齐",
                    tint = if (on) Color.White else colors.secondaryLabel,
                    modifier = Modifier.padding(2.dp),
                )
            }
        }
    }
}

/** “恢复默认设置…”：只重置阅读样式这一组（栏、颜色、字体、字号、行距、标题样式）。 */
@Composable
fun ReaderStyleReset() {
    val prefs = Graph.get().prefs
    val colors = Runnel.colors
    var confirm by remember { mutableStateOf(false) }
    GroupedSection {
        GroupedRow("恢复默认设置…", titleColor = colors.destructive, onClick = { confirm = true })
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("恢复阅读设置的默认值？") },
            text = { Text("只会重置阅读样式（栏、颜色、字体、字号、行距、标题样式），不影响其他设置。") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    val d = Settings()
                    prefs.updateSettings {
                        it.copy(
                            autoHideBars = d.autoHideBars, coloredLinks = d.coloredLinks,
                            highContrast = d.highContrast, readerFont = d.readerFont, useDefaultFontSize = d.useDefaultFontSize,
                            readerFontSize = d.readerFontSize, readerLineSpacing = d.readerLineSpacing,
                            titleSystemFont = d.titleSystemFont, titleScale = d.titleScale, titleBold = d.titleBold,
                            titleUppercase = d.titleUppercase, titleCenter = d.titleCenter,
                        )
                    }
                }) { Text("恢复默认", color = colors.destructive) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消", color = colors.icon) } },
            containerColor = colors.cell,
        )
    }
}
