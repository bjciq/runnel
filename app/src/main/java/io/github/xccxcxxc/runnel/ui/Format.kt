package io.github.xccxcxxc.runnel.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val monthDay = DateTimeFormatter.ofPattern("M月d日")
private val fullDate = DateTimeFormatter.ofPattern("yyyy年M月d日")
private val fullDateTime = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")

/** 列表里的相对时间：刚刚 / 5 分钟 / 3 小时 / 昨天 / 4 天 / 3月2日。 */
fun relativeTime(ts: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - ts
    val minutes = diff / 60_000
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟"
        minutes < 24 * 60 -> "${minutes / 60} 小时"
        else -> {
            val zone = ZoneId.systemDefault()
            val date = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
            val today = LocalDate.now(zone)
            val days = today.toEpochDay() - date.toEpochDay()
            when {
                days <= 1L -> "昨天"
                days < 7 -> "$days 天"
                date.year == today.year -> date.format(monthDay)
                else -> date.format(fullDate)
            }
        }
    }
}

fun fullTime(ts: Long): String = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(fullDateTime)

fun syncedAgo(ts: Long, now: Long = System.currentTimeMillis()): String =
    if (ts <= 0) "尚未同步" else {
        val minutes = (now - ts) / 60_000
        when {
            minutes < 1 -> "刚刚更新"
            minutes < 60 -> "$minutes 分钟前更新"
            minutes < 24 * 60 -> "${minutes / 60} 小时前更新"
            else -> "${minutes / 1440} 天前更新"
        }
    }

private val dayHeader = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", java.util.Locale.CHINA)

/** 列表分组标题：今天 / 昨天 / 2026年10月11日 星期日。 */
fun dayLabel(ts: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
    return when (LocalDate.now(zone).toEpochDay() - date.toEpochDay()) {
        0L -> "今天"
        1L -> "昨天"
        else -> date.format(dayHeader)
    }
}

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

fun clockTime(ts: Long): String = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(hhmm)
