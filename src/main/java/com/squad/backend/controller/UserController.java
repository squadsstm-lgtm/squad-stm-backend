package com.squad.backend.controller;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.dto.request.user.CreateUserRequest;
import com.squad.backend.dto.request.user.InviteUserRequest;
import com.squad.backend.dto.response.ApiResponse;
import com.squad.backend.dto.response.ClubResponse;
import com.squad.backend.dto.response.PageMetaResponse;
import com.squad.backend.dto.response.user.AvailabilityResponse;
import com.squad.backend.dto.response.user.PagedUsersResponse;
import com.squad.backend.dto.response.user.UserResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.model.User;
import com.squad.backend.security.TenantScope;
import com.squad.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<ApiResponse<User>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            User user = userService.createUser(request, auth);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Create user error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedUsersResponse>> getAllUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String pageNumber,
            @RequestParam(required = false) String pageSize,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            com.squad.backend.utils.PaginationUtils.PageParams pageParams =
                    com.squad.backend.utils.PaginationUtils.parse(pageNumber, pageSize);
            int safePage = pageParams.page() != null && pageParams.page() > 0 ? pageParams.page() : 1;
            int safeSize = pageParams.size() != null && pageParams.size() > 0 ? pageParams.size() : 10;
            UserService.PagedUsersResult result = userService.getAllUsersPaged(
                    auth.getClubId(),
                    auth.getEmail(),
                    search,
                    safePage,
                    safeSize
            );
            PagedUsersResponse body = PagedUsersResponse.builder()
                    .users(result.users())
                    .pagination(PageMetaResponse.builder()
                            .page(result.currentPage())
                            .limit(result.pageSize())
                            .pageSize(result.pageSize())
                            .total(result.totalItems())
                            .pages(result.totalPages())
                            .build())
                    .build();
            return ResponseEntity.ok(ApiResponse.success(body));
        } catch (Exception e) {
            log.error("Get users error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/get-clubs")
    public ResponseEntity<ApiResponse<List<ClubResponse>>> getAllClubs() {
        try {
            List<ClubResponse> clubs = userService.getAllClubs();
            return ResponseEntity.ok(ApiResponse.success(clubs));
        } catch (Exception e) {
            log.error("Get clubs error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> checkEmail(
            @RequestParam String email,
            @RequestParam(required = false) String clubId,
            @RequestParam(required = false) String excludeUserId,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("email is required."));
            }
            String effectiveClubId = TenantScope.resolveClubForAvailabilityCheck(clubId, auth);
            boolean available = userService.checkEmailAvailability(email, effectiveClubId, excludeUserId);
            AvailabilityResponse response = new AvailabilityResponse(available);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.FORBIDDEN.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Check email error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/check-email")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> checkEmailPost(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            String email = request.get("email");
            String clubId = request.get("clubId");
            String userId = request.get("userId");
            String excludeUserId = request.get("excludeUserId"); // Support both for compatibility

            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("email is required."));
            }

            String effectiveClubId = TenantScope.resolveClubForAvailabilityCheck(clubId, auth);
            // Use excludeUserId if provided, otherwise use userId
            String userIdToExclude = excludeUserId != null ? excludeUserId : userId;
            boolean available = userService.checkEmailAvailability(email, effectiveClubId, userIdToExclude);
            AvailabilityResponse response = new AvailabilityResponse(available);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.FORBIDDEN.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Check email error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/check-phone")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> checkPhone(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            String phone = request.get("phone");
            String clubId = request.get("clubId");
            String userId = request.get("userId");
            if (phone == null || phone.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("phone is required."));
            }
            String effectiveClubId = TenantScope.resolveClubForAvailabilityCheck(clubId, auth);
            boolean available = userService.checkPhoneAvailability(phone, effectiveClubId, userId);
            AvailabilityResponse response = new AvailabilityResponse(available);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.FORBIDDEN.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Check phone error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/Invite-User")
    public ResponseEntity<ApiResponse<User>> inviteUser(
            @Valid @RequestBody InviteUserRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            User user = userService.inviteUser(request, auth, auth.getSeasonId());
            return ResponseEntity.ok(ApiResponse.success(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (RuntimeException e) {
            if ("Mail sending error.".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        } catch (Exception e) {
            log.error("Invite user error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/change-status/{id}")
    public ResponseEntity<ApiResponse<Auth>> changeUserStatus(
            @PathVariable String id,
            @RequestBody Map<String, Boolean> request,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            Boolean isBlocked = request.get("isBlocked");
            if (isBlocked == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("isBlocked field is required"));
            }
            Auth authUser = userService.changeUserStatus(id, isBlocked, auth);
            return ResponseEntity.ok(ApiResponse.success(authUser));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Change user status error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @PathVariable String id,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            UserResponse response = userService.getUserById(id, auth);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get user error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Auth>> updateUser(
            @PathVariable String id,
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            Auth authResult = userService.updateUser(id, request, auth);
            return ResponseEntity.ok(ApiResponse.success(authResult));
        } catch (IllegalArgumentException e) {
            HttpStatus status = HttpStatus.NOT_FOUND;
            if (e.getMessage().contains("is already added for this club")) {
                status = HttpStatus.BAD_REQUEST;
            }
            return ResponseEntity.status(status)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Update user error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/{id}/{token}")
    public ResponseEntity<ApiResponse<Auth>> updateUserWithToken(
            @PathVariable String id,
            @PathVariable String token,
            @Valid @RequestBody CreateUserRequest request) {
        try {
            Auth authResult = userService.updateUserWithToken(id, token, request);
            return ResponseEntity.ok(ApiResponse.success(authResult));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            HttpStatus status = HttpStatus.NOT_FOUND;
            if (e.getMessage().contains("is already added for this club")) {
                status = HttpStatus.BAD_REQUEST;
            }
            return ResponseEntity.status(status)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Update user via token error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/{id}/{token}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserByIdWithToken(
            @PathVariable String id,
            @PathVariable String token) {
        try {
            UserResponse response = userService.getUserByIdWithToken(id, token);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            if ("Token is invalid".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get user by id with token error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable String id,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            userService.deleteUser(id, auth);
            return ResponseEntity.ok(ApiResponse.success(null, "User deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Delete user error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }
}
