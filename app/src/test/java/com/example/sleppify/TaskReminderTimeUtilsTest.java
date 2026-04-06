package com.example.sleppify;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class TaskReminderTimeUtilsTest {

    @Test
    public void parseDueAtMillis_convertsAmPmTo24Hour() {
        long dueAt = TaskReminderTimeUtils.parseDueAtMillis("2026-04-03", "9:05 PM");
        assertTrue(dueAt > 0L);

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dueAt);

        assertEquals(2026, calendar.get(Calendar.YEAR));
        assertEquals(Calendar.APRIL, calendar.get(Calendar.MONTH));
        assertEquals(3, calendar.get(Calendar.DAY_OF_MONTH));
        assertEquals(21, calendar.get(Calendar.HOUR_OF_DAY));
        assertEquals(5, calendar.get(Calendar.MINUTE));
    }

    @Test
    public void parseDueAtMillis_handlesMidnight12Am() {
        long dueAt = TaskReminderTimeUtils.parseDueAtMillis("2026-04-04", "12:00 AM");
        assertTrue(dueAt > 0L);

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dueAt);

        assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, calendar.get(Calendar.MINUTE));
    }

    @Test
    public void parseDueAtMillis_returnsNegativeForInvalidInputs() {
        assertEquals(-1L, TaskReminderTimeUtils.parseDueAtMillis("2026-13-40", "9:00 PM"));
        assertEquals(-1L, TaskReminderTimeUtils.parseDueAtMillis("2026-04-03", "25:88"));
        assertEquals(-1L, TaskReminderTimeUtils.parseDueAtMillis("", ""));
    }

    @Test
    public void computeDelayMs_neverReturnsNegative() {
        assertEquals(0L, TaskReminderTimeUtils.computeDelayMs(1000L, 2000L));
        assertEquals(1500L, TaskReminderTimeUtils.computeDelayMs(3500L, 2000L));
    }

    @Test
    public void buildStableReminderId_isDeterministic() {
        String first = TaskReminderTimeUtils.buildStableReminderId("2026-04-03|0|Estudiar|9:00 PM|Matematica");
        String second = TaskReminderTimeUtils.buildStableReminderId("2026-04-03|0|Estudiar|9:00 PM|Matematica");
        String different = TaskReminderTimeUtils.buildStableReminderId("2026-04-03|1|Estudiar|9:00 PM|Matematica");

        assertEquals(first, second);
        assertNotEquals(first, different);
        assertTrue(first.length() >= 8);
    }
}
