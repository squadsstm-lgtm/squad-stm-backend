package com.squad.backend.controller;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.dto.request.clubwallet.UpdateWithdrawalRequest;
import com.squad.backend.dto.response.ApiResponse;
import com.squad.backend.dto.response.PageMetaResponse;
import com.squad.backend.dto.response.clubwallet.PagedWithdrawalsResponse;
import com.squad.backend.dto.response.clubwallet.WithdrawalRequestResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.service.ClubWalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/master-panel")
@RequiredArgsConstructor
@Slf4j
public class MasterPanelController {

    private final ClubWalletService clubWalletService;

    @GetMapping("/withdrawals")
    public ResponseEntity<ApiResponse<PagedWithdrawalsResponse>> getWithdrawals(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit,
            @AuthenticationPrincipal Auth auth) {
        if (auth == null || !"Controller".equalsIgnoreCase(auth.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Forbidden"));
        }
        try {
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
        if (auth == null || !"Controller".equalsIgnoreCase(auth.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Forbidden"));
        }
        try {
            Map<String, String> details = clubWalletService.getWithdrawalAccountDetailsForController(withdrawalId);
            return ResponseEntity.ok(ApiResponse.success(details));
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
        if (auth == null || !"Controller".equalsIgnoreCase(auth.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Forbidden"));
        }
        try {
            request.setProcessedBy(auth.getId());
            WithdrawalRequestResponse withdrawal = clubWalletService.updateWithdrawalStatusByController(withdrawalId, request);
            return ResponseEntity.ok(ApiResponse.success(withdrawal));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Master panel update withdrawal error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }
}
