package io.github.xccxcxxc.runnel

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.xccxcxxc.runnel.data.Settings
import io.github.xccxcxxc.runnel.ui.settings.AppIcon
import io.github.xccxcxxc.runnel.ui.settings.AppIconSection
import io.github.xccxcxxc.runnel.ui.theme.RunnelTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/** 设置 → 外观 → App 图标：桌面上始终只有一个入口，点哪个就换成哪个，再点回去恢复默认。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@Config(sdk = [35])
class AppIconTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** 系统桌面能看到的入口（按 alias 名）。 */
    private fun launchers(): List<String> =
        context.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0)
            .map { it.activityInfo.name.substringAfterLast('.') }

    @Test
    fun switchesBetweenIcons() {
        assertEquals(AppIcon.RIPPLE, AppIcon.current(context))
        assertEquals(listOf("LauncherRipple"), launchers())

        rule.setContent { RunnelTheme(Settings()) { AppIconSection() } }
        rule.onNodeWithTag("app_icon_PAGE").performClick()
        rule.waitForIdle()
        assertEquals(AppIcon.PAGE, AppIcon.current(context))
        assertEquals(listOf("LauncherPage"), launchers())

        rule.onNodeWithTag("app_icon_RIPPLE").performClick()
        rule.waitForIdle()
        assertEquals(AppIcon.RIPPLE, AppIcon.current(context))
        assertEquals(listOf("LauncherRipple"), launchers())
    }
}
