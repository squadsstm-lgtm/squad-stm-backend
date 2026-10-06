package com.squad.backend.controller;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.dto.request.clubwallet.UpdateWithdrawalRequest;
import com.squad.backend.dto.request.masterpanel.CreateControllerRequest;
import com.squad.backend.dto.request.masterpanel.InviteControllerRequest;
import com.squad.backend.dto.request.masterpanel.UpdateControllerDetailsRequest;
import com.squad.backend.dto.request.masterpanel.UpdateClubPlatformFeeRequest;
import com.squad.backend.dto.request.masterpanel.UpdateControllerPermissionsRequest;
import com.squad.backend.dto.request.masterpanel.UpdateDefaultPlatformFeeRequest;
import com.squad.backend.dto.response.ApiResponse;
import com.squad.backend.dto.response.PageMetaResponse;
import com.squad.backend.dto.response.clubwallet.PagedWithdrawalsResponse;
import com.squad.backend.dto.response.clubwallet.WithdrawalRequestResponse;
import com.squad.backend.dto.response.masterpanel.ClubPlatformFeeResponse;
import com.squad.backend.dto.response.masterpanel.ControllerInviteResponse;
import com.squad.backend.dto.response.masterpanel.ControllerListItemResponse;
import com.squad.backend.dto.response.masterpanel.ControllerPermissionsResponse;
import com.squad.backend.dto.response.masterpanel.MasterClubDetailResponse;
import com.squad.backend.dto.response.masterpanel.MasterClubListItemResponse;
import com.squad.backend.dto.response.masterpanel.MasterClubsListResponse;
import com.squad.backend.dto.response.masterpanel.MasterClubsPlatformSummaryResponse;
import com.squad.backend.dto.response.masterpanel.PlatformSettingsResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.service.ClubWalletService;
import com.squad.backend.service.ControllerPermissionsService;
import com.squad.backend.service.MasterPanelClubsService;
import com.squad.backend.service.PlatformSettingsService;
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
@RequestMapping("/api/master-panel")
@RequiredArgsConstructor
@Slf4j
public class MasterPanelController {

    private final ClubWalletService clubWalletService;
    private final MasterPanelClubsService masterPanelClubsService;
    private final ControllerPermissionsService controllerPermissionsService;
    private final PlatformSettingsService platformSettingsService;

