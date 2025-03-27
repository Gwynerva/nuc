package ru.gwynerva.nuc;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class TimeUtils {
    public static String formatTimestamp(long timestamp, String tomorrowStr) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String formattedTime = sdf.format(new Date(timestamp));

        // Check if timestamp is tomorrow
        Calendar today = Calendar.getInstance();
        Calendar timestampCal = Calendar.getInstance();
        timestampCal.setTimeInMillis(timestamp);

        boolean isTomorrow = timestampCal.get(Calendar.DAY_OF_YEAR) != today.get(Calendar.DAY_OF_YEAR) ||
                             timestampCal.get(Calendar.YEAR) != today.get(Calendar.YEAR);

        // Check if it's midnight (0:00) of tomorrow
        if (isTomorrow &&
            timestampCal.get(Calendar.HOUR_OF_DAY) == 0 &&
            timestampCal.get(Calendar.MINUTE) == 0) {
            return tomorrowStr;
        }

        if (isTomorrow) {
            formattedTime += " (" + tomorrowStr + ")";
        }

        return formattedTime;
    }
}
