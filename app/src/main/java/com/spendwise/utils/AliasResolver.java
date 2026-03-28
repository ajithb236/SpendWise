package com.spendwise.utils;

import com.spendwise.data.Alias;

import java.util.List;
import java.util.Locale;

public final class AliasResolver {

    private AliasResolver() {
    }

    public static String resolveAliasForText(String text, List<Alias> aliases) {
        Alias matched = resolveAliasEntryForText(text, aliases);
        return matched != null && matched.getAliasName() != null ? matched.getAliasName().trim() : null;
    }

    public static Alias resolveAliasEntryForText(String text, List<Alias> aliases) {
        if (text == null || aliases == null || aliases.isEmpty()) {
            return null;
        }

        Alias bestMatch = null;
        int bestOriginalLength = -1;

        for (Alias alias : aliases) {
            if (alias == null) {
                continue;
            }

            String original = alias.getOriginalName();
            String aliasName = alias.getAliasName();
            if (original == null || original.trim().isEmpty() || aliasName == null || aliasName.trim().isEmpty()) {
                continue;
            }

            if (matches(text, original)) {
                int len = normalize(original).length();
                if (len > bestOriginalLength) {
                    bestOriginalLength = len;
                    bestMatch = alias;
                }
            }
        }

        return bestMatch;
    }

    private static boolean matches(String text, String original) {
        String normalizedText = normalize(text);
        String normalizedOriginal = normalize(original);

        if (normalizedText.isEmpty() || normalizedOriginal.isEmpty()) {
            return false;
        }

        if (normalizedText.contains(normalizedOriginal)) {
            return true;
        }

        String normalizedTextToken = normalizedToken(text);
        String normalizedOriginalToken = normalizedToken(original);
        if (!normalizedTextToken.isEmpty() && !normalizedOriginalToken.isEmpty()) {
            if (normalizedTextToken.contains(normalizedOriginalToken)
                    || normalizedOriginalToken.contains(normalizedTextToken)) {
                return true;
            }
        }

        String vpaLocalPart = extractVpaLocalPart(text);
        if (!vpaLocalPart.isEmpty()) {
            String normalizedVpaLocalPart = normalize(vpaLocalPart);
            if (normalizedVpaLocalPart.contains(normalizedOriginal)
                    || normalizeToken(vpaLocalPart).contains(normalizedOriginalToken)) {
                return true;
            }
        }

        return false;
    }

    private static String extractVpaLocalPart(String value) {
        if (value == null) {
            return "";
        }
        int at = value.indexOf('@');
        if (at <= 0) {
            return "";
        }
        return value.substring(0, at);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.getDefault());
    }

    private static String normalizedToken(String value) {
        return normalizeToken(normalize(value));
    }

    private static String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^a-z0-9]", "");
    }
}
