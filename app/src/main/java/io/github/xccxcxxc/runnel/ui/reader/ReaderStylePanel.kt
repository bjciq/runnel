package io.github.xccxcxxc.runnel.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.xccxcxxc.runnel.ui.nav.BlockSwipeBack
import io.github.xccxcxxc.runnel.ui.settings.ReaderStyleReset
import io.github.xccxcxxc.runnel.ui.settings.ReaderStyleSections
import io.github.xccxcxxc.runnel.ui.theme.Runnel
import io.github.xccxcxxc.runnel.ui.theme.Motion

/**
 * 阅读页右上角“…”：从底部升起的阅读设置面板。
 * 改设置时后面的文章立刻跟着变；点面板外面或返回键收起。
 */
@Composable
fun ReaderStylePanel(visible: Boolean, onDismiss: () -> Unit) {
    BackHandler(enabled = visible, onBack = onDismiss)
    // 面板开着时不能右滑返回，否则往右拖滑块会把阅读页拖走
    BlockSwipeBack(visible)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f))
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible,
            enter = slideInVertically(Motion.ios(0.4f)) { it },
            exit = slideOutVertically(Motion.ios(0.3f)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.62f)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(Runnel.colors.background)
                    // 面板里的点击不要穿透到下面的遮罩
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .verticalScroll(rememberScrollState())
                    .testTag("reader_style_panel"),
            ) {
                ReaderStyleSections(compact = true)
                ReaderStyleReset()
                Spacer(Modifier.height(16.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}
