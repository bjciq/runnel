package io.github.xccxcxxc.runnel

import io.github.xccxcxxc.runnel.ui.components.initialOf
import org.junit.Assert.assertEquals
import org.junit.Test

/** 订阅源没有图标时方块里显示的首字。 */
class FeedIconInitialTest {
    @Test
    fun firstCharacterUppercased() {
        assertEquals("T", initialOf("the verge"))
        assertEquals("少", initialOf("  少数派"))
    }

    @Test
    fun emojiIsNotSplit() {
        assertEquals("🚀", initialOf("🚀 Launch"))
    }

    @Test
    fun blankTitleShowsNothing() {
        assertEquals("", initialOf("   "))
    }
}
