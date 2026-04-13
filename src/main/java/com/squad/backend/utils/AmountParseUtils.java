package com.squad.backend.utils;

public final class AmountParseUtils {

    private AmountParseUtils() {
    }

    public static double parseToDoubleSafe(String amount) {
        if (amount == null || amount.isBlank()) return 0.0;
        String normalized = amount.replaceAll("[^\\d.-]", "");
        if (normalized.isBlank()) return 0.0;
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }
}
