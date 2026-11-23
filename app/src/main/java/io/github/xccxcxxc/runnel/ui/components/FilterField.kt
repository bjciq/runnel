package io.github.xccxcxxc.runnel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.xccxcxxc.runnel.ui.theme.Runnel

/**
 * 列表顶部的 Filter 输入框：圆角灰底，右侧 ✕。
 * [focusPending] 为 true 时进入组合后抢焦点并弹出键盘，抢到后调用 [onFocused]。
 */
@Composable
fun FilterField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    focusPending: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "筛选",
) {
    val colors = Runnel.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusPending) {
        if (focusPending) {
            runCatching { focus.requestFocus() }
            onFocused()
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.highlight)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = Runnel.type.body, color = colors.tertiaryLabel, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = Runnel.type.body.copy(color = colors.label),
                cursorBrush = SolidColor(colors.icon),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        BarButton(RunnelIcons.Close, "清除", modifier = Modifier.size(36.dp), tint = colors.secondaryLabel, iconSize = 16.dp, onClick = onClear)
    }
}
