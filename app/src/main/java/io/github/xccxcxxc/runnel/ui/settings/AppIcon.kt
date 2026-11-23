package io.github.xccxcxxc.runnel.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.xccxcxxc.runnel.R
import io.github.xccxcxxc.runnel.ui.components.RunnelIcons
import io.github.xccxcxxc.runnel.ui.components.GroupedSection
import io.github.xccxcxxc.runnel.ui.components.RowDivider
import io.github.xccxcxxc.runnel.ui.components.pressHighlight
import io.github.xccxcxxc.runnel.ui.theme.Runnel

/**
 * 桌面图标。每个选项对应 AndroidManifest 里一个 activity-alias，换图标 = 启用这个 alias、停用其它的。
 * 以系统里组件的启用状态为准，不另存设置；[enabledByDefault] 要和 manifest 里的 android:enabled 一致。
 */
enum class AppIcon(
    val alias: String,
    val label: String,
    val enabledByDefault: Boolean,
    @param:DrawableRes val background: Int,
    @param:DrawableRes val foreground: Int,
) {
    RIPPLE("LauncherRipple", "涟漪", true, R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground),
    PAGE("LauncherPage", "页中溪", false, R.drawable.ic_launcher_page_background, R.drawable.ic_launcher_page_foreground);

    private fun component(context: Context) = ComponentName(context.packageName, "io.github.xccxcxxc.runnel.$alias")

    private fun isEnabled(context: Context): Boolean =
        when (context.packageManager.getComponentEnabledSetting(component(context))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> enabledByDefault
            else -> false
        }

    companion object {
        fun current(context: Context): AppIcon = entries.firstOrNull { it.isEnabled(context) } ?: RIPPLE

        /** 先启用新的再停用旧的，中间任何时刻桌面上都至少有一个入口。DONT_KILL_APP：换完 App 不退出。 */
        fun set(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                icon.component(context),
                if (icon.enabledByDefault) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(
                    it.component(context),
                    if (it.enabledByDefault) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}

/** 设置 → 外观 → App 图标：每行左边是图标缩略图，选中的打勾。 */
@Composable
internal fun AppIconSection() {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppIcon.current(context)) }
    GroupedSection("App 图标", footer = "换图标后桌面可能要过几秒才更新；有的桌面会把已放在主屏上的图标移走，需要从应用列表重新拖出来。") {
        AppIcon.entries.forEachIndexed { i, icon ->
            if (i > 0) RowDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .testTag("app_icon_${icon.name}")
                    .pressHighlight {
                        if (icon != current) {
                            AppIcon.set(context, icon)
                            current = icon
                        }
                    }
                    .heightIn(min = 46.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconPreview(icon)
                Box(Modifier.width(12.dp))
                Text(icon.label, style = Runnel.type.body, color = Runnel.colors.label, modifier = Modifier.weight(1f))
                if (icon == current) Icon(RunnelIcons.Check, "已选", tint = Runnel.colors.accent, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** 自适应图标在桌面上只露出 108dp 画布中间的 72dp，所以把两层画大 1.5 倍再裁成圆角方。 */
@Composable
private fun IconPreview(icon: AppIcon) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Image(painterResource(icon.background), null, Modifier.requiredSize(60.dp))
        Image(painterResource(icon.foreground), null, Modifier.requiredSize(60.dp))
    }
}
