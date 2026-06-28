package com.squad.backend.utils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PhoneUtils {

    private PhoneUtils() {
    }

    /** Normalizes to digits-only for comparison (strips +, spaces, dashes). */
    public static String normalize(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        return phone.replaceAll("\\D", "");
    }

    public static String formatWithPlus(String phone) {
        String digits = normalize(phone);
        if (digits.isEmpty()) {
            return "";
        }
        return "+" + digits;
    }

    /** Distinct stored-form candidates for repository lookup. */
    public static List<String> lookupVariants(String phone) {
        if (phone == null || phone.isBlank()) {
            return List.of();
        }
        Set<String> variants = new LinkedHashSet<>();
        String trimmed = phone.trim();
        variants.add(trimmed);
        String withPlus = formatWithPlus(trimmed);
        if (!withPlus.isEmpty()) {
            variants.add(withPlus);
        }
        String digits = normalize(trimmed);
        if (!digits.isEmpty()) {
            variants.add(digits);
        }
        return new ArrayList<>(variants);
    }

    public static boolean matches(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return a.equals(b);
    }
}
