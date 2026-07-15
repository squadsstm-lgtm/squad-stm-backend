package com.squad.backend.controller;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.dto.request.confirmationrequest.SendPaymentRequestRequest;
import com.squad.backend.dto.request.confirmationrequest.UpdatePaymentRequest;
import com.squad.backend.dto.response.ApiResponse;
import com.squad.backend.dto.response.confirmationrequest.ConfirmationRequestsListResponse;
import com.squad.backend.dto.response.confirmationrequest.ConfirmationRequestsMetaResponse;
import com.squad.backend.dto.response.confirmationrequest.ConfirmationRequestsPageResponse;
import com.squad.backend.dto.response.confirmationrequest.ConfirmationRequestResponse;
import com.squad.backend.dto.response.confirmationrequest.ConfirmationRequestDetailResponse;
import com.squad.backend.dto.response.confirmationrequest.UpdatePaymentResponse;
import com.squad.backend.dto.response.confirmationrequest.SendPaymentRequestResponse;
import com.squad.backend.dto.response.finance.FinanceTotalResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.service.ConfirmationRequestService;
import com.squad.backend.utils.PaginationUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
@Slf4j
public class ConfirmationRequestController {

    private final ConfirmationRequestService confirmationRequestService;

    @GetMapping("/pending-payments")
    public ResponseEntity<ApiResponse<ConfirmationRequestsPageResponse>> getPendingPayments(
            @RequestParam(required = false) String team,
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String session,
            @RequestParam(required = false) String pageNumber,
            @RequestParam(required = false) String pageSize,
            @AuthenticationPrincipal Auth auth) {
        try {
            PaginationUtils.PageParams pageParams = PaginationUtils.parse(pageNumber, pageSize);
            int requestedPage = pageParams.page() != null ? pageParams.page() : 1;
            int requestedSize = pageParams.size() != null ? pageParams.size() : 20;
            ConfirmationRequestService.PendingPaymentsPagedResult result =
                    confirmationRequestService.getPendingPaymentsPaged(
                            auth.getClubId(),
                            auth.getSeasonId(),
                            team,
                            player,
                            session,
                            requestedPage,
                            requestedSize
                    );

            ConfirmationRequestsPageResponse body = ConfirmationRequestsPageResponse.builder()
                    .requests(result.requests())
                    .meta(ConfirmationRequestsMetaResponse.builder()
                            .totalItems(result.totalItems())
                            .totalPages(result.totalPages())
                            .currentPage(result.currentPage())
                            .pageSize(result.pageSize())
                            .build())
                    .build();
            return ResponseEntity.ok(ApiResponse.success(body));
        } catch (Exception e) {
            log.error("Get pending payments error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/confirm-payments")
    public ResponseEntity<ApiResponse<ConfirmationRequestsListResponse>> getConfirmPayments(
            @AuthenticationPrincipal Auth auth) {
        try {
            List<ConfirmationRequestResponse> requestResponses = confirmationRequestService.getConfirmPayments(
                    auth.getClubId(), auth.getSeasonId());

            return ResponseEntity.ok(ApiResponse.success(
                    ConfirmationRequestsListResponse.builder().requests(requestResponses).build()));
        } catch (Exception e) {
            log.error("Get confirm payments error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/total")
    public ResponseEntity<ApiResponse<FinanceTotalResponse>> getTotal(
            @AuthenticationPrincipal Auth auth) {
        try {
            Integer totalPayments = confirmationRequestService.getTotalPayments(
                    auth.getClubId(), auth.getSeasonId());

            return ResponseEntity.ok(ApiResponse.success(FinanceTotalResponse.builder().total(totalPayments).build()));
        } catch (Exception e) {
            log.error("Get total error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/all-payments")
    public ResponseEntity<ApiResponse<ConfirmationRequestsPageResponse>> getAllPayments(
            @RequestParam(required = false) String team,
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String session,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String pageNumber,
            @RequestParam(required = false) String pageSize,
            @AuthenticationPrincipal Auth auth) {
        try {
            PaginationUtils.PageParams pageParams = PaginationUtils.parse(pageNumber, pageSize);
            int requestedPage = pageParams.page() != null ? pageParams.page() : 1;
            int requestedSize = pageParams.size() != null ? pageParams.size() : 20;
            ConfirmationRequestService.AllPaymentsPagedResult result =
                    confirmationRequestService.getAllPaymentsPaged(
                            auth.getClubId(),
                            auth.getSeasonId(),
                            team,
                            player,
                            session,
                            paymentStatus,
                            requestedPage,
                            requestedSize
                    );
            return ResponseEntity.ok(ApiResponse.success(buildAllPaymentsBody(
                    result.requests(),
                    result.totalItems(),
                    result.totalPages(),
                    result.currentPage(),
                    result.pageSize(),
                    result.pendingCount(),
                    result.paidCount(),
                    result.totalPendingAmount(),
                    result.totalPaidAmount()
            )));
        } catch (Exception e) {
            log.error("Get all payments error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    private ConfirmationRequestsPageResponse buildAllPaymentsBody(
            List<ConfirmationRequestResponse> requests,
            long totalItems,
            int totalPages,
            int currentPage,
            int pageSize,
            long pendingCount,
            long paidCount,
            double totalPendingAmount,
            double totalPaidAmount) {
        ConfirmationRequestsMetaResponse meta = ConfirmationRequestsMetaResponse.builder()
                .totalItems(totalItems)
                .totalPages(totalPages)
                .currentPage(currentPage)
                .pageSize(pageSize)
                .pendingCount(pendingCount)
                .paidCount(paidCount)
                .totalPendingAmount(totalPendingAmount)
                .totalPaidAmount(totalPaidAmount)
                .build();
        return ConfirmationRequestsPageResponse.builder()
                .requests(requests)
                .meta(meta)
                .build();
    }

    @GetMapping("/{id}/{token}")
    public ResponseEntity<ApiResponse<ConfirmationRequestDetailResponse>> getRequestByIdWithToken(
            @PathVariable String id,
            @PathVariable String token) {
        try {
            ConfirmationRequestDetailResponse response = confirmationRequestService.getRequestById(id, token);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            if ("This Link Is Expired".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get request by id error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ConfirmationRequestDetailResponse>> getRequestById(
            @PathVariable String id,
            @AuthenticationPrincipal Auth auth) {
        try {
            ConfirmationRequestDetailResponse response = confirmationRequestService.getRequestById(id, null);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get request by id error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/update")
    public ResponseEntity<ApiResponse<ConfirmationRequestResponse>> updateAttendance(
            @RequestParam String id,
            @RequestParam String playerAttendanceResponse,
            @RequestParam String token) {
        try {
            ConfirmationRequestResponse response = confirmationRequestService.updateAttendance(id, playerAttendanceResponse, token);
            return ResponseEntity.ok(ApiResponse.success(response, "Attendance updated successfully"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Update attendance error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/payment")
    public ResponseEntity<ApiResponse<UpdatePaymentResponse>> updatePayment(
            @Valid @RequestBody UpdatePaymentRequest request) {
        try {
            UpdatePaymentResponse response = confirmationRequestService.updatePayment(request);
            String message = "Yes".equals(request.getPayment()) 
                    ? "Payment processed successfully and transaction created"
                    : "Payment status updated successfully";
            return ResponseEntity.ok(ApiResponse.success(response, message));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Update payment error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/send-payment-request")
    public ResponseEntity<ApiResponse<List<SendPaymentRequestResponse>>> sendPaymentRequest(
            @Valid @RequestBody List<SendPaymentRequestRequest> requests,
            @AuthenticationPrincipal Auth auth) {
        try {
            List<SendPaymentRequestResponse> results = requests.stream()
                    .map(request -> confirmationRequestService.sendPaymentRequest(request, auth))
                    .collect(Collectors.toList());

            boolean anyWhatsApp = requests.stream()
                    .anyMatch(r -> r.getCommunicationMethod() != null
                            && "whatsapp".equalsIgnoreCase(r.getCommunicationMethod().trim()));
            String message = anyWhatsApp
                    ? "Payment link ready to share on WhatsApp"
                    : "Payment request sent successfully";

            return ResponseEntity.ok(ApiResponse.success(results, message));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Send payment request error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }
}
