package com.spendwise.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DateDisplayUtil {

    private static final String[] INPUT_PATTERNS = new String[] {
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "dd-MM-yyyy",
            "dd/MM/yyyy",
            "dd MMM yyyy",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss"
    };

    private static final SimpleDateFormat OUTPUT_FORMAT = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    private DateDisplayUtil() {
    }

    public static String toIndianDisplayDate(String rawDate) {
        if (rawDate == null) {
            return "";
        }

        String input = rawDate.trim();
        if (input.isEmpty()) {
            return "";
        }

        for (String pattern : INPUT_PATTERNS) {
            SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.getDefault());
            parser.setLenient(false);
            try {
                Date parsed = parser.parse(input);
                if (parsed != null) {
                    return OUTPUT_FORMAT.format(parsed);
                }
            } catch (ParseException ignored) {
            }
        }

        return input;
    }
}