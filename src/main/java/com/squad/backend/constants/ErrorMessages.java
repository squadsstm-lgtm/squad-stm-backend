package com.squad.backend.constants;

public class ErrorMessages {
    
    private ErrorMessages() {
    }
    
    public static final String VALIDATION_FAILED = "Validation failed";
    public static final String AN_ERROR_OCCURRED = "An error occurred";
    public static final String INTERNAL_SERVER_ERROR = "Internal server error";
    
    public static final String USER_NOT_FOUND = "User not found";
    public static final String EMAIL_ALREADY_EXISTS = "Email is already registered.";
    public static final String PHONE_ALREADY_EXISTS = "Phone number is already registered.";
    public static final String INVALID_PASSWORD = "Invalid password";
    public static final String USER_BLOCKED = "User Blocked.";
    public static final String USER_NOT_VERIFIED = "User not verified. Verification Email is send to your email.";
    public static final String TOKEN_INVALID = "Token is invalid";
    
    public static final String SIGNUP_ERROR = "An error occurred during signup";
    public static final String LOGIN_ERROR = "An error occurred during login";
    public static final String GOOGLE_LOGIN_NOT_IMPLEMENTED = "Google login will be implemented in Phase 8 (Firebase integration)";
    
    public static final String CLUB_ALREADY_EXISTS = "Club already exists";
    public static final String CLUB_NOT_FOUND = "Club not found";
    
    public static final String PLAYER_NOT_FOUND = "Player not found";
    public static final String PLAYER_PROFILE_COMPLETED = "This player has already completed their profile. Please use Edit instead of Add.";
    public static final String PLAYER_INVITE_ALREADY_SUBMITTED = "This form has already been submitted.";
    public static final String PLAYER_INVITE_LINK_EXPIRED = "This invite link has expired. Please contact your club administrator for a new invitation.";
    public static final String PLAYER_HAS_ACTIVE_SESSION = "This Player have active session.";
    
    public static final String TEAM_NOT_FOUND = "Team not found";
    public static final String TEAM_HAS_ACTIVE_SESSION = "This Team have active session.";
    
    public static final String ROLE_NOT_FOUND = "Role not found";
    
    public static final String SESSION_NOT_FOUND = "Session not found";
    public static final String SESSION_ACTIVE_CANNOT_DELETE = "Active sessions cannot be deleted.";

    public static final String NO_ACTIVE_SEASON = "No active season found";
    
    public static final String PAYMENT_ALREADY_PROCESSED = "You have already paid for this session";
    public static final String PAYMENT_NOT_COMPLETED = "Payment not completed";
    public static final String PAYMENT_PROCESSING_ERROR = "Payment processing error";
    public static final String MISSING_REQUIRED_FIELDS = "Missing required fields";
    public static final String WALLET_NOT_FOUND = "Wallet not found";
    public static final String INSUFFICIENT_BALANCE = "Insufficient available balance";
    
    public static final String RESOURCE_NOT_FOUND = "Resource not found";
    public static final String UNAUTHORIZED = "Unauthorized";
    public static final String FORBIDDEN = "Forbidden";
    public static final String BAD_REQUEST = "Bad request";
}
