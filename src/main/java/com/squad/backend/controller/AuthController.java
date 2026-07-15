package com.squad.backend.controller;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.dto.request.auth.ForgotMpinRequest;
import com.squad.backend.dto.request.auth.ForgotPasswordRequest;
import com.squad.backend.dto.request.auth.GoogleLoginRequest;
import com.squad.backend.dto.request.auth.LoginRequest;
import com.squad.backend.dto.request.auth.ResendVerificationRequest;
import com.squad.backend.dto.request.auth.ResetMpinRequest;
import com.squad.backend.dto.request.auth.ResetMpinWithTokenRequest;
import com.squad.backend.dto.request.auth.ResetPasswordRequest;
import com.squad.backend.dto.request.auth.SeedControllerRequest;
import com.squad.backend.dto.request.auth.SetMpinRequest;
import com.squad.backend.dto.request.auth.SignupRequest;
import com.squad.backend.dto.request.auth.UpdateAuthRequest;
import com.squad.backend.dto.request.auth.VerifyMpinRequest;
import com.squad.backend.dto.request.auth.VerifyPasswordRequest;
import com.squad.backend.dto.response.ApiResponse;
import com.squad.backend.dto.response.auth.AuthResponse;
import com.squad.backend.dto.response.auth.AuthUserInfoResponse;
import com.squad.backend.dto.response.auth.AvailabilityResponse;
import com.squad.backend.dto.response.auth.MpinStatusResponse;
import com.squad.backend.dto.response.auth.SeedControllerResponse;
import com.squad.backend.dto.response.auth.TokenRefreshResponse;
import com.squad.backend.dto.response.auth.UpdateAuthResponse;
import com.squad.backend.dto.response.auth.UserProfileResponse;
import com.squad.backend.dto.response.auth.ValidateAccessTokenResponse;
import com.squad.backend.dto.response.auth.VerifyTokenResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.security.JwtTokenProvider;
import com.squad.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.cookie-secure:false}")
    private boolean cookieSecure;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletRequest servletRequest) {
        try {
            Auth auth = authService.signup(request);
            
            // If verified, return tokens like Node.js
            if (Boolean.TRUE.equals(auth.getIsVerified())) {
                // Access token: 1 hour (3600000 ms), Refresh token: 1 day (86400000 ms)
                String accessToken = jwtTokenProvider.generateToken(auth.getId(), 3600000L);
                String refreshToken = jwtTokenProvider.generateToken(auth.getId(), 86400000L);
                
                AuthResponse data = AuthResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .clubId(auth.getClubId() != null ? auth.getClubId() : "")
                        .userId(auth.getId())
                        .email(auth.getEmail())
                        .firstName(auth.getFirstName())
                        .lastName(auth.getLastName())
                        .role(auth.getRole() != null ? auth.getRole() : "")
                        .build();
                return ResponseEntity.status(HttpStatus.CREATED)
                        .header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(refreshToken, servletRequest).toString())
                        .body(ApiResponse.success(data, "Sign Up successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(null, "Verification required"));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Signup error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.SIGNUP_ERROR));
        }
    }

    @PostMapping("/signin")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(response.getRefreshToken(), servletRequest).toString())
                    .body(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            HttpStatus status = HttpStatus.BAD_REQUEST;
            if (ErrorMessages.USER_NOT_FOUND.equals(e.getMessage())) {
                status = HttpStatus.NOT_FOUND;
            } else if (ErrorMessages.USER_BLOCKED.equals(e.getMessage()) || ErrorMessages.USER_NOT_VERIFIED.equals(e.getMessage())) {
                status = HttpStatus.FORBIDDEN;
            } else if (ErrorMessages.INVALID_PASSWORD.equals(e.getMessage())) {
                status = HttpStatus.UNAUTHORIZED;
            }
            return ResponseEntity.status(status)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Login error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.LOGIN_ERROR));
        }
    }

    @PostMapping("/google-login")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request,
            HttpServletRequest servletRequest) {
        try {
            AuthResponse response = authService.googleLogin(request.getToken());
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(response.getRefreshToken(), servletRequest).toString())
                    .body(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            HttpStatus status = HttpStatus.BAD_REQUEST;
            if (ErrorMessages.USER_BLOCKED.equals(e.getMessage())) {
                status = HttpStatus.FORBIDDEN;
            }
            return ResponseEntity.status(status)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Google login error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    /**
     * One-time seed: create the first Controller user (Master Panel).
     * Only works when no Controller exists. Call once with email and password.
     * Returns slim data only (no password, mpin, or tokens).
     */
    @PostMapping("/seed-controller")
    public ResponseEntity<ApiResponse<SeedControllerResponse>> seedController(@Valid @RequestBody SeedControllerRequest request) {
        try {
            Auth auth = authService.seedControllerUser(request);
            SeedControllerResponse data = SeedControllerResponse.builder()
                    .id(auth.getId())
                    .email(auth.getEmail())
                    .firstName(auth.getFirstName())
                    .lastName(auth.getLastName())
                    .role(auth.getRole())
                    .clubId(auth.getClubId())
                    .build();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(data, "Controller user created. You can now sign in."));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Seed controller error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    /**
     * Full profile for the current user (settings, MPIN status, etc.).
     * Requires auth. Use when you need more than sign-in data.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(@AuthenticationPrincipal Auth auth) {
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Not authenticated"));
        }
        try {
            UserProfileResponse profile = authService.getProfile(auth.getId());
            return ResponseEntity.ok(ApiResponse.success(profile));
        } catch (IllegalArgumentException e) {
            HttpStatus status = ErrorMessages.USER_BLOCKED.equals(e.getMessage())
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.NOT_FOUND;
            return ResponseEntity.status(status).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get profile error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/verifyToken")
    public ResponseEntity<ApiResponse<VerifyTokenResponse>> verifyToken(
            @RequestBody Map<String, String> request,
            HttpServletRequest servletRequest) {
        try {
            String token = request.get("token");
            if (token == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("token is required"));
            }
            VerifyTokenResponse response = authService.verifyToken(token);
            ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
            if (response.getRefreshToken() != null && !response.getRefreshToken().isBlank()) {
                builder.header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(response.getRefreshToken(), servletRequest).toString());
            }
            return builder.body(ApiResponse.success(response, "Token is valid"));
        } catch (IllegalArgumentException e) {
            HttpStatus status = ErrorMessages.USER_BLOCKED.equals(e.getMessage())
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.UNAUTHORIZED;
            return ResponseEntity.status(status)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Verify token error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/oauth/token")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refreshToken(
            @RequestBody(required = false) Map<String, String> request,
            @CookieValue(value = "refresh_token", required = false) String refreshTokenCookie,
            HttpServletRequest servletRequest) {
        try {
            String refreshToken = refreshTokenCookie;
            if ((refreshToken == null || refreshToken.isBlank()) && request != null) {
                refreshToken = request.get("refresh_token");
            }
            if (refreshToken == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("refresh_token is required"));
            }
            TokenRefreshResponse tokens = authService.refreshToken(refreshToken);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(tokens.getRefreshToken(), servletRequest).toString())
                    .body(ApiResponse.success(tokens));
        } catch (IllegalArgumentException e) {
            HttpStatus status = ErrorMessages.USER_BLOCKED.equals(e.getMessage())
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.UNAUTHORIZED;
            return ResponseEntity.status(status)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Refresh token error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest servletRequest) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshTokenCookie(servletRequest).toString())
                .body(ApiResponse.success((Void) null, "Logged out successfully"));
    }

    private ResponseCookie buildRefreshTokenCookie(String refreshToken, HttpServletRequest servletRequest) {
        boolean secure = cookieSecure || servletRequest.isSecure();
        return ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(secure)
                .path("/api/auth")
                .sameSite("Lax")
                .build();
    }

    private ResponseCookie clearRefreshTokenCookie(HttpServletRequest servletRequest) {
        boolean secure = cookieSecure || servletRequest.isSecure();
        return ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(secure)
                .path("/api/auth")
                .sameSite("Lax")
                .maxAge(0)
                .build();
    }

    @PostMapping("/forgotPassword")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        try {
            authService.forgotPassword(request);
            return ResponseEntity.ok(ApiResponse.success((Void) null));
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.USER_BLOCKED.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (RuntimeException e) {
            if ("Mail sending error.".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        } catch (Exception e) {
            log.error("Forgot password error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        try {
            authService.resendVerificationEmail(request.getEmail());
            return ResponseEntity.ok(ApiResponse.success((Void) null, "Verification email sent. Please check your inbox."));
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.USER_BLOCKED.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(e.getMessage()));
            }
            if (ErrorMessages.USER_NOT_FOUND.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Resend verification email failed: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Resend verification error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        try {
            authService.resetPassword(request);
            return ResponseEntity.ok(ApiResponse.success((Void) null, "Password Updated Successfully"));
        } catch (IllegalArgumentException e) {
            HttpStatus status = ErrorMessages.USER_BLOCKED.equals(e.getMessage())
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Reset password error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/validate-accessToken")
    public ResponseEntity<ApiResponse<ValidateAccessTokenResponse>> validateAccessToken(
            @RequestBody(required = false) Map<String, String> request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            String token = null;
            if (request != null && request.get("token") != null) {
                token = request.get("token").trim();
                if (token.startsWith("Bearer ")) {
                    token = token.substring(7).trim();
                }
            }
            if ((token == null || token.isEmpty()) && authorization != null && authorization.startsWith("Bearer ")) {
                token = authorization.substring(7).trim();
            }
            if (token == null || token.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("token is required"));
            }
            ValidateAccessTokenResponse response = authService.validateAccessToken(token);
            return ResponseEntity.ok(ApiResponse.success(response, "Token is valid"));
        } catch (IllegalArgumentException e) {
            HttpStatus status = ErrorMessages.USER_BLOCKED.equals(e.getMessage())
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.UNAUTHORIZED;
            return ResponseEntity.status(status)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Validate access token error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/check-username")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> checkUsername(
            @RequestParam String username) {
        try {
            Map<String, Object> response = authService.checkUsername(username.toLowerCase());
            AvailabilityResponse body = AvailabilityResponse.builder()
                    .available((Boolean) response.get("available"))
                    .storedUsername((String) response.get("storedUsername"))
                    .build();
            return ResponseEntity.ok(ApiResponse.success(body));
        } catch (Exception e) {
            log.error("Check username error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/check-clubName")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> checkClubName(
            @RequestParam String clubName) {
        try {
            Map<String, Object> response = authService.checkClubName(clubName.toLowerCase());
            AvailabilityResponse body = AvailabilityResponse.builder()
                    .available((Boolean) response.get("available"))
                    .storedClubName((String) response.get("storedClubName"))
                    .build();
            return ResponseEntity.ok(ApiResponse.success(body));
        } catch (Exception e) {
            log.error("Check club name error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> checkEmail(
            @RequestParam String email) {
        try {
            boolean available = authService.checkEmailAvailability(email);
            return ResponseEntity.ok(ApiResponse.success(AvailabilityResponse.builder().available(available).build()));
        } catch (Exception e) {
            log.error("Check email error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/check-phone")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> checkPhone(
            @RequestParam String phone) {
        try {
            boolean available = authService.checkPhoneAvailability(phone);
            return ResponseEntity.ok(ApiResponse.success(AvailabilityResponse.builder().available(available).build()));
        } catch (Exception e) {
            log.error("Check phone error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/verifyEmail/{id}")
    public ResponseEntity<Void> verifyEmail(@PathVariable String id) {
        try {
            authService.verifyEmail(id);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", frontendUrl + "#/auth/success")
                    .build();
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.USER_BLOCKED.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            log.error("Verify email error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UpdateAuthResponse>> updateAuth(
            @PathVariable String id,
            @RequestBody UpdateAuthRequest request) {
        try {
            Auth updated = authService.updateAuth(id, request);
            AuthUserInfoResponse updatedUser = AuthUserInfoResponse.builder()
                    .mongoId(updated.getId())
                    .id(updated.getId())
                    .email(updated.getEmail())
                    .firstName(updated.getFirstName())
                    .lastName(updated.getLastName())
                    .clubId(updated.getClubId())
                    .seasonId(updated.getSeasonId())
                    .role(updated.getRole())
                    .roleId(updated.getRoleId())
                    .isVerified(updated.getIsVerified())
                    .isBlocked(updated.getIsBlocked())
                    .userName(updated.getUserName())
                    .phone(updated.getPhone())
                    .build();
            return ResponseEntity.ok(ApiResponse.success(UpdateAuthResponse.builder().updateUser(updatedUser).build()));
        } catch (IllegalArgumentException e) {
            HttpStatus status = ErrorMessages.USER_BLOCKED.equals(e.getMessage())
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.NOT_FOUND;
            return ResponseEntity.status(status)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Update auth error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/mpin/status")
    public ResponseEntity<ApiResponse<MpinStatusResponse>> getMpinStatus(
            @RequestHeader("Authorization") String authorization) {
        try {
            String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
            if (token == null || !jwtTokenProvider.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Invalid or missing token"));
            }
            String authId = jwtTokenProvider.getUserIdFromToken(token);
            boolean hasMpin = authService.hasMpin(authId);
            return ResponseEntity.ok(ApiResponse.success(MpinStatusResponse.builder().hasMpin(hasMpin).build()));
        } catch (IllegalArgumentException e) {
            HttpStatus status = ErrorMessages.USER_BLOCKED.equals(e.getMessage())
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get MPIN status error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/mpin/set")
    public ResponseEntity<ApiResponse<Void>> setMpin(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody SetMpinRequest request) {
        try {
            String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
            if (token == null || !jwtTokenProvider.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Invalid or missing token"));
            }
            String authId = jwtTokenProvider.getUserIdFromToken(token);
            authService.setMpin(authId, request);
            return ResponseEntity.ok(ApiResponse.success((Void) null, "MPIN set successfully"));
        } catch (IllegalArgumentException e) {
            HttpStatus status = ErrorMessages.USER_BLOCKED.equals(e.getMessage())
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Set MPIN error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/mpin/verify")
    public ResponseEntity<ApiResponse<Void>> verifyMpin(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody VerifyMpinRequest request) {
        try {
            String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
            if (token == null || !jwtTokenProvider.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Invalid or missing token"));
            }
            String authId = jwtTokenProvider.getUserIdFromToken(token);
            authService.verifyMpin(authId, request);
            return ResponseEntity.ok(ApiResponse.success((Void) null, "MPIN verified"));
        } catch (IllegalArgumentException e) {
            HttpStatus status = ErrorMessages.USER_BLOCKED.equals(e.getMessage())
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Verify MPIN error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/verify-password")
    public ResponseEntity<ApiResponse<Void>> verifyPassword(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody VerifyPasswordRequest request) {
        try {
            String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
            if (token == null || !jwtTokenProvider.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Invalid or missing token"));
            }
            String authId = jwtTokenProvider.getUserIdFromToken(token);
            authService.verifyPassword(authId, request);
            return ResponseEntity.ok(ApiResponse.success((Void) null, "Password verified"));
        } catch (IllegalArgumentException e) {
            HttpStatus status = HttpStatus.BAD_REQUEST;
            if (ErrorMessages.USER_BLOCKED.equals(e.getMessage())) {
                status = HttpStatus.FORBIDDEN;
            } else if (ErrorMessages.INVALID_PASSWORD.equals(e.getMessage())) {
                status = HttpStatus.UNAUTHORIZED;
            }
            return ResponseEntity.status(status).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Verify password error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/mpin/reset")
    public ResponseEntity<ApiResponse<Void>> resetMpin(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ResetMpinRequest request) {
        try {
            String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
            if (token == null || !jwtTokenProvider.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Invalid or missing token"));
            }
            String authId = jwtTokenProvider.getUserIdFromToken(token);
            authService.resetMpin(authId, request);
            return ResponseEntity.ok(ApiResponse.success((Void) null, "MPIN updated successfully"));
        } catch (IllegalArgumentException e) {
            HttpStatus status = HttpStatus.BAD_REQUEST;
            if (ErrorMessages.USER_BLOCKED.equals(e.getMessage())) {
                status = HttpStatus.FORBIDDEN;
            } else if (ErrorMessages.INVALID_PASSWORD.equals(e.getMessage())) {
                status = HttpStatus.UNAUTHORIZED;
            }
            return ResponseEntity.status(status).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Reset MPIN error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/mpin/forgot")
    public ResponseEntity<ApiResponse<Void>> requestForgotMpin(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ForgotMpinRequest request) {
        try {
            String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
            if (token == null || !jwtTokenProvider.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Invalid or missing token"));
            }
            String authId = jwtTokenProvider.getUserIdFromToken(token);
            authService.requestForgotMpin(authId, request);
            return ResponseEntity.ok(ApiResponse.success((Void) null, "Reset MPIN link sent to your email"));
        } catch (IllegalArgumentException e) {
            HttpStatus status = HttpStatus.BAD_REQUEST;
            if (ErrorMessages.USER_BLOCKED.equals(e.getMessage())) {
                status = HttpStatus.FORBIDDEN;
            } else if (ErrorMessages.INVALID_PASSWORD.equals(e.getMessage())) {
                status = HttpStatus.UNAUTHORIZED;
            }
            return ResponseEntity.status(status).body(ApiResponse.error(e.getMessage()));
        } catch (RuntimeException e) {
            if ("Mail sending error.".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        } catch (Exception e) {
            log.error("Forgot MPIN error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/mpin/forgot/validate")
    public ResponseEntity<ApiResponse<Void>> validateForgotMpinToken(@RequestParam String token) {
        try {
            authService.validateForgotMpinToken(token);
            return ResponseEntity.ok(ApiResponse.success((Void) null, "Token is valid"));
        } catch (IllegalArgumentException e) {
            HttpStatus status = ErrorMessages.USER_BLOCKED.equals(e.getMessage())
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Validate forgot MPIN token error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/mpin/forgot/reset")
    public ResponseEntity<ApiResponse<Void>> resetMpinWithToken(
            @Valid @RequestBody ResetMpinWithTokenRequest request) {
        try {
            authService.resetMpinWithToken(request);
            return ResponseEntity.ok(ApiResponse.success((Void) null, "MPIN set successfully. You can now sign in and use Club Wallet."));
        } catch (IllegalArgumentException e) {
            HttpStatus status = ErrorMessages.USER_BLOCKED.equals(e.getMessage())
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Reset MPIN with token error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }
}
