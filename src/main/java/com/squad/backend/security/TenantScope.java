package com.squad.backend.security;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.model.Auth;

/**
 * Enforces club (and optionally season) boundaries for multi-tenant data.
 * Master Panel {@code Controller} users without a {@code clubId} are treated as platform scope (no deny).
 */
public final class TenantScope {

    private TenantScope() {
    }

    public static boolean isControllerWithoutClub(Auth auth) {
        return auth != null
                && "Controller".equalsIgnoreCase(auth.getRole())
                && (auth.getClubId() == null || auth.getClubId().isBlank());
    }

    /**
     * Deny when auth is null, entity club does not match, or entity season does not match caller season
     * (when both sides have a season id).
     */
    public static boolean denyClubScopedEntity(Auth auth, String entityClubId, String entitySeasonId) {
        if (auth == null) {
            return true;
        }
        if (isControllerWithoutClub(auth)) {
            return false;
        }
        if (entityClubId == null || auth.getClubId() == null || auth.getClubId().isBlank()) {
            return true;
        }
        if (!entityClubId.equals(auth.getClubId())) {
            return true;
        }
        if (auth.getSeasonId() != null && !auth.getSeasonId().isBlank()
                && entitySeasonId != null && !entitySeasonId.isBlank()
                && !entitySeasonId.equals(auth.getSeasonId())) {
            return true;
        }
        return false;
    }

    /** Club id only (e.g. user roster not tied to current season the same way). */
    public static boolean denyClubOnly(Auth auth, String entityClubId) {
        if (auth == null) {
            return true;
        }
        if (isControllerWithoutClub(auth)) {
            return false;
        }
        if (entityClubId == null || auth.getClubId() == null || auth.getClubId().isBlank()) {
            return true;
        }
        return !entityClubId.equals(auth.getClubId());
    }

    /**
     * Email/phone availability and similar: master must pass {@code clubId}; club users use {@code auth.getClubId()}
     * or a matching optional {@code requestedClubId}.
     */
    public static String resolveClubForAvailabilityCheck(String requestedClubId, Auth auth) {
        if (isControllerWithoutClub(auth)) {
            if (requestedClubId == null || requestedClubId.isBlank()) {
                throw new IllegalArgumentException("clubId is required.");
            }
            return requestedClubId.trim();
        }
        String scoped = auth.getClubId();
        if (scoped == null || scoped.isBlank()) {
            throw new IllegalArgumentException(ErrorMessages.FORBIDDEN);
        }
        if (requestedClubId != null && !requestedClubId.isBlank() && !scoped.equals(requestedClubId.trim())) {
            throw new IllegalArgumentException(ErrorMessages.FORBIDDEN);
        }
        return scoped;
    }
}
