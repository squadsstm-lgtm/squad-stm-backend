package com.squad.backend.constants;

import java.util.Set;

/**
 * Stable keys for the first-visit page walkthrough.
 * Adding a page later means adding a key here and a script in the frontend. The database field stays the same.
 */
public final class WalkthroughPages {

    public static final String DASHBOARD = "dashboard";
    public static final String PLAYERS = "players";
    public static final String TEAMS = "teams";
    public static final String SESSIONS = "sessions";
    public static final String FINANCES = "finances";
    public static final String CLUB_WALLET = "club-wallet";
    public static final String USERS = "users";
    public static final String ROLES = "roles";
    public static final String ADMIN = "admin";

    public static final Set<String> ALL = Set.of(
            DASHBOARD,
            PLAYERS,
            TEAMS,
            SESSIONS,
            FINANCES,
            CLUB_WALLET,
            USERS,
            ROLES,
            ADMIN
    );

    private WalkthroughPages() {
    }

    public static boolean isKnown(String page) {
        return page != null && ALL.contains(page);
    }
}
