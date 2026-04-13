package com.squad.backend.controller;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.dto.request.finance.CreateTransactionRequest;
import com.squad.backend.dto.request.finance.UpdateTransactionRequest;
import com.squad.backend.dto.response.ApiResponse;
import com.squad.backend.dto.response.finance.FinanceSummaryResponse;
import com.squad.backend.dto.response.PageMetaResponse;
import com.squad.backend.dto.response.finance.PagedTransactionsResponse;
import com.squad.backend.dto.response.finance.TransactionResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.security.TenantScope;
import com.squad.backend.service.FinanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finances")
@RequiredArgsConstructor
@Slf4j
public class FinanceController {

    private final FinanceService financeService;

    @GetMapping("/transactions/{clubId}")
    public ResponseEntity<ApiResponse<PagedTransactionsResponse>> getTransactions(
            @PathVariable String clubId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String playerId,
            @RequestParam(required = false) String sessionId,
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
            FinanceService.PagedTransactionsResult result = financeService.getTransactionsPaged(
                    clubId, type, status, playerId, sessionId, page, limit);
            PagedTransactionsResponse response = PagedTransactionsResponse.builder()
                    .transactions(result.transactions())
                    .pagination(PageMetaResponse.builder()
                            .page(result.page())
                            .limit(result.limit())
                            .pageSize(result.limit())
                            .total(result.totalItems())
                            .pages(result.totalPages())
                            .build())
                    .build();
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            log.error("Get transactions error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/transactions/{clubId}/{transactionId}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransactionById(
            @PathVariable String clubId,
            @PathVariable String transactionId,
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
            TransactionResponse response = financeService.getTransactionById(clubId, transactionId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get transaction error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/transactions")
    public ResponseEntity<ApiResponse<TransactionResponse>> createTransaction(
            @Valid @RequestBody CreateTransactionRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            if (TenantScope.denyClubScopedEntity(auth, request.getClubId(), request.getSeasonId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(ErrorMessages.FORBIDDEN));
            }
            TransactionResponse response = financeService.createTransaction(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Create transaction error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/transactions/{clubId}/{transactionId}")
    public ResponseEntity<ApiResponse<TransactionResponse>> updateTransaction(
            @PathVariable String clubId,
            @PathVariable String transactionId,
            @Valid @RequestBody UpdateTransactionRequest request,
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
            TransactionResponse response = financeService.updateTransaction(clubId, transactionId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Update transaction error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/summary/{clubId}")
    public ResponseEntity<ApiResponse<FinanceSummaryResponse>> getFinancialSummary(
            @PathVariable String clubId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
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
            FinanceSummaryResponse summary = financeService.getFinancialSummary(clubId, startDate, endDate);
            return ResponseEntity.ok(ApiResponse.success(summary));
        } catch (Exception e) {
            log.error("Get financial summary error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }
}
