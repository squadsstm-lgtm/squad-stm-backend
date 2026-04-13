package com.squad.backend.service;

import com.squad.backend.dto.request.finance.CreateTransactionRequest;
import com.squad.backend.dto.request.finance.UpdateTransactionRequest;
import com.squad.backend.dto.response.clubwallet.ClubWalletResponse;
import com.squad.backend.dto.response.finance.FinanceSummaryResponse;
import com.squad.backend.dto.response.finance.TransactionResponse;
import com.squad.backend.model.Transaction;
import com.squad.backend.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceService {

    private final TransactionRepository transactionRepository;
    private final ClubWalletService clubWalletService;
    private final MongoTemplate mongoTemplateClient;
    private static final Sort DEFAULT_TRANSACTIONS_SORT =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id"));

    public PagedTransactionsResult getTransactionsPaged(
            String clubId,
            String type,
            String status,
            String playerId,
            String sessionId,
            int page,
            int limit) {
        int safePage = Math.max(1, page);
        int safeLimit = Math.max(1, limit);

        Query query = new Query();
        query.addCriteria(Criteria.where("clubId").is(clubId));
        if (hasText(type)) query.addCriteria(Criteria.where("type").is(type));
        if (hasText(status)) query.addCriteria(Criteria.where("status").is(status));
        if (hasText(playerId)) query.addCriteria(Criteria.where("playerId").is(playerId));
        if (hasText(sessionId)) query.addCriteria(Criteria.where("sessionId").is(sessionId));

        long totalItems = mongoTemplateClient.count(query, Transaction.class);
        Pageable pageable = PageRequest.of(safePage - 1, safeLimit, DEFAULT_TRANSACTIONS_SORT);
        query.with(pageable);
        List<TransactionResponse> transactions = mongoTemplateClient.find(query, Transaction.class)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        int totalPages = (int) Math.ceil((double) totalItems / safeLimit);
        return new PagedTransactionsResult(transactions, totalItems, totalPages, safePage, safeLimit);
    }

    public TransactionResponse getTransactionById(String clubId, String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .filter(t -> clubId.equals(t.getClubId()))
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        return mapToResponse(transaction);
    }

    @Transactional
    public TransactionResponse createTransaction(CreateTransactionRequest request) {
        if (request.getClubId() == null || request.getPlayerId() == null || 
            request.getSessionId() == null || request.getAmount() == null) {
            throw new IllegalArgumentException("Missing required fields");
        }

        Transaction transaction = new Transaction();
        transaction.setClubId(request.getClubId());
        transaction.setSeasonId(request.getSeasonId());
        transaction.setPlayerId(request.getPlayerId());
        transaction.setSessionId(request.getSessionId());
        transaction.setTeamId(request.getTeamId());
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency() != null ? request.getCurrency() : "GBP");
        transaction.setType(request.getType());
        transaction.setStatus(request.getStatus());
        transaction.setPaymentMethod(request.getPaymentMethod());
        transaction.setStripeTransactionId(request.getStripeTransactionId());
        transaction.setDescription(request.getDescription());
        transaction.setNotes(request.getNotes());
        transaction.setSessionDate(request.getSessionDate() != null ? request.getSessionDate() : LocalDate.now());
        transaction.setMoneyLocked(true);
        transaction.setAvailableForWithdrawal(false);
        transaction.setSessionStatus("pending");
        transaction.setProcessingFee(0.0);

        transaction = transactionRepository.save(transaction);

        if ("payment".equals(request.getType()) && "completed".equals(request.getStatus())) {
            clubWalletService.addEarnings(request.getClubId(), request.getAmount());
        }

        return mapToResponse(transaction);
    }

    @Transactional
    public TransactionResponse updateTransaction(String clubId, String transactionId, UpdateTransactionRequest request) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .filter(t -> clubId.equals(t.getClubId()))
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

        if (request.getStatus() != null) {
            transaction.setStatus(request.getStatus());
        }
        if (request.getSessionStatus() != null) {
            transaction.setSessionStatus(request.getSessionStatus());
        }
        if (request.getMoneyLocked() != null) {
            transaction.setMoneyLocked(request.getMoneyLocked());
        }
        if (request.getAvailableForWithdrawal() != null) {
            transaction.setAvailableForWithdrawal(request.getAvailableForWithdrawal());
        }
        if (request.getDescription() != null) {
            transaction.setDescription(request.getDescription());
        }
        if (request.getNotes() != null) {
            transaction.setNotes(request.getNotes());
        }

        transaction = transactionRepository.save(transaction);
        return mapToResponse(transaction);
    }

    public FinanceSummaryResponse getFinancialSummary(String clubId, String startDate, String endDate) {
        ClubWalletResponse wallet = clubWalletService.getWallet(clubId);

        Instant rangeStart = parseDateToInstant(startDate, true);
        Instant rangeEnd = parseDateToInstant(endDate, false);

        List<Transaction> allTransactions = transactionRepository.findByClubId(clubId);
        List<Transaction> filteredTransactions = allTransactions;
        if (rangeStart != null || rangeEnd != null) {
            filteredTransactions = allTransactions.stream()
                    .filter(t -> isWithinRange(t.getCreatedAt(), rangeStart, rangeEnd))
                    .collect(Collectors.toList());
        }

        Map<String, Object> transactionSummary = filteredTransactions.stream()
                .collect(Collectors.groupingBy(
                        Transaction::getType,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> {
                                    Map<String, Object> summary = new HashMap<>();
                                    summary.put("count", list.size());
                                    summary.put("totalAmount", list.stream()
                                            .mapToDouble(t -> t.getAmount() != null ? t.getAmount() : 0.0)
                                            .sum());
                                    summary.put("totalFees", list.stream()
                                            .mapToDouble(t -> t.getProcessingFee() != null ? t.getProcessingFee() : 0.0)
                                            .sum());
                                    return summary;
                                }
                        )
                ));

        Map<String, Object> withdrawalSummary = clubWalletService.getWithdrawalSummary(clubId, rangeStart, rangeEnd);

        return FinanceSummaryResponse.builder()
                .wallet(wallet)
                .transactions(transactionSummary)
                .withdrawals(withdrawalSummary)
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .clubId(transaction.getClubId())
                .seasonId(transaction.getSeasonId())
                .playerId(transaction.getPlayerId())
                .sessionId(transaction.getSessionId())
                .teamId(transaction.getTeamId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .type(transaction.getType())
                .status(transaction.getStatus())
                .sessionDate(transaction.getSessionDate())
                .sessionStatus(transaction.getSessionStatus())
                .moneyLocked(transaction.getMoneyLocked())
                .availableForWithdrawal(transaction.getAvailableForWithdrawal())
                .paymentMethod(transaction.getPaymentMethod())
                .stripeTransactionId(transaction.getStripeTransactionId())
                .stripeCustomerId(transaction.getStripeCustomerId())
                .processingFee(transaction.getProcessingFee())
                .createdAt(transaction.getCreatedAt())
                .completedAt(transaction.getCompletedAt())
                .build();
    }

    private static Instant parseDateToInstant(String dateStr, boolean startOfDay) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(dateStr);
        } catch (DateTimeParseException e) {
            // try date-only format (yyyy-MM-dd)
            try {
                LocalDate d = LocalDate.parse(dateStr);
                return startOfDay
                        ? d.atStartOfDay(ZoneOffset.UTC).toInstant()
                        : d.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    private static boolean isWithinRange(Instant value, Instant rangeStart, Instant rangeEnd) {
        if (value == null) return false;
        if (rangeStart != null && value.isBefore(rangeStart)) return false;
        if (rangeEnd != null && value.isAfter(rangeEnd)) return false;
        return true;
    }

    public record PagedTransactionsResult(
            List<TransactionResponse> transactions,
            long totalItems,
            int totalPages,
            int page,
            int limit
    ) {}
}
