package com.squad.backend.constants;

public final class InvitePurpose {
    public static final String PLAYER_PROFILE = "PLAYER_PROFILE";
    public static final String USER_PROFILE = "USER_PROFILE";
    public static final String SESSION_CONFIRMATION = "SESSION_CONFIRMATION";
    public static final String PAYMENT_REQUEST = "PAYMENT_REQUEST";
    /** Frozen multi-session outstanding balance invoice. */
    public static final String PAYMENT_INVOICE = "PAYMENT_INVOICE";

    private InvitePurpose() {
    }
}
