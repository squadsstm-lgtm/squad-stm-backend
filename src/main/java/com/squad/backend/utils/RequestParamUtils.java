package com.squad.backend.utils;

public class RequestParamUtils {
    
    /**
     * Safely converts a String parameter to Integer, handling "undefined", null, and empty strings.
     * Returns null if the value cannot be parsed or is "undefined".
     * 
     * @param value The string value to convert
     * @return Integer value or null if invalid/undefined
     */
    public static Integer parseInteger(String value) {
        if (value == null || value.isEmpty() || "undefined".equalsIgnoreCase(value) || "null".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
