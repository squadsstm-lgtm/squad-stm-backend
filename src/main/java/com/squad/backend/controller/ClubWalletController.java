package com.squad.backend.controller;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.constants.WithdrawalStatus;
import com.squad.backend.dto.request.clubwallet.CreateWithdrawalRequest;
import com.squad.backend.dto.request.clubwallet.UpdatePayoutAccountRequest;
import com.squad.backend.dto.request.clubwallet.UpdateWalletRequest;
import com.squad.backend.dto.request.clubwallet.UpdateWithdrawalRequest;
import com.squad.backend.dto.response.ApiResponse;
import com.squad.backend.dto.response.clubwallet.ClubWalletResponse;
import com.squad.backend.dto.response.PageMetaResponse;
import com.squad.backend.dto.response.clubwallet.PagedWithdrawalsResponse;
import com.squad.backend.dto.response.clubwallet.PayoutAccountResponse;
import com.squad.backend.dto.response.clubwallet.WithdrawalStatusOptionResponse;
import com.squad.backend.dto.response.clubwallet.WithdrawalRequestResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.security.TenantScope;
import com.squad.backend.service.ClubWalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/club-wallet")
@RequiredArgsConstructor
@Slf4j
public class ClubWalletController {

    private final ClubWalletService clubWalletService;

    @GetMapping("/wallet/{clubId}")
    public ResponseEntity<ApiResponse<ClubWalletResponse>> getWallet(
            @PathVariable String clubId,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            if (TenantScope.denyClubOnly(auth, clubId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(ErrorMessages.FORBIDDEN));
            }
            ClubWalletResponse response = clubWalletService.getWallet(clubId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get wallet error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/payout-account/{clubId}")
    public ResponseEntity<ApiResponse<PayoutAccountResponse>> getPayoutAccount(
            @PathVariable String clubId,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            if (TenantScope.denyClubOnly(auth, clubId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(ErrorMessages.FORBIDDEN));
            }
            PayoutAccountResponse response = clubWalletService.getPayoutAccount(clubId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            log.error("Get payout account error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/payout-account/{clubId}")
    public ResponseEntity<ApiResponse<PayoutAccountResponse>> updatePayoutAccount(
            @PathVariable String clubId,
            @Valid @RequestBody UpdatePayoutAccountRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            if (TenantScope.denyClubOnly(auth, clubId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(ErrorMessages.FORBIDDEN));
            }
            PayoutAccountResponse response = clubWalletService.updatePayoutAccount(clubId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Update payout account error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    /** Returns withdrawal statuses for filter dropdowns (club wallet & owner dashboard). */
    @GetMapping("/withdrawal-statuses")
    public ResponseEntity<ApiResponse<List<WithdrawalStatusOptionResponse>>> getWithdrawalStatuses() {
        List<WithdrawalStatusOptionResponse> statuses = List.of(
                new WithdrawalStatusOptionResponse(WithdrawalStatus.WAITING_FOR_APPROVAL, "Waiting for approval"),
                new WithdrawalStatusOptionResponse(WithdrawalStatus.VERIFIED, "Verified"),
                new WithdrawalStatusOptionResponse(WithdrawalStatus.PROCESSING, "Under processing"),
                new WithdrawalStatusOptionResponse(WithdrawalStatus.COMPLETED, "Completed / Transferred"),
                new WithdrawalStatusOptionResponse(WithdrawalStatus.FAILED, "Failed")
        );
        return ResponseEntity.ok(ApiResponse.success(statuses));
    }

    @GetMapping("/withdrawals/{clubId}")
    public ResponseEntity<ApiResponse<PagedWithdrawalsResponse>> getWithdrawals(
            @PathVariable String clubId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            if (TenantScope.denyClubOnly(auth, clubId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(ErrorMessages.FORBIDDEN));
            }
            ClubWalletService.PagedWithdrawalResult result = clubWalletService.getWithdrawalsPaged(
                    clubId, status, search, page, limit);
            return ResponseEntity.ok(ApiResponse.success(buildPagedWithdrawalsBody(result)));
        } catch (Exception e) {
            log.error("Get withdrawals error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/withdrawals")
    public ResponseEntity<ApiResponse<WithdrawalRequestResponse>> createWithdrawal(
            @Valid @RequestBody CreateWithdrawalRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            WithdrawalRequestResponse withdrawal = clubWalletService.createWithdrawal(request, auth);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(withdrawal));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Create withdrawal error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/wallet/{clubId}")
    public ResponseEntity<ApiResponse<ClubWalletResponse>> updateWallet(
            @PathVariable String clubId,
            @Valid @RequestBody UpdateWalletRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            if (TenantScope.denyClubOnly(auth, clubId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(ErrorMessages.FORBIDDEN));
            }
            ClubWalletResponse response = clubWalletService.updateWallet(clubId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Update wallet error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/withdrawals/{clubId}/{withdrawalId}")
    public ResponseEntity<ApiResponse<WithdrawalRequestResponse>> updateWithdrawal(
            @PathVariable String clubId,
            @PathVariable String withdrawalId,
            @Valid @RequestBody UpdateWithdrawalRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            if (TenantScope.denyClubOnly(auth, clubId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(ErrorMessages.FORBIDDEN));
            }
            WithdrawalRequestResponse withdrawal = clubWalletService.updateWithdrawal(clubId, withdrawalId, request);
            return ResponseEntity.ok(ApiResponse.success(withdrawal));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Update withdrawal error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/withdrawals/{clubId}/{withdrawalId}")
    public ResponseEntity<ApiResponse<WithdrawalRequestResponse>> getWithdrawalDetail(
            @PathVariable String clubId,
            @PathVariable String withdrawalId,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            if (TenantScope.denyClubOnly(auth, clubId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(ErrorMessages.FORBIDDEN));
            }
            WithdrawalRequestResponse withdrawal = clubWalletService.getWithdrawalDetail(clubId, withdrawalId);
            return ResponseEntity.ok(ApiResponse.success(withdrawal));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get withdrawal detail error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    private PagedWithdrawalsResponse buildPagedWithdrawalsBody(ClubWalletService.PagedWithdrawalResult result) {
        return PagedWithdrawalsResponse.builder()
                .withdrawals(result.withdrawals())
                .pagination(PageMetaResponse.builder()
                        .page(result.page())
                        .limit(result.limit())
                        .pageSize(result.limit())
                        .total(result.total())
                        .pages(result.pages())
                        .build())
                .build();
    }
}
