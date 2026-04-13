package com.squad.backend.service;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.dto.request.auth.ForgotMpinRequest;
import com.squad.backend.dto.request.auth.ForgotPasswordRequest;
import com.squad.backend.dto.request.auth.LoginRequest;
import com.squad.backend.dto.request.auth.ResetMpinRequest;
import com.squad.backend.dto.request.auth.ResetMpinWithTokenRequest;
import com.squad.backend.dto.request.auth.ResetPasswordRequest;
import com.squad.backend.dto.request.auth.SeedControllerRequest;
import com.squad.backend.dto.request.auth.SetMpinRequest;
import com.squad.backend.dto.request.auth.SignupRequest;
import com.squad.backend.dto.request.auth.UpdateAuthRequest;
import com.squad.backend.dto.request.auth.VerifyMpinRequest;
import com.squad.backend.dto.request.auth.VerifyPasswordRequest;
import com.squad.backend.dto.response.auth.AuthResponse;
import com.squad.backend.dto.response.auth.AuthUserInfoResponse;
import com.squad.backend.dto.response.auth.TokenRefreshResponse;
import com.squad.backend.dto.response.auth.UserProfileResponse;
import com.squad.backend.dto.response.auth.ValidateAccessTokenResponse;
import com.squad.backend.dto.response.auth.VerifyTokenResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.model.Club;
import com.squad.backend.model.Permission;
import com.squad.backend.model.Role;
import com.squad.backend.model.Season;
import com.squad.backend.repository.AuthRepository;
import com.squad.backend.repository.ClubRepository;
import com.squad.backend.repository.PermissionRepository;
import com.squad.backend.repository.RoleRepository;
import com.squad.backend.repository.SeasonRepository;
import com.squad.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthRepository authRepository;
    private final ClubRepository clubRepository;
    private final SeasonRepository seasonRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final FirebaseService firebaseService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.backend-url}")
    private String backendUrl;

    @Transactional
    public Auth signup(SignupRequest request) {
        if (authRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw new IllegalArgumentException(ErrorMessages.EMAIL_ALREADY_EXISTS);
        }

        if (authRepository.existsByPhone(request.getPhone())) {
            throw new IllegalArgumentException(ErrorMessages.PHONE_ALREADY_EXISTS);
        }

        // Get current active season
        Season currentSeason = seasonRepository.findByActive(true)
                .orElseThrow(() -> new IllegalArgumentException("No active season found"));

        // Check if club already exists
        Optional<Club> existingClub = clubRepository.findByClubNameIgnoreCase(request.getClubName());
        if (existingClub.isPresent()) {
            throw new IllegalArgumentException(existingClub.get().getClubName() + " is already exist.");
        }

        // Create new club
        Club newClub = new Club();
        newClub.setClubName(request.getClubName());
        newClub.setSeasonId(currentSeason.getId());
        newClub = clubRepository.save(newClub);
        String clubId = newClub.getId();

        // Create default roles and permissions
        createDefaultRolesAndPermissions(clubId);

        // Find Admin role
        Role adminRole = roleRepository.findByNameIgnoreCaseAndClubId("Admin", clubId)
                .orElseThrow(() -> new IllegalArgumentException("Admin role not found"));

        // Create Auth user
        Auth auth = new Auth();
        mapToModel(request, auth);
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            auth.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        auth.setIsVerified(request.getIsVerified() != null ? request.getIsVerified() : false);
        auth.setIsBlocked(false);
        auth.setClubId(clubId);
        auth.setSeasonId(currentSeason.getId());
        auth.setRoleId(adminRole.getId());
        auth.setRole("Admin");
        
        Auth savedAuth = authRepository.save(auth);
        
        // Send email based on verification status
        boolean isVerified = Boolean.TRUE.equals(savedAuth.getIsVerified());
        if (isVerified) {
            // Send welcome email
            sendWelcomeEmail(savedAuth);
        } else {
            // Send verification email
            sendVerificationEmail(savedAuth);
        }
        
        return savedAuth;
    }
    
    private void sendWelcomeEmail(Auth auth) {
        Map<String, String> templateData = new HashMap<>();
        templateData.put("emailTitle", "Squad STM - Welcome!");
        templateData.put("emailHeading", "Welcome to Squad STM");
        templateData.put("emailMessage", String.format(
            "Hi <strong>%s</strong>,<br><br>Your account has been successfully created! You can now sign in to access your dashboard.",
            auth.getFirstName()
        ));
        templateData.put("buttonText", "Sign In Now");
        templateData.put("buttonLink", frontendUrl + "#/auth/login");
        templateData.put("buttonColor", "#007bff");
        templateData.put("additionalInfo", "Click the button above to sign in to your account. If you have any questions, feel free to contact our support team.");
        templateData.put("footerMessage", "Welcome aboard! We're excited to have you on the team.");
        
        String message = String.format("Successfully signUp. You can login with %s#/auth/login by google.", frontendUrl);
        emailService.sendEmail(auth.getEmail(), "Squad STM - Welcome!", message, templateData);
    }
    
    private void sendVerificationEmail(Auth auth) {
        Map<String, String> templateData = new HashMap<>();
        templateData.put("emailTitle", "Squad STM - Email Verification");
        templateData.put("emailHeading", "Email Verification Required");
        templateData.put("emailMessage", String.format(
            "Hi <strong>%s</strong>,<br><br>Welcome to Squad STM! Please verify your email address to complete your registration.",
            auth.getFirstName()
        ));
        templateData.put("buttonText", "Verify Email");
        templateData.put("buttonLink", backendUrl + "/auth/verifyEmail/" + auth.getId());
        templateData.put("buttonColor", "#28a745");
        templateData.put("additionalInfo", "Click the button above to verify your email address. If you didn't create this account, please ignore this email.");
        templateData.put("footerMessage", "If you believe this email was sent in error, please contact support.");
        
        String message = String.format("Hi %s, Please click here %s/auth/verifyEmail/%s to Verify your email.", 
            auth.getFirstName(), backendUrl, auth.getId());
        emailService.sendEmail(auth.getEmail(), "Squad STM - Email Verification", message, templateData);
    }

    private void createDefaultRolesAndPermissions(String clubId) {
        String[] roleNames = {"Admin", "Coach", "Secretary", "Treasurer"};
        
        for (String roleName : roleNames) {
            // Create Role and Permission with same ID
            Role role = new Role();
            role.setClubId(clubId);
            role.setName(roleName);
            role.setIsActive(true);
            role = roleRepository.save(role);
            
            // Create Permission with same ID as Role
            Permission permission = new Permission();
            permission.setId(role.getId());
            permission.setClubId(clubId);
            permission.setName(roleName);
            permission.setGroups(buildPermissionGroups(roleName));
            permissionRepository.save(permission);
        }
    }

    private List<Permission.PermissionGroup> buildPermissionGroups(String roleName) {
        List<Permission.PermissionGroup> groups = new ArrayList<>();
        
        // User Management permissions (group id: 1, permission groupId: 1)
        Permission.PermissionGroup userManagement = new Permission.PermissionGroup();
        userManagement.setId(1);
        userManagement.setGroup("User Management");
        List<Permission.PermissionItem> userPerms = new ArrayList<>();
        userPerms.add(createPermissionItem(1, 1, "Invite User", shouldAllow(roleName, "User Management", "Invite User")));
        userPerms.add(createPermissionItem(1, 2, "Edit User", shouldAllow(roleName, "User Management", "Edit User")));
        userPerms.add(createPermissionItem(1, 3, "Delete User", shouldAllow(roleName, "User Management", "Delete User")));
        userPerms.add(createPermissionItem(1, 4, "Get User", shouldAllow(roleName, "User Management", "Get User")));
        userManagement.setPermissions(userPerms);
        groups.add(userManagement);
        
        // Player Management permissions (group id: 2, permission groupId: 3)
        Permission.PermissionGroup playerManagement = new Permission.PermissionGroup();
        playerManagement.setId(2);
        playerManagement.setGroup("Player Management");
        List<Permission.PermissionItem> playerPerms = new ArrayList<>();
        playerPerms.add(createPermissionItem(3, 1, "Add Player", shouldAllow(roleName, "Player Management", "Add Player")));
        playerPerms.add(createPermissionItem(3, 2, "Edit Player", shouldAllow(roleName, "Player Management", "Edit Player")));
        playerPerms.add(createPermissionItem(3, 3, "Delete Player", shouldAllow(roleName, "Player Management", "Delete Player")));
        playerPerms.add(createPermissionItem(3, 4, "Get Player", shouldAllow(roleName, "Player Management", "Get Player")));
        playerPerms.add(createPermissionItem(3, 5, "Invite Player", shouldAllow(roleName, "Player Management", "Invite Player")));
        playerManagement.setPermissions(playerPerms);
        groups.add(playerManagement);
        
        // Team Management permissions (group id: 3, permission groupId: 2)
        Permission.PermissionGroup teamManagement = new Permission.PermissionGroup();
        teamManagement.setId(3);
        teamManagement.setGroup("Team Management");
        List<Permission.PermissionItem> teamPerms = new ArrayList<>();
        teamPerms.add(createPermissionItem(2, 1, "Add Team", shouldAllow(roleName, "Team Management", "Add Team")));
        teamPerms.add(createPermissionItem(2, 2, "Edit Team", shouldAllow(roleName, "Team Management", "Edit Team")));
        teamPerms.add(createPermissionItem(2, 3, "Delete Team", shouldAllow(roleName, "Team Management", "Delete Team")));
        teamPerms.add(createPermissionItem(2, 4, "Get Team", shouldAllow(roleName, "Team Management", "Get Team")));
        teamManagement.setPermissions(teamPerms);
        groups.add(teamManagement);
        
        // Session Management permissions (group id: 4, permission groupId: 4)
        Permission.PermissionGroup sessionManagement = new Permission.PermissionGroup();
        sessionManagement.setId(4);
        sessionManagement.setGroup("Session Management");
        List<Permission.PermissionItem> sessionPerms = new ArrayList<>();
        sessionPerms.add(createPermissionItem(4, 1, "Add Session", shouldAllow(roleName, "Session Management", "Add Session")));
        sessionPerms.add(createPermissionItem(4, 2, "Edit Session", shouldAllow(roleName, "Session Management", "Edit Session")));
        sessionPerms.add(createPermissionItem(4, 3, "Delete Session", shouldAllow(roleName, "Session Management", "Delete Session")));
        sessionPerms.add(createPermissionItem(4, 4, "Get Session", shouldAllow(roleName, "Session Management", "Get Session")));
        sessionPerms.add(createPermissionItem(4, 5, "Mark Player's Attendance", shouldAllow(roleName, "Session Management", "Mark Player's Attendance")));
        sessionManagement.setPermissions(sessionPerms);
        groups.add(sessionManagement);
        
        // Finance Management permissions (group id: 5, permission groupId: 5)
        Permission.PermissionGroup financeManagement = new Permission.PermissionGroup();
        financeManagement.setId(5);
        financeManagement.setGroup("Finance Management");
        List<Permission.PermissionItem> financePerms = new ArrayList<>();
        financePerms.add(createPermissionItem(5, 2, "Edit Finance", shouldAllow(roleName, "Finance Management", "Edit Finance")));
        financePerms.add(createPermissionItem(5, 4, "Get Finance", shouldAllow(roleName, "Finance Management", "Get Finance")));
        financeManagement.setPermissions(financePerms);
        groups.add(financeManagement);

        // Club Wallet permissions (group id: 6, permission groupId: 6)
        Permission.PermissionGroup clubWallet = new Permission.PermissionGroup();
        clubWallet.setId(6);
        clubWallet.setGroup("Club Wallet");
        List<Permission.PermissionItem> clubWalletPerms = new ArrayList<>();
        clubWalletPerms.add(createPermissionItem(6, 1, "Access Club Wallet", shouldAllow(roleName, "Club Wallet", "Access Club Wallet")));
        clubWallet.setPermissions(clubWalletPerms);
        groups.add(clubWallet);

        return groups;
    }

    private Permission.PermissionItem createPermissionItem(Integer groupId, Integer roleId, String description, Boolean status) {
        Permission.PermissionItem item = new Permission.PermissionItem();
        item.setGroupId(groupId);
        item.setRoleId(roleId);
        item.setDescription(description);
        item.setStatus(status);
        return item;
    }

    private Boolean shouldAllow(String roleName, String group, String permission) {
        // Admin: all true
        if ("Admin".equals(roleName)) {
            return true;
        }
        
        // Secretary
        if ("Secretary".equals(roleName)) {
            if ("User Management".equals(group) && "Get User".equals(permission)) {
                return true;
            }
            if ("Player Management".equals(group) && 
                ("Add Player".equals(permission) || "Get Player".equals(permission) || "Invite Player".equals(permission))) {
                return true;
            }
            if ("Team Management".equals(group) && !"Delete Team".equals(permission)) {
                return true;
            }
            if ("Session Management".equals(group)) {
                return true;
            }
            return false;
        }
        
        // Coach
        if ("Coach".equals(roleName)) {
            if ("User Management".equals(group) && 
                ("Invite User".equals(permission) || "Edit User".equals(permission) || "Get User".equals(permission))) {
                return true;
            }
            if ("Player Management".equals(group)) {
                return true;
            }
            if ("Team Management".equals(group) && !"Delete Team".equals(permission)) {
                return true;
            }
            if ("Session Management".equals(group)) {
                return true;
            }
            if ("Finance Management".equals(group) && "Get Finance".equals(permission)) {
                return true;
            }
            return false;
        }
        
        // Treasurer
        if ("Treasurer".equals(roleName)) {
            if ("Finance Management".equals(group)) {
                return true;
            }
            if ("Player Management".equals(group) && "Get Player".equals(permission)) {
                return true;
            }
            if ("Team Management".equals(group) && "Get Team".equals(permission)) {
                return true;
            }
            if ("Session Management".equals(group) && "Get Session".equals(permission)) {
                return true;
            }
            if ("User Management".equals(group) && "Get User".equals(permission)) {
                return true;
            }
            if ("Club Wallet".equals(group)) {
                return true;
            }
            return false;
        }
        
        return false;
    }

    /** Blocked accounts must not authenticate, refresh tokens, or change credentials. */
    private void requireAuthNotBlocked(Auth auth) {
        if (auth != null && Boolean.TRUE.equals(auth.getIsBlocked())) {
            throw new IllegalArgumentException(ErrorMessages.USER_BLOCKED);
        }
    }

    public AuthResponse login(LoginRequest request) {
        Auth auth = authRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.USER_NOT_FOUND));

        if (Boolean.TRUE.equals(auth.getIsBlocked())) {
            throw new IllegalArgumentException(ErrorMessages.USER_BLOCKED);
        }

        if (!Boolean.TRUE.equals(auth.getIsVerified())) {
            throw new IllegalArgumentException(ErrorMessages.USER_NOT_VERIFIED);
        }

        if (auth.getPassword() != null && !auth.getPassword().isEmpty()) {
            if (!passwordEncoder.matches(request.getPassword(), auth.getPassword())) {
                throw new IllegalArgumentException(ErrorMessages.INVALID_PASSWORD);
            }
        }

        if ("Controller".equalsIgnoreCase(auth.getRole())) {
            Optional<Season> currentSeason = seasonRepository.findByActive(true);
            if (currentSeason.isPresent() && !currentSeason.get().getId().equals(auth.getSeasonId())) {
                auth.setSeasonId(currentSeason.get().getId());
                authRepository.save(auth);
            }
        }

        // Access token: 1 hour (3600000 ms), Refresh token: 1 day (86400000 ms)
        String accessToken = jwtTokenProvider.generateToken(auth.getId(), 3600000L);
        String refreshToken = jwtTokenProvider.generateToken(auth.getId(), 86400000L);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .clubId(auth.getClubId() != null ? auth.getClubId() : "")
                .userId(auth.getId())
                .email(auth.getEmail())
                .firstName(auth.getFirstName())
                .lastName(auth.getLastName())
                .role(auth.getRole() != null ? auth.getRole() : "")
                .build();
    }

    public TokenRefreshResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }
        String userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        Auth auth = authRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        requireAuthNotBlocked(auth);
        String newAccessToken = jwtTokenProvider.generateToken(userId, 3600000L);
        String newRefreshToken = jwtTokenProvider.generateToken(userId, 86400000L);
        return TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        Auth user = authRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        requireAuthNotBlocked(user);

        Map<String, String> templateData = new HashMap<>();
        templateData.put("emailTitle", "Squad STM - Password Reset");
        templateData.put("emailHeading", "Password Reset Request");
        templateData.put("emailMessage", "<strong>" + (user.getFirstName() != null ? user.getFirstName() : "User") + "</strong>,<br><br>We received a request to reset your password. Click the button below to set a new password.");
        templateData.put("buttonText", "Reset Password");
        templateData.put("buttonLink", frontendUrl + "#/auth/update-password/" + user.getId());
        templateData.put("buttonColor", "#dc3545");
        templateData.put("additionalInfo", "This link will expire soon for security reasons. If you didn't request this password reset, please ignore this email.");
        templateData.put("footerMessage", "If you believe this email was sent in error, please contact support.");

        boolean mailSent = emailService.sendEmail(
                user.getEmail(),
                "Squad STM - Password Reset",
                "Click here, " + frontendUrl + "#/auth/update-password/" + user.getId() + " to set new password.",
                templateData
        );

        if (!mailSent) {
            throw new RuntimeException("Mail sending error.");
        }
    }

    public void resetPassword(ResetPasswordRequest request) {
        Auth user = authRepository.findById(request.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        requireAuthNotBlocked(user);

        if (request.getOldPassword() != null && !request.getOldPassword().isEmpty()) {
            if (user.getPassword() == null || !passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
                throw new IllegalArgumentException("Invalid old password");
            }
        }

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        authRepository.save(user);
    }

    /** Returns true if the auth user has MPIN set (for Club Wallet). */
    public boolean hasMpin(String authId) {
        Auth auth = authRepository.findById(authId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        requireAuthNotBlocked(auth);
        String m = auth.getMpin();
        return m != null && !m.trim().isEmpty();
    }

    /** Full profile for current user (no password, mpin value, or tokens). Use for settings / MPIN flows. */
    public UserProfileResponse getProfile(String authId) {
        Auth auth = authRepository.findById(authId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        requireAuthNotBlocked(auth);
        boolean hasMpin = auth.getMpin() != null && !auth.getMpin().trim().isEmpty();
        return UserProfileResponse.builder()
                .id(auth.getId())
                .clubId(auth.getClubId())
                .seasonId(auth.getSeasonId())
                .firstName(auth.getFirstName())
                .lastName(auth.getLastName())
                .userName(auth.getUserName())
                .clubName(auth.getClubName())
                .email(auth.getEmail())
                .phone(auth.getPhone())
                .isVerified(auth.getIsVerified())
                .roleId(auth.getRoleId())
                .role(auth.getRole())
                .userId(auth.getUserId())
                .isBlocked(auth.getIsBlocked())
                .hasMpin(hasMpin)
                .version(auth.getVersion())
                .build();
    }

    /** Set MPIN (only when current MPIN is empty). Validates mpin and confirmMpin match. */
    public void setMpin(String authId, SetMpinRequest request) {
        Auth auth = authRepository.findById(authId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        requireAuthNotBlocked(auth);
        if (auth.getMpin() != null && !auth.getMpin().trim().isEmpty()) {
            throw new IllegalArgumentException("MPIN already set. Use change or verify.");
        }
        if (!request.getMpin().equals(request.getConfirmMpin())) {
            throw new IllegalArgumentException("MPIN and confirm MPIN do not match");
        }
        auth.setMpin(passwordEncoder.encode(request.getMpin()));
        authRepository.save(auth);
    }

    /** Verify MPIN for Club Wallet access. */
    public void verifyMpin(String authId, VerifyMpinRequest request) {
        Auth auth = authRepository.findById(authId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        requireAuthNotBlocked(auth);
        if (auth.getMpin() == null || auth.getMpin().trim().isEmpty()) {
            throw new IllegalArgumentException("MPIN not set");
        }
        if (!passwordEncoder.matches(request.getMpin(), auth.getMpin())) {
            throw new IllegalArgumentException("Invalid MPIN");
        }
    }

    /** Reset MPIN (change to new MPIN). Requires current MPIN to verify. */
    public void resetMpin(String authId, ResetMpinRequest request) {
        Auth auth = authRepository.findById(authId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        requireAuthNotBlocked(auth);
        if (auth.getMpin() == null || auth.getMpin().trim().isEmpty()) {
            throw new IllegalArgumentException("MPIN not set. Use create MPIN first.");
        }
        if (!passwordEncoder.matches(request.getOldMpin(), auth.getMpin())) {
            throw new IllegalArgumentException("Current MPIN is incorrect");
        }
        if (!request.getMpin().equals(request.getConfirmMpin())) {
            throw new IllegalArgumentException("New MPIN and confirm MPIN do not match");
        }
        auth.setMpin(passwordEncoder.encode(request.getMpin()));
        authRepository.save(auth);
    }

    /** Request forgot MPIN: verify password, generate one-time token, send email. */
    public void requestForgotMpin(String authId, ForgotMpinRequest request) {
        Auth auth = authRepository.findById(authId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        requireAuthNotBlocked(auth);
        if (auth.getPassword() == null || auth.getPassword().isEmpty()) {
            throw new IllegalArgumentException("Password not set for this account");
        }
        if (!passwordEncoder.matches(request.getPassword(), auth.getPassword())) {
            throw new IllegalArgumentException(ErrorMessages.INVALID_PASSWORD);
        }
        String token = UUID.randomUUID().toString();
        long expiry = System.currentTimeMillis() + (60 * 60 * 1000); // 1 hour
        auth.setForgotMpinToken(token);
        auth.setForgotMpinTokenExpiry(expiry);
        authRepository.save(auth);

        Map<String, String> templateData = new HashMap<>();
        templateData.put("emailTitle", "Squad STM - Reset MPIN");
        templateData.put("emailHeading", "Reset Club Wallet MPIN");
        templateData.put("emailMessage", "<strong>" + (auth.getFirstName() != null ? auth.getFirstName() : "User") + "</strong>,<br><br>We received a request to reset your Club Wallet MPIN. Click the button below to set a new MPIN.");
        templateData.put("buttonText", "Set New MPIN");
        templateData.put("buttonLink", frontendUrl + "#/auth/reset-mpin?token=" + token);
        templateData.put("buttonColor", "#007bff");
        templateData.put("additionalInfo", "This link will expire in 1 hour. If you didn't request this, please ignore this email.");
        templateData.put("footerMessage", "If you believe this email was sent in error, please contact support.");

        boolean mailSent = emailService.sendEmail(
                auth.getEmail(),
                "Squad STM - Reset MPIN",
                "Click here to set new MPIN: " + frontendUrl + "#/auth/reset-mpin?token=" + token,
                templateData
        );
        if (!mailSent) {
            auth.setForgotMpinToken(null);
            auth.setForgotMpinTokenExpiry(null);
            authRepository.save(auth);
            throw new RuntimeException("Mail sending error.");
        }
    }

    /** Validate forgot MPIN token (for the reset-mpin page). */
    public void validateForgotMpinToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Invalid or expired link");
        }
        Auth auth = authRepository.findByForgotMpinToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired link"));
        requireAuthNotBlocked(auth);
        if (auth.getForgotMpinTokenExpiry() == null || auth.getForgotMpinTokenExpiry() < System.currentTimeMillis()) {
            auth.setForgotMpinToken(null);
            auth.setForgotMpinTokenExpiry(null);
            authRepository.save(auth);
            throw new IllegalArgumentException("Invalid or expired link");
        }
    }

    /** Set new MPIN using one-time token (consumes token). */
    public void resetMpinWithToken(ResetMpinWithTokenRequest request) {
        Auth auth = authRepository.findByForgotMpinToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired link"));
        requireAuthNotBlocked(auth);
        if (auth.getForgotMpinTokenExpiry() == null || auth.getForgotMpinTokenExpiry() < System.currentTimeMillis()) {
            auth.setForgotMpinToken(null);
            auth.setForgotMpinTokenExpiry(null);
            authRepository.save(auth);
            throw new IllegalArgumentException("Invalid or expired link");
        }
        if (!request.getMpin().equals(request.getConfirmMpin())) {
            throw new IllegalArgumentException("MPIN and confirm MPIN do not match");
        }
        auth.setMpin(passwordEncoder.encode(request.getMpin()));
        auth.setForgotMpinToken(null);
        auth.setForgotMpinTokenExpiry(null);
        authRepository.save(auth);
    }

    /** Verify login password (e.g. to access Reset MPIN / Forgot MPIN in settings). */
    public void verifyPassword(String authId, VerifyPasswordRequest request) {
        Auth auth = authRepository.findById(authId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        requireAuthNotBlocked(auth);
        if (auth.getPassword() == null || auth.getPassword().isEmpty()) {
            throw new IllegalArgumentException("Password not set for this account");
        }
        if (!passwordEncoder.matches(request.getPassword(), auth.getPassword())) {
            throw new IllegalArgumentException(ErrorMessages.INVALID_PASSWORD);
        }
    }

    public ValidateAccessTokenResponse validateAccessToken(String token) {
        if (!jwtTokenProvider.validateToken(token)) {
            throw new IllegalArgumentException("Token is invalid");
        }
        String userId = jwtTokenProvider.getUserIdFromToken(token);
        Auth user = authRepository.findById(userId).orElse(null);
        if (user != null) {
            requireAuthNotBlocked(user);
            Season season = null;
            if (user.getSeasonId() != null && !user.getSeasonId().isEmpty()) {
                season = seasonRepository.findById(user.getSeasonId()).orElse(null);
            }
            AuthUserInfoResponse userData = authToUserInfo(user);
            userData.setHasMpin(hasMpin(user.getId()));
            if (season != null) userData.setSeasonDetails(season);
            return ValidateAccessTokenResponse.builder().user(userData).build();
        }
        return ValidateAccessTokenResponse.builder().user(null).build();
    }

    public boolean checkUsernameAvailability(String username) {
        Optional<Auth> existing = authRepository.findByUserNameIgnoreCase(username);
        return existing.isEmpty();
    }

    public Map<String, Object> checkUsername(String username) {
        Optional<Auth> existing = authRepository.findByUserNameIgnoreCase(username);
        Map<String, Object> response = new HashMap<>();
        if (existing.isPresent()) {
            response.put("available", false);
            response.put("storedUsername", existing.get().getUserName());
        } else {
            response.put("available", true);
        }
        return response;
    }

    public boolean checkClubNameAvailability(String clubName) {
        Optional<Club> existing = clubRepository.findByClubNameIgnoreCase(clubName);
        return existing.isEmpty();
    }

    public Map<String, Object> checkClubName(String clubName) {
        Optional<Club> existing = clubRepository.findByClubNameIgnoreCase(clubName);
        Map<String, Object> response = new HashMap<>();
        if (existing.isPresent()) {
            response.put("available", false);
            response.put("storedClubName", existing.get().getClubName());
        } else {
            response.put("available", true);
        }
        return response;
    }

    /**
     * One-time seed: create the first Controller user (Master Panel).
     * Only succeeds when no Auth with role "Controller" exists.
     * Controller role is created on startup if missing.
     */
    @Transactional
    public Auth seedControllerUser(SeedControllerRequest request) {
        if (authRepository.existsByRole("Controller")) {
            throw new IllegalStateException("A Controller user already exists.");
        }
        if (authRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw new IllegalArgumentException("Email already registered.");
        }
        Role controllerRole = roleRepository.findByNameIgnoreCaseAndClubIdIsNull("Controller")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setClubId(null);
                    r.setName("Controller");
                    r.setIsActive(true);
                    return roleRepository.save(r);
                });
        Auth auth = new Auth();
        auth.setEmail(request.getEmail().toLowerCase().trim());
        auth.setFirstName(request.getFirstName().trim());
        auth.setLastName(request.getLastName().trim());
        auth.setPassword(passwordEncoder.encode(request.getPassword()));
        auth.setClubId(null);
        auth.setSeasonId(null);
        auth.setRoleId(controllerRole.getId());
        auth.setRole("Controller");
        auth.setUserId(null);
        auth.setUserName(null);
        auth.setPhone(null);
        auth.setClubName(null);
        auth.setIsVerified(true);
        auth.setIsBlocked(false);
        auth.setMpin(null);
        auth.setForgotMpinToken(null);
        auth.setForgotMpinTokenExpiry(null);
        seasonRepository.findByActive(true).ifPresent(season -> auth.setSeasonId(season.getId()));
        return authRepository.save(auth);
    }

    public boolean checkEmailAvailability(String email) {
        Optional<Auth> existing = authRepository.findByEmail(email);
        return existing.isEmpty();
    }

    public boolean checkPhoneAvailability(String phone) {
        String formattedPhone = phone.trim().startsWith("+") ? phone.trim() : "+" + phone.trim();
        Optional<Auth> existing = authRepository.findByPhone(formattedPhone);
        return existing.isEmpty();
    }

    public void verifyEmail(String id) {
        Auth auth = authRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        requireAuthNotBlocked(auth);
        auth.setIsVerified(true);
        authRepository.save(auth);
    }

    public Auth updateAuth(String id, UpdateAuthRequest request) {
        Auth auth = authRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        requireAuthNotBlocked(auth);

        if (request.getSeasonId() != null && !request.getSeasonId().isEmpty()) {
            auth.setSeasonId(request.getSeasonId());
        } else {
            if (request.getFirstName() != null) {
                auth.setFirstName(request.getFirstName());
            }
            if (request.getLastName() != null) {
                auth.setLastName(request.getLastName());
            }
            if (request.getUserName() != null) {
                auth.setUserName(request.getUserName());
            }
        }

        return authRepository.save(auth);
    }

    public AuthResponse googleLogin(String idToken) {
        try {
            com.google.firebase.auth.FirebaseToken decodedToken = firebaseService.verifyIdToken(idToken);
            String email = decodedToken.getEmail();
            String name = decodedToken.getName();
            
            if (email == null || email.isEmpty()) {
                throw new IllegalArgumentException("Email not found in Firebase token");
            }
            
            Optional<Auth> existingAuth = authRepository.findByEmail(email.toLowerCase());
            Auth auth;
            
            if (existingAuth.isPresent()) {
                auth = existingAuth.get();
                if (Boolean.TRUE.equals(auth.getIsBlocked())) {
                    throw new IllegalArgumentException(ErrorMessages.USER_BLOCKED);
                }
            } else {
                auth = new Auth();
                auth.setEmail(email.toLowerCase());
                if (name != null && !name.isEmpty()) {
                    String[] nameParts = name.split(" ", 2);
                    auth.setFirstName(nameParts[0]);
                    auth.setLastName(nameParts.length > 1 ? nameParts[1] : "");
                }
                auth.setIsVerified(true);
                auth.setIsBlocked(false);
                auth.setPassword("");
                auth = authRepository.save(auth);
            }

            if ("Controller".equalsIgnoreCase(auth.getRole())) {
                Optional<Season> currentSeason = seasonRepository.findByActive(true);
                if (currentSeason.isPresent() && !currentSeason.get().getId().equals(auth.getSeasonId())) {
                    auth.setSeasonId(currentSeason.get().getId());
                    authRepository.save(auth);
                }
            }

            // Access token: 1 hour (3600000 ms), Refresh token: 1 day (86400000 ms)
            String accessToken = jwtTokenProvider.generateToken(auth.getId(), 3600000L);
            String refreshToken = jwtTokenProvider.generateToken(auth.getId(), 86400000L);
            
            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .clubId(auth.getClubId() != null ? auth.getClubId() : "")
                    .userId(auth.getId())
                    .email(auth.getEmail())
                    .firstName(auth.getFirstName())
                    .lastName(auth.getLastName())
                    .role(auth.getRole() != null ? auth.getRole() : "")
                    .build();
        } catch (com.google.firebase.auth.FirebaseAuthException e) {
            log.error("Firebase token verification failed: ", e);
            throw new IllegalArgumentException("Invalid Firebase token");
        }
    }

    public VerifyTokenResponse verifyToken(String idToken) {
        try {
            com.google.firebase.auth.FirebaseToken decodedToken = firebaseService.verifyIdToken(idToken);
            String email = decodedToken.getEmail();
            Optional<Auth> existingAuth = email != null ? authRepository.findByEmail(email.toLowerCase()) : Optional.empty();
            if (existingAuth.isPresent()) {
                Auth auth = existingAuth.get();
                requireAuthNotBlocked(auth);
                Season season = null;
                if (auth.getSeasonId() != null && !auth.getSeasonId().isEmpty()) {
                    season = seasonRepository.findById(auth.getSeasonId()).orElse(null);
                }
                AuthUserInfoResponse userInfo = authToUserInfo(auth);
                if (season != null) userInfo.setSeasonDetails(season);
                return VerifyTokenResponse.builder()
                        .userInfo(userInfo)
                        .accessToken(jwtTokenProvider.generateToken(auth.getId(), 3600000L))
                        .refreshToken(jwtTokenProvider.generateToken(auth.getId(), 86400000L))
                        .existingUser(true)
                        .build();
            }
            AuthUserInfoResponse userInfo = AuthUserInfoResponse.builder()
                    .uid(decodedToken.getUid())
                    .email(decodedToken.getEmail())
                    .name(decodedToken.getName())
                    .build();
            return VerifyTokenResponse.builder()
                    .userInfo(userInfo)
                    .accessToken(jwtTokenProvider.generateToken(decodedToken.getUid(), 3600000L))
                    .existingUser(false)
                    .build();
        } catch (com.google.firebase.auth.FirebaseAuthException e) {
            log.error("Firebase token verification failed: ", e);
            throw new IllegalArgumentException("Invalid Firebase token");
        }
    }

    private AuthUserInfoResponse authToUserInfo(Auth auth) {
        return AuthUserInfoResponse.builder()
                .mongoId(auth.getId())
                .id(auth.getId())
                .email(auth.getEmail())
                .firstName(auth.getFirstName())
                .lastName(auth.getLastName())
                .clubId(auth.getClubId())
                .seasonId(auth.getSeasonId())
                .role(auth.getRole())
                .roleId(auth.getRoleId())
                .isVerified(auth.getIsVerified())
                .isBlocked(auth.getIsBlocked())
                .userName(auth.getUserName())
                .phone(auth.getPhone())
                .build();
    }

    private void mapToModel(SignupRequest request, Auth auth) {
        auth.setEmail(request.getEmail().toLowerCase());
        auth.setFirstName(request.getFirstName());
        auth.setLastName(request.getLastName());
        auth.setPhone(request.getPhone());
    }
}
