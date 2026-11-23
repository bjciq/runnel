package io.github.xccxcxxc.runnel.ui.theme

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.xccxcxxc.runnel.data.AppFont
import io.github.xccxcxxc.runnel.data.Settings
import top.yukonga.miuix.kmp.utils.MiuixOverscrollFactory

/** 配色参照 iOS 系统色；强调色是青绿，和 App 图标底色同一色系（res/drawable/ic_launcher_background.xml）。 */
@Immutable
data class RunnelColors(
    val isDark: Boolean,
    val background: Color,
    val groupedBackground: Color,
    val cell: Color,
    val bar: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val highlight: Color,
    val accent: Color,
    val icon: Color,
    val star: Color,
    val readAction: Color,
    val destructive: Color,
)

val LightColors = RunnelColors(
    isDark = false,
    background = Color(0xFFF8F7F5),
    groupedBackground = Color(0xFFEFEEEB),
    cell = Color(0xFFF8F7F5),
    bar = Color(0xF2F8F7F5),
    label = Color(0xFF4A4A4A),
    secondaryLabel = Color(0xFF8E8E8E),
    tertiaryLabel = Color(0xFFB4B4B4),
    separator = Color(0xFFE6E4E1),
    highlight = Color(0xFFE8E6E3),
    accent = Color(0xFF2F8F83),
    icon = Color(0xFF6E6E6E),
    star = Color(0xFFE9A92B),
    readAction = Color(0xFF5A8FD6),
    destructive = Color(0xFFD9473A),
)

val DarkColors = RunnelColors(
    isDark = true,
    background = Color(0xFF171615),
    groupedBackground = Color(0xFF0F0E0D),
    cell = Color(0xFF222120),
    bar = Color(0xF2171615),
    label = Color(0xFFD9D7D4),
    secondaryLabel = Color(0xFF8E8C89),
    tertiaryLabel = Color(0xFF5E5C5A),
    separator = Color(0xFF2E2D2B),
    highlight = Color(0xFF2A2927),
    accent = Color(0xFF4FB3A6),
    icon = Color(0xFFA09E9B),
    star = Color(0xFFE9B23B),
    readAction = Color(0xFF6A9FE0),
    destructive = Color(0xFFE5584B),
)

/** 字号参照 iOS 动态字体的默认档。 */
@Immutable
data class RunnelType(
    val largeTitle: TextStyle = TextStyle(fontSize = 33.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    val title: TextStyle = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    val headline: TextStyle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    val body: TextStyle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp),
    val callout: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 21.sp),
    val subhead: TextStyle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    val footnote: TextStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    val caption: TextStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
)

/** App 字号档位对应的缩放（M = 1.0）。 */
val FontSteps = floatArrayOf(0.85f, 0.93f, 1.0f, 1.1f, 1.22f)
val FontStepLabels = listOf("XS", "S", "M", "L", "XL")

private fun RunnelType.withFamily(f: FontFamily) = RunnelType(
    largeTitle.copy(fontFamily = f), title.copy(fontFamily = f), headline.copy(fontFamily = f), body.copy(fontFamily = f),
    callout.copy(fontFamily = f), subhead.copy(fontFamily = f), footnote.copy(fontFamily = f), caption.copy(fontFamily = f),
)

val LocalColors = staticCompositionLocalOf { LightColors }
val LocalType = staticCompositionLocalOf { RunnelType() }
val LocalListFling = staticCompositionLocalOf<FlingBehavior?> { null }

object Runnel {
    val colors: RunnelColors @Composable get() = LocalColors.current
    val type: RunnelType @Composable get() = LocalType.current
}

/** 列表统一用这个 fling：开了“iOS 式惯性”时滑得更远更顺，否则用系统默认。 */
@Composable
fun listFling(): FlingBehavior = LocalListFling.current ?: ScrollableDefaults.flingBehavior()

@Composable
fun RunnelTheme(settings: Settings, content: @Composable () -> Unit) {
    val iosFling = settings.iosFling
    val grayIcons = settings.grayFavicons
    val dark = when (settings.theme) {
        io.github.xccxcxxc.runnel.data.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        io.github.xccxcxxc.runnel.data.ThemeMode.LIGHT -> false
        io.github.xccxcxxc.runnel.data.ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkColors else LightColors
    val material = if (dark) {
        darkColorScheme(primary = colors.accent, background = colors.background, surface = colors.cell, onSurface = colors.label)
    } else {
        lightColorScheme(primary = colors.accent, background = colors.background, surface = colors.cell, onSurface = colors.label)
    }
    val fling = if (iosFling) rememberIosFlingBehavior() else null
    val type = remember(settings.appFont) {
        if (settings.appFont == AppFont.SERIF) RunnelType().withFamily(FontFamily.Serif) else RunnelType()
    }
    // “使用系统字号”关闭时，按档位（XS S M L XL）缩放 App 内所有文字
    val density = LocalDensity.current
    val scaled = if (settings.useSystemFontSize) density
    else Density(density.density, density.fontScale * FontSteps[settings.appFontStep.coerceIn(0, FontSteps.lastIndex)])
    MaterialTheme(colorScheme = material) {
        CompositionLocalProvider(
            LocalColors provides colors,
            LocalType provides type,
            LocalDensity provides scaled,
            LocalListFling provides fling,
            io.github.xccxcxxc.runnel.ui.components.LocalGrayIcons provides grayIcons,
            // 所有可滚动容器到边缘时都是 iOS 式橡皮筋回弹（Miuix 实现）
            LocalOverscrollFactory provides MiuixOverscrollFactory,
            content = content,
        )
    }
}
