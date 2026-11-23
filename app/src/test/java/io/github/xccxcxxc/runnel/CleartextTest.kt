package io.github.xccxcxxc.runnel

import io.github.xccxcxxc.runnel.sync.isPublicCleartext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleartextTest {
    @Test
    fun httpsAndBareHostsNeverWarn() {
        assertFalse(isPublicCleartext("https://rss.example.com"))
        assertFalse(isPublicCleartext("rss.example.com")) // 不写协议时按 https 连
        assertFalse(isPublicCleartext(""))
    }

    @Test
    fun lanAndVpnAddressesDoNotWarn() {
        listOf(
            "http://192.168.1.10:8080", "http://10.0.0.5", "http://172.20.3.4/freshrss", "http://127.0.0.1",
            "http://100.101.102.103", "http://nas:8080", "http://localhost", "http://rss.local",
            "http://freshrss.lan", "http://box.home.arpa", "http://pi.tail1234.ts.net", "http://[fd00::1]:80",
            "HTTP://192.168.0.2",
        ).forEach { assertFalse(it, isPublicCleartext(it)) }
    }

    @Test
    fun publicHttpAddressesWarn() {
        listOf(
            "http://rss.example.com", "http://8.8.8.8", "http://172.32.0.1", "http://100.128.0.1",
            "  http://example.org/api/greader.php ", "http://[2001:db8::1]",
        ).forEach { assertTrue(it, isPublicCleartext(it)) }
    }
}
