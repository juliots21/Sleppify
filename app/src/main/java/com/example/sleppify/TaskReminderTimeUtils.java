package com.example.sleppify;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TaskReminderTimeUtils {

    private static final Pattern TIME_PATTERN = Pattern.compile("^(\\d{1,2}):(\\d{2})\\s*([AaPp][Mm])?$");

    private TaskReminderTimeUtils() {
    }

    static long parseDueAtMillis(@Nullable String dateKey, @Nullable String timeLabel) {
        long dayStartMs = parseDateStartMillis(dateKey);
        if (dayStartMs <= 0L) {
            return -1L;
        }

        int[] hourMinute = parseHourMinute(timeLabel);
        if (hourMinute == null) {
            return -1L;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dayStartMs);
        calendar.set(Calendar.HOUR_OF_DAY, hourMinute[0]);
        calendar.set(Calendar.MINUTE, hourMinute[1]);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    static long parseDateStartMillis(@Nullable String dateKey) {
        if (dateKey == null) {
            return -1L;
        }

        String[] parts = dateKey.trim().split("-");
        if (parts.length != 3) {
            return -1L;
        }

        int year;
        int month;
        int day;
        try {
            year = Integer.parseInt(parts[0]);
            month = Integer.parseInt(parts[1]);
            day = Integer.parseInt(parts[2]);
        } catch (Exception ignored) {
            return -1L;
        }

        if (month < 1 || month > 12 || day < 1 || day > 31) {
            return -1L;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setLenient(false);
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        try {
            return calendar.getTimeInMillis();
        } catch (Exception ignored) {
            return -1L;
        }
    }

    @Nullable
    static int[] parseHourMinute(@Nullable String timeLabel) {
        if (timeLabel == null) {
            return null;
        }

        String normalized = timeLabel.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        Matcher matcher = TIME_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }

        int hour;
        int minute;
        try {
            hour = Integer.parseInt(matcher.group(1));
            minute = Integer.parseInt(matcher.group(2));
        } catch (Exception ignored) {
            return null;
        }

        if (minute < 0 || minute > 59) {
            return null;
        }

        String meridiem = matcher.group(3);
        if (meridiem == null || meridiem.trim().isEmpty()) {
            if (hour < 0 || hour > 23) {
                return null;
            }
            return new int[]{hour, minute};
        }

        if (hour < 1 || hour > 12) {
            return null;
        }

        String upper = meridiem.toUpperCase();
        int hour24 = hour % 12;
        if ("PM".equals(upper)) {
            hour24 += 12;
        }
        return new int[]{hour24, minute};
    }

    static long computeDelayMs(long triggerAtMs, long nowMs) {
        if (triggerAtMs <= nowMs) {
            return 0L;
        }
        return triggerAtMs - nowMs;
    }

    @NonNull
    static String buildStableReminderId(@NonNull String rawValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawValue.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < hash.length && i < 12; i++) {
                builder.append(String.format("%02x", hash[i]));
            }
            return builder.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(rawValue.hashCode());
        }
    }
}
