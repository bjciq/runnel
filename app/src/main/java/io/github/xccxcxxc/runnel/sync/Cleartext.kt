package io.github.xccxcxxc.runnel.sync

import java.net.URI

/**
 * 登录地址是 `http://`，而且从地址本身看不出是局域网 / VPN 内的服务器时返回 true，
 * 登录页据此提示“密码会明文传输”。只看地址字面，不做 DNS 解析：公网域名指向内网的情况会多提示一次，不拦截。
 */
fun isPublicCleartext(server: String): Boolean {
    val s = server.trim()
    if (!s.startsWith("http://", ignoreCase = true)) return false
    val host = runCatching { URI(s).host }.getOrNull()?.lowercase()?.removeSurrounding("[", "]") ?: return false
    return !isLocalHost(host)
}

private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home", ".home.arpa", ".internal", ".localdomain", ".ts.net")

internal fun isLocalHost(host: String): Boolean {
    if (host == "localhost" || !host.contains('.') && !host.contains(':')) return true // 单段主机名（如 nas）只在局域网里解析得到
    if (LOCAL_SUFFIXES.any { host.endsWith(it) }) return true
    ipv4(host)?.let { (a, b) ->
        return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168) ||
            (a == 169 && b == 254) || (a == 100 && b in 64..127) // 100.64/10：运营商级 NAT、Tailscale
    }
    if (host.contains(':')) { // IPv6 字面量
        return host == "::1" || host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe80")
    }
    return false
}

/** IPv4 字面量的前两段；不是 IPv4 返回 null。 */
private fun ipv4(host: String): Pair<Int, Int>? {
    val parts = host.split('.')
    if (parts.size != 4) return null
    val n = parts.map { it.toIntOrNull()?.takeIf { v -> v in 0..255 } ?: return null }
    return n[0] to n[1]
}
