package com.squad.backend.utils;

public final class FrontendLinkUtils {

    private FrontendLinkUtils() {
    }

    /** Builds a hash-routing URL: {@code http://host/#/path} (slash before {@code #}). */
    public static String hashLink(String frontendUrl, String routePath) {
        if (frontendUrl == null || frontendUrl.isBlank()) {
            throw new IllegalArgumentException("Frontend URL is required");
        }
        String base = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
        String path = routePath.startsWith("/") ? routePath.substring(1) : routePath;
        return base + "/#/" + path;
    }
}
