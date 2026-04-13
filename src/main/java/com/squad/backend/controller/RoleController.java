package com.squad.backend.controller;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.dto.request.role.CreateRoleRequest;
import com.squad.backend.dto.response.ApiResponse;
import com.squad.backend.dto.response.PageMetaResponse;
import com.squad.backend.dto.response.role.PagedRolesResponse;
import com.squad.backend.dto.response.role.RoleDeleteResponse;
import com.squad.backend.dto.response.role.RoleExistsResponse;
import com.squad.backend.dto.response.role.RoleResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.security.TenantScope;
import com.squad.backend.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@Slf4j
public class RoleController {

    private final RoleService roleService;

    @PostMapping
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(
            @Valid @RequestBody CreateRoleRequest request,
            @RequestParam String clubId,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            if (!TenantScope.isControllerWithoutClub(auth) && TenantScope.denyClubOnly(auth, clubId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(ErrorMessages.FORBIDDEN));
            }
            RoleResponse role = roleService.createRole(request, clubId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(role));
        } catch (Exception e) {
            log.error("Create role error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedRolesResponse>> getAllRoles(
            @RequestParam String clubId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String pageNumber,
            @RequestParam(required = false) String pageSize,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            if (!TenantScope.isControllerWithoutClub(auth) && TenantScope.denyClubOnly(auth, clubId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(ErrorMessages.FORBIDDEN));
            }
            com.squad.backend.utils.PaginationUtils.PageParams pageParams =
                    com.squad.backend.utils.PaginationUtils.parse(pageNumber, pageSize);
            int safePage = pageParams.page() != null && pageParams.page() > 0 ? pageParams.page() : 1;
            int safeSize = pageParams.size() != null && pageParams.size() > 0 ? pageParams.size() : 10;
            RoleService.PagedRolesResult result = roleService.getAllRolesPaged(clubId, search, safePage, safeSize);
            PagedRolesResponse body = PagedRolesResponse.builder()
                    .roles(result.roles())
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
            log.error("Get roles error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/check-role")
    public ResponseEntity<ApiResponse<RoleExistsResponse>> checkRole(
            @RequestParam String role,
            @RequestParam String clubId,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            if (!TenantScope.isControllerWithoutClub(auth) && TenantScope.denyClubOnly(auth, clubId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(ErrorMessages.FORBIDDEN));
            }
            boolean exists = roleService.checkRoleExists(role, clubId);
            return ResponseEntity.ok(ApiResponse.success(new RoleExistsResponse(exists)));
        } catch (Exception e) {
            log.error("Check role error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(
            @PathVariable String id,
            @RequestParam String clubId,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            if (!TenantScope.isControllerWithoutClub(auth) && TenantScope.denyClubOnly(auth, clubId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(ErrorMessages.FORBIDDEN));
            }
            RoleResponse role = roleService.getRoleById(id, clubId);
            return ResponseEntity.ok(ApiResponse.success(role));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get role by id error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @PathVariable String id,
            @RequestParam String clubId,
            @Valid @RequestBody CreateRoleRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            if (!TenantScope.isControllerWithoutClub(auth) && TenantScope.denyClubOnly(auth, clubId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(ErrorMessages.FORBIDDEN));
            }
            RoleResponse role = roleService.updateRole(id, clubId, request);
            return ResponseEntity.ok(ApiResponse.success(role));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Update role error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleDeleteResponse>> deleteRole(
            @PathVariable String id,
            @RequestParam String clubId,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            if (!TenantScope.isControllerWithoutClub(auth) && TenantScope.denyClubOnly(auth, clubId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(ErrorMessages.FORBIDDEN));
            }
            RoleDeleteResponse response = roleService.deleteRole(id, clubId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Delete role error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }
}