    @GetMapping("/me/permissions")
    public ResponseEntity<ApiResponse<ControllerPermissionsResponse>> getMyPermissions(
            @AuthenticationPrincipal Auth auth) {
        try {
            controllerPermissionsService.requireController(auth);
            ControllerPermissionsResponse data =
                    controllerPermissionsService.getOrBootstrapPermissions(auth.getId());
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel get my permissions error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/controllers")
    public ResponseEntity<ApiResponse<List<ControllerListItemResponse>>> listControllers(
            @AuthenticationPrincipal Auth auth) {
        try {
            List<ControllerListItemResponse> data =
                    controllerPermissionsService.listControllersForActor(auth);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel list controllers error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/controllers/invite")
    public ResponseEntity<ApiResponse<ControllerInviteResponse>> inviteController(
            @Valid @RequestBody InviteControllerRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            ControllerInviteResponse data =
                    controllerPermissionsService.inviteController(request, auth);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(data, "Controller invite sent."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel invite controller error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/controllers")
    public ResponseEntity<ApiResponse<ControllerListItemResponse>> createController(
            @Valid @RequestBody CreateControllerRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            ControllerListItemResponse data =
                    controllerPermissionsService.createController(request, auth);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(data, "Controller created."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel create controller error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/controllers/{authId}/permissions")
    public ResponseEntity<ApiResponse<ControllerPermissionsResponse>> updateControllerPermissions(
            @PathVariable String authId,
            @RequestBody UpdateControllerPermissionsRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            ControllerPermissionsResponse data =
                    controllerPermissionsService.updatePermissions(authId, request, auth);
            return ResponseEntity.ok(ApiResponse.success(data, "Permissions updated."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel update controller permissions error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/controllers/{authId}")
    public ResponseEntity<ApiResponse<ControllerListItemResponse>> updateControllerDetails(
            @PathVariable String authId,
            @Valid @RequestBody UpdateControllerDetailsRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            ControllerListItemResponse data =
                    controllerPermissionsService.updateDetails(authId, request, auth);
            return ResponseEntity.ok(ApiResponse.success(data, "Controller details updated."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel update controller details error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @DeleteMapping("/controllers/{authId}/invite")
    public ResponseEntity<ApiResponse<Void>> revokeControllerInvite(
            @PathVariable String authId,
            @AuthenticationPrincipal Auth auth) {
        try {
            controllerPermissionsService.revokePendingInvite(authId, auth);
            return ResponseEntity.ok(ApiResponse.success(null, "Invite revoked."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel revoke controller invite error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/controllers/{authId}/block")
    public ResponseEntity<ApiResponse<ControllerListItemResponse>> blockController(
            @PathVariable String authId,
            @RequestParam(defaultValue = "true") boolean blocked,
            @AuthenticationPrincipal Auth auth) {
        try {
            ControllerListItemResponse data =
                    controllerPermissionsService.setBlocked(authId, blocked, auth);
            return ResponseEntity.ok(ApiResponse.success(data,
                    blocked ? "Controller blocked." : "Controller unblocked."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel block controller error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/controllers/{authId}/status")
    public ResponseEntity<ApiResponse<ControllerListItemResponse>> setControllerStatus(
            @PathVariable String authId,
            @RequestParam String status,
            @AuthenticationPrincipal Auth auth) {
        try {
            ControllerListItemResponse data =
                    controllerPermissionsService.setStatus(authId, status, auth);
            return ResponseEntity.ok(ApiResponse.success(data, "Controller status updated."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel set controller status error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/clubs/summary")
    public ResponseEntity<ApiResponse<MasterClubsPlatformSummaryResponse>> getClubsSummary(
            @AuthenticationPrincipal Auth auth) {
        try {
            ControllerPermissionsResponse perms = controllerPermissionsService.requirePermission(
                    auth, ControllerPermissionsResponse::getViewClubs);
            MasterClubsPlatformSummaryResponse data =
                    masterPanelClubsService.getPlatformSummary(auth.getSeasonId());
            if (!Boolean.TRUE.equals(perms.getViewClubMoney())) {
                redactSummaryMoney(data);
            }
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel clubs summary error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/clubs")
    public ResponseEntity<ApiResponse<MasterClubsListResponse>> getClubs(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit,
            @AuthenticationPrincipal Auth auth) {
        try {
            ControllerPermissionsResponse perms = controllerPermissionsService.requirePermission(
                    auth, ControllerPermissionsResponse::getViewClubs);
            MasterClubsListResponse data = masterPanelClubsService.getClubs(
                    auth.getSeasonId(), search, status, page, limit);
            if (!Boolean.TRUE.equals(perms.getViewClubMoney())) {
                redactClubsListMoney(data);
            }
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel clubs list error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/platform-settings")
    public ResponseEntity<ApiResponse<PlatformSettingsResponse>> getPlatformSettings(
            @AuthenticationPrincipal Auth auth) {
        try {
            requireClubMoney(auth);
            return ResponseEntity.ok(ApiResponse.success(platformSettingsService.getSettings()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel get platform settings error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/platform-settings")
    public ResponseEntity<ApiResponse<PlatformSettingsResponse>> updatePlatformSettings(
            @Valid @RequestBody UpdateDefaultPlatformFeeRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            requireClubMoney(auth);
            PlatformSettingsResponse data =
                    platformSettingsService.updateDefault(request.getDefaultPlatformFee(), auth.getId());
            return ResponseEntity.ok(ApiResponse.success(data, "Default platform fee updated."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel update platform settings error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/clubs/{clubId}/platform-fee")
    public ResponseEntity<ApiResponse<ClubPlatformFeeResponse>> updateClubPlatformFee(
            @PathVariable String clubId,
            @Valid @RequestBody UpdateClubPlatformFeeRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            requireClubMoney(auth);
            ClubPlatformFeeResponse data = masterPanelClubsService.updateClubPlatformFee(
                    clubId, request.getPlatformFee(), auth.getId());
            return ResponseEntity.ok(ApiResponse.success(data, "Club platform fee updated."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            HttpStatus status = "Club not found".equals(e.getMessage())
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel update club platform fee error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/clubs/{clubId}/platform-fee/sync-default")
    public ResponseEntity<ApiResponse<ClubPlatformFeeResponse>> syncClubPlatformFee(
            @PathVariable String clubId,
            @AuthenticationPrincipal Auth auth) {
        try {
            requireClubMoney(auth);
            ClubPlatformFeeResponse data =
                    masterPanelClubsService.syncClubPlatformFeeToDefault(clubId, auth.getId());
            return ResponseEntity.ok(ApiResponse.success(data, "Club platform fee set to the default."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            HttpStatus status = "Club not found".equals(e.getMessage())
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel sync club platform fee error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/clubs/{clubId}")
    public ResponseEntity<ApiResponse<MasterClubDetailResponse>> getClubDetail(
            @PathVariable String clubId,
            @AuthenticationPrincipal Auth auth) {
        try {
            ControllerPermissionsResponse perms = controllerPermissionsService.requirePermission(
                    auth, ControllerPermissionsResponse::getViewClubs);
            MasterClubDetailResponse data =
                    masterPanelClubsService.getClubDetail(clubId, auth.getSeasonId());
            if (!Boolean.TRUE.equals(perms.getViewClubMoney())) {
                redactClubDetailMoney(data);
            }
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel club detail error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/withdrawals")
    public ResponseEntity<ApiResponse<PagedWithdrawalsResponse>> getWithdrawals(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit,
            @AuthenticationPrincipal Auth auth) {
        try {
            controllerPermissionsService.requirePermission(
                    auth, ControllerPermissionsResponse::getViewRequests);
            ClubWalletService.PagedControllerWithdrawalResult result =
                    clubWalletService.getWithdrawalsForControllerPaged(status, page, limit);
            PagedWithdrawalsResponse data = PagedWithdrawalsResponse.builder()
                    .withdrawals(result.withdrawals())
                    .pagination(PageMetaResponse.builder()
                            .page(result.page())
                            .limit(result.limit())
                            .pageSize(result.limit())
                            .total(result.total())
                            .pages(result.pages())
                            .build())
                    .build();
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel get withdrawals error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/withdrawals/{withdrawalId}/account-details")
    public ResponseEntity<ApiResponse<Map<String, String>>> getWithdrawalAccountDetails(
            @PathVariable String withdrawalId,
            @AuthenticationPrincipal Auth auth) {
        try {
            controllerPermissionsService.requirePermission(
                    auth, ControllerPermissionsResponse::getViewRequestPaymentDetails);
            Map<String, String> details = clubWalletService.getWithdrawalAccountDetailsForController(withdrawalId);
            return ResponseEntity.ok(ApiResponse.success(details));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel get account details error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/withdrawals/{withdrawalId}")
    public ResponseEntity<ApiResponse<WithdrawalRequestResponse>> updateWithdrawalStatus(
            @PathVariable String withdrawalId,
            @Valid @RequestBody UpdateWithdrawalRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            controllerPermissionsService.requirePermission(
                    auth, ControllerPermissionsResponse::getWorkRequests);
            request.setProcessedBy(auth.getId());
            WithdrawalRequestResponse withdrawal = clubWalletService.updateWithdrawalStatusByController(withdrawalId, request);
            return ResponseEntity.ok(ApiResponse.success(withdrawal));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel update withdrawal error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    private void redactSummaryMoney(MasterClubsPlatformSummaryResponse data) {
        data.setTotalEarnings(0);
        data.setPlatformCollected(0);
        data.setTotalOutstanding(0);
        data.setTotalAvailableForWithdrawal(0);
        data.setTotalPendingWithdrawals(0);
    }

    private void redactClubsListMoney(MasterClubsListResponse data) {
        if (data.getClubs() == null) {
            return;
        }
        for (MasterClubListItemResponse club : data.getClubs()) {
            club.setTotalEarnings(0.0);
            club.setPlatformCollected(0.0);
            club.setOutstandingAmount(0.0);
            club.setAvailableForWithdrawal(0.0);
            club.setPendingWithdrawals(0.0);
        }
    }

    private void redactClubDetailMoney(MasterClubDetailResponse data) {
        data.setPlatformCollected(0.0);
        data.setOutstandingAmount(0.0);
        data.setOutstandingCount(0);
        data.setWallet(null);
        data.setRecentPayments(null);
        data.setRecentWithdrawals(null);
        data.setPlatformFee(null);
        data.setPlatformFeeSaved(null);
        data.setPlatformFeeFollowsDefault(null);
        data.setPlatformFeeUpdatedAt(null);
        data.setPlatformFeeUpdatedByName(null);
        data.setDefaultPlatformFee(null);
    }

    private void requireClubMoney(Auth auth) {
        controllerPermissionsService.requirePermission(auth, ControllerPermissionsResponse::getViewClubs);
        controllerPermissionsService.requirePermission(auth, ControllerPermissionsResponse::getViewClubMoney);
    }
}
