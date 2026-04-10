package com.example.sleppify

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

object TaskReminderTimeUtils {
    private val TIME_PATTERN: Pattern = Pattern.compile("^(\\d{1,2}):(\\d{2})\\s*([AaPp][Mm])?$")

    @JvmStatic
    fun parseDueAtMillis(dateKey: String?, timeLabel: String?): Long {
        val dayStartMs = parseDateStartMillis(dateKey)
        if (dayStartMs <= 0L) {
            return -1L
        }

        val hourMinute = parseHourMinute(timeLabel) ?: return -1L

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = dayStartMs
        calendar.set(Calendar.HOUR_OF_DAY, hourMinute[0])
        calendar.set(Calendar.MINUTE, hourMinute[1])
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    @JvmStatic
    fun parseDateStartMillis(dateKey: String?): Long {
        if (dateKey == null) {
            return -1L
        }

        val parts = dateKey.trim().split("-")
        if (parts.size != 3) {
            return -1L
        }

        val year: Int
        val month: Int
        val day: Int
        try {
            year = parts[0].toInt()
            month = parts[1].toInt()
            day = parts[2].toInt()
        } catch (_: Exception) {
            return -1L
        }

        if (month !in 1..12 || day !in 1..31) {
            return -1L
        }

        val calendar = Calendar.getInstance()
        calendar.isLenient = false
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month - 1)
        calendar.set(Calendar.DAY_OF_MONTH, day)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        return try {
            calendar.timeInMillis
        } catch (_: Exception) {
            -1L
        }
    }

    @JvmStatic
    fun parseHourMinute(timeLabel: String?): IntArray? {
        if (timeLabel.isNullOrBlank()) {
            return null
        }

        val normalized = timeLabel.trim()
        val matcher = TIME_PATTERN.matcher(normalized)
        if (!matcher.matches()) {
            return null
        }

        val hour: Int
        val minute: Int
        try {
            hour = matcher.group(1)?.toInt() ?: return null
            minute = matcher.group(2)?.toInt() ?: return null
        } catch (_: Exception) {
            return null
        }

        if (minute !in 0..59) {
            return null
        }

        val meridiem = matcher.group(3)
        if (meridiem.isNullOrBlank()) {
            if (hour !in 0..23) {
                return null
            }
            return intArrayOf(hour, minute)
        }

        if (hour !in 1..12) {
            return null
        }

        val upper = meridiem.uppercase(Locale.US)
        var hour24 = hour % 12
        if (upper == "PM") {
            hour24 += 12
        }
        return intArrayOf(hour24, minute)
    }

    @JvmStatic
    fun computeDelayMs(triggerAtMs: Long, nowMs: Long): Long {
        return if (triggerAtMs <= nowMs) 0L else triggerAtMs - nowMs
    }

    @JvmStatic
    fun buildStableReminderId(rawValue: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(rawValue.toByteArray(StandardCharsets.UTF_8))
            buildString {
                for (i in 0 until minOf(hash.size, 12)) {
                    append(String.format("%02x", hash[i]))
                }
            }
        } catch (_: Exception) {
            rawValue.hashCode().toString(16)
        }
    }
}
