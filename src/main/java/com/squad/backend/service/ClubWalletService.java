package com.squad.backend.service;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.constants.WithdrawalStatus;
import com.squad.backend.dto.request.clubwallet.CreateWithdrawalRequest;
import com.squad.backend.dto.request.clubwallet.UpdatePayoutAccountRequest;
import com.squad.backend.dto.request.clubwallet.UpdateWalletRequest;
import com.squad.backend.dto.request.clubwallet.UpdateWithdrawalRequest;
import com.squad.backend.dto.response.clubwallet.ClubWalletResponse;
import com.squad.backend.dto.response.clubwallet.PayoutAccountResponse;
import com.squad.backend.dto.response.clubwallet.WithdrawalRequestResponse;
import com.squad.backend.event.ClubWalletUnlockRequestedEvent;
import com.squad.backend.model.Auth;
import com.squad.backend.model.ClubWallet;
import com.squad.backend.model.ConfirmationRequest;
import com.squad.backend.model.Session;
import com.squad.backend.model.Transaction;
import com.squad.backend.model.WithdrawalRequest;
import com.squad.backend.repository.AuthRepository;
import com.squad.backend.repository.ClubRepository;
import com.squad.backend.repository.ClubWalletRepository;
import com.squad.backend.repository.ConfirmationRequestRepository;
import com.squad.backend.repository.RoleRepository;
import com.squad.backend.repository.SessionRepository;
import com.squad.backend.repository.TransactionRepository;
import com.squad.backend.repository.WithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClubWalletService {
    private static final List<String> WAITING_STATUS_ALIASES = List.of(WithdrawalStatus.WAITING_FOR_APPROVAL, "pending");
    private static final Sort DEFAULT_WITHDRAWAL_PAGE_SORT = Sort.by(
            Sort.Order.desc("requestedAt"),
            Sort.Order.desc("_id")
    );

    private final ClubWalletRepository clubWalletRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final AuthRepository authRepository;
    private final ClubRepository clubRepository;
    private final TransactionRepository transactionRepository;
    private final SessionRepository sessionRepository;
    private final ConfirmationRequestRepository confirmationRequestRepository;
    private final EncryptionService encryptionService;
    private final EmailService emailService;
    private final RoleRepository roleRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    /** Returns wallet summary quickly; eligible unlock runs asynchronously in the background. */
    public ClubWalletResponse getWallet(String clubId) {
        eventPublisher.publishEvent(new ClubWalletUnlockRequestedEvent(clubId));
        ClubWallet wallet = getOrCreateWallet(clubId);
        return mapWalletToResponse(wallet);
    }

    /** Returns payout account. sortCode and accountNumber are display-only (formatted and masked). */
    public PayoutAccountResponse getPayoutAccount(String clubId) {
        ClubWallet wallet = getOrCreateWallet(clubId);
        String sortCodeRaw = encryptionService.decrypt(wallet.getPayoutSortCode());
        String accountNumberRaw = encryptionService.decrypt(wallet.getPayoutAccountNumber());
        return PayoutAccountResponse.builder()
                .accountName(wallet.getPayoutAccountName())
                .sortCode(formatSortCodeDisplay(sortCodeRaw))
                .accountNumber(maskAccountNumberDisplay(accountNumberRaw))
                .build();
    }

    @Transactional
    public PayoutAccountResponse updatePayoutAccount(String clubId, UpdatePayoutAccountRequest request) {
        ClubWallet wallet = getOrCreateWallet(clubId);
        String sortCodeRaw = request.getSortCode() != null ? request.getSortCode().trim() : null;
        String accountNumberRaw = request.getAccountNumber() != null ? request.getAccountNumber().trim() : null;
        wallet.setPayoutAccountName(request.getAccountName() != null ? request.getAccountName().trim() : null);
        // Encrypt when we have real data (UK: 6-digit sort code, 8-digit account number). Never store masked/display.
        if (isRealSortCode(sortCodeRaw)) {
            wallet.setPayoutSortCode(encryptionService.encrypt(normalizeBankValue(sortCodeRaw)));
        }
        if (isRealAccountNumber(accountNumberRaw)) {
            wallet.setPayoutAccountNumber(encryptionService.encrypt(normalizeBankValue(accountNumberRaw)));
        }
        wallet.setLastUpdated(Instant.now());
        clubWalletRepository.save(wallet);
        String sortCodeDisplay = formatSortCodeDisplay(encryptionService.decrypt(wallet.getPayoutSortCode()));
        String accountNumberDisplay = maskAccountNumberDisplay(encryptionService.decrypt(wallet.getPayoutAccountNumber()));
        return PayoutAccountResponse.builder()
                .accountName(wallet.getPayoutAccountName())
                .sortCode(sortCodeDisplay)
                .accountNumber(accountNumberDisplay)
                .build();
    }

    /** UK sort code: 6 digits. Not masked (no *). */
    private static boolean isRealSortCode(String value) {
        if (value == null || value.isEmpty()) return false;
        if (value.contains("*")) return false;
        return value.replaceAll("\\D", "").length() == 6;
    }

    /** UK account number: 8 digits. Not masked (no *). */
    private static boolean isRealAccountNumber(String value) {
        if (value == null || value.isEmpty()) return false;
        if (value.contains("*")) return false;
        return value.replaceAll("\\D", "").length() == 8;
    }

    /** Strip to digits only for storage. */
    private static String normalizeBankValue(String value) {
        if (value == null) return null;
        return value.replaceAll("\\D", "");
    }

    private static String formatSortCodeDisplay(String value) {
        if (value == null || value.isEmpty()) return null;
        String d = value.replaceAll("\\D", "");
        if (d.length() == 6) return d.substring(0, 2) + "-" + d.substring(2, 4) + "-" + d.substring(4, 6);
        if (d.length() >= 2) return "******" + d.substring(d.length() - 2);
        return "****";
    }

    /** Mask for display (any length; last 2 digits shown). */
    private static String maskAccountNumberDisplay(String value) {
        if (value == null || value.isEmpty()) return null;
        String d = value.replaceAll("\\D", "");
        if (d.length() < 2) return "****";
        return "******" + d.substring(d.length() - 2);
    }

    public ClubWallet getOrCreateWallet(String clubId) {
        return clubWalletRepository.findByClubId(clubId)
                .orElseGet(() -> {
                    ClubWallet newWallet = new ClubWallet();
                    newWallet.setClubId(clubId);
                    newWallet.setTotalEarnings(0.0);
                    newWallet.setLockedEarnings(0.0);
                    newWallet.setAvailableForWithdrawal(0.0);
                    newWallet.setTotalWithdrawn(0.0);
                    newWallet.setPendingWithdrawals(0.0);
                    newWallet.setProcessingFees(0.0);
                    newWallet.setMonthlyRevenue(0.0);
                    newWallet.setYearlyRevenue(0.0);
                    newWallet.setTotalRefunds(0.0);
                    newWallet.setLastUpdated(Instant.now());
                    return clubWalletRepository.save(newWallet);
                });
    }

    @Transactional
    public void addEarnings(String clubId, Double amount) {
        ClubWallet wallet = getOrCreateWallet(clubId);
        wallet.setTotalEarnings(wallet.getTotalEarnings() + amount);
        wallet.setLockedEarnings(wallet.getLockedEarnings() + amount);
        wallet.setLastUpdated(Instant.now());
        clubWalletRepository.save(wallet);
    }

    /**
     * Unlocks earnings for sessions that are past and have attendance marked.
     * Triggered when the user opens Club Wallet. All queries scoped by clubId.
     */
    @Transactional
    public void unlockEligibleForClub(String clubId) {
        List<Transaction> locked = transactionRepository.findByClubIdAndTypeAndStatusAndMoneyLockedTrue(
                clubId, "payment", "completed");
        if (locked.isEmpty()) return;

        Set<String> sessionIds = locked.stream()
                .map(Transaction::getSessionId)
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toSet());
        if (sessionIds.isEmpty()) return;

        List<Session> sessions = sessionRepository.findByClubIdAndIdIn(clubId, List.copyOf(sessionIds));
        List<ConfirmationRequest> withAttendance = confirmationRequestRepository
                .findByClubIdAndSessionIdInAndAttendanceMarkedAtNotNull(clubId, List.copyOf(sessionIds));
        Set<String> sessionIdsWithAttendance = withAttendance.stream()
                .map(ConfirmationRequest::getSessionId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        Instant now = Instant.now();
        Set<String> eligibleSessionIds = sessions.stream()
                .filter(s -> {
                    Instant sessionInstant = parseSessionDate(s.getDate());
                    return sessionInstant != null && !sessionInstant.isAfter(now)
                            && sessionIdsWithAttendance.contains(s.getId());
                })
                .map(Session::getId)
                .collect(Collectors.toSet());
        if (eligibleSessionIds.isEmpty()) return;

        List<Transaction> toUnlock = locked.stream()
                .filter(t -> t.getSessionId() != null && eligibleSessionIds.contains(t.getSessionId()))
                .collect(Collectors.toList());
        double totalUnlock = toUnlock.stream()
                .mapToDouble(t -> t.getAmount() != null ? t.getAmount() : 0.0)
                .sum();
        if (totalUnlock <= 0) return;

        for (Transaction t : toUnlock) {
            t.setMoneyLocked(false);
            t.setAvailableForWithdrawal(true);
        }
        transactionRepository.saveAll(toUnlock);

        ClubWallet wallet = getOrCreateWallet(clubId);
        wallet.setLockedEarnings(Math.max(0.0, wallet.getLockedEarnings() - totalUnlock));
        wallet.setAvailableForWithdrawal(wallet.getAvailableForWithdrawal() + totalUnlock);
        wallet.setLastUpdated(Instant.now());
        clubWalletRepository.save(wallet);
    }

    private Instant parseSessionDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            return Instant.parse(dateStr);
        } catch (DateTimeParseException e1) {
            try {
                return ZonedDateTime.parse(dateStr).toInstant();
            } catch (DateTimeParseException e2) {
                try {
                    return LocalDateTime.parse(dateStr).atZone(java.time.ZoneId.systemDefault()).toInstant();
                } catch (DateTimeParseException e3) {
                    return null;
                }
            }
        }
    }

    public PagedWithdrawalResult getWithdrawalsPaged(String clubId, String status, String search, int page, int limit) {
        int safePage = Math.max(1, page);
        int safeLimit = Math.max(1, limit);
        Pageable pageable = PageRequest.of(
                safePage - 1,
                safeLimit,
                DEFAULT_WITHDRAWAL_PAGE_SORT);

        String searchTerm = normalizeSearch(search);
        List<String> matchingCreatedByIds = resolveSearchCreatedByIds(clubId, searchTerm);
        if (hasText(searchTerm) && matchingCreatedByIds.isEmpty()) {
            return new PagedWithdrawalResult(List.of(), safePage, safeLimit, 0, 0);
        }

        Page<WithdrawalRequest> pageData = fetchWithdrawalsPage(clubId, status, matchingCreatedByIds, pageable);
        Map<String, String> authIdToDisplayName = buildAuthDisplayNameMap(pageData.getContent());
        List<WithdrawalRequestResponse> rows = pageData.getContent().stream()
                .map(w -> mapToWithdrawalResponse(w, authIdToDisplayName))
                .collect(Collectors.toList());

        return new PagedWithdrawalResult(
                rows,
                safePage,
                safeLimit,
                pageData.getTotalElements(),
                pageData.getTotalPages());
    }

    private Page<WithdrawalRequest> fetchWithdrawalsPage(
            String clubId,
            String status,
            List<String> matchingCreatedByIds,
            Pageable pageable) {
        boolean hasSearchIds = matchingCreatedByIds != null;
        boolean hasStatus = hasText(status);

        if (!hasStatus) {
            return hasSearchIds
                    ? withdrawalRequestRepository.findByClubIdAndCreatedByIn(clubId, matchingCreatedByIds, pageable)
                    : withdrawalRequestRepository.findByClubId(clubId, pageable);
        }

        if (WithdrawalStatus.WAITING_FOR_APPROVAL.equals(status)) {
            return hasSearchIds
                    ? withdrawalRequestRepository.findByClubIdAndStatusInAndCreatedByIn(clubId, WAITING_STATUS_ALIASES, matchingCreatedByIds, pageable)
                    : withdrawalRequestRepository.findByClubIdAndStatusIn(clubId, WAITING_STATUS_ALIASES, pageable);
        }

        return hasSearchIds
                ? withdrawalRequestRepository.findByClubIdAndStatusAndCreatedByIn(clubId, status, matchingCreatedByIds, pageable)
                : withdrawalRequestRepository.findByClubIdAndStatus(clubId, status, pageable);
    }

    private Map<String, String> buildAuthDisplayNameMap(List<WithdrawalRequest> list) {
        List<String> createdByIds = list.stream()
                .map(WithdrawalRequest::getCreatedBy)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        Map<String, String> authIdToDisplayName = new HashMap<>();
        if (!createdByIds.isEmpty()) {
            authRepository.findAllById(createdByIds).forEach(auth ->
                    authIdToDisplayName.put(auth.getId(), formatAuthDisplayName(auth)));
        }
        return authIdToDisplayName;
    }

    private boolean matchesAuthSearch(Auth a, String searchLower) {
        if (a == null) return false;
        String first = a.getFirstName() != null ? a.getFirstName().toLowerCase() : "";
        String last = a.getLastName() != null ? a.getLastName().toLowerCase() : "";
        String fullName = (first + " " + last).trim();
        return first.contains(searchLower)
                || last.contains(searchLower)
                || fullName.contains(searchLower);
    }

    private String normalizeSearch(String search) {
        if (search == null) return "";
        String normalized = search.trim();
        return normalized.length() >= 2 ? normalized : "";
    }

    private List<String> resolveSearchCreatedByIds(String clubId, String searchTerm) {
        if (!hasText(searchTerm)) return null;
        String searchLower = searchTerm.toLowerCase();
        return authRepository.findByClubId(clubId).stream()
                .filter(a -> !Boolean.TRUE.equals(a.getIsBlocked()))
                .filter(a -> matchesAuthSearch(a, searchLower))
                .map(Auth::getId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    public WithdrawalRequestResponse getWithdrawalDetail(String clubId, String withdrawalId) {
        WithdrawalRequest w = withdrawalRequestRepository.findById(withdrawalId)
                .filter(wr -> clubId.equals(wr.getClubId()))
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal request not found"));
        return mapToWithdrawalResponse(w);
    }

    /**
     * Returns full bank account details for a withdrawal for Controller use (e.g. to make the payment).
     * Sort code and account number are decrypted and returned in full; account name as stored.
     */
    public Map<String, String> getWithdrawalAccountDetailsForController(String withdrawalId) {
        WithdrawalRequest w = withdrawalRequestRepository.findById(withdrawalId)
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal request not found"));
        if (w.getBankAccount() == null) {
            return Map.of("accountName", "", "sortCode", "", "accountNumber", "");
        }
        String sortCodeRaw = encryptionService.decrypt(w.getBankAccount().getSortCode());
        String accountNumberRaw = encryptionService.decrypt(w.getBankAccount().getAccountNumber());
        String sortCodeFormatted = sortCodeRaw != null && sortCodeRaw.replaceAll("\\D", "").length() == 6
                ? formatSortCodeDisplay(sortCodeRaw)
                : (sortCodeRaw != null ? sortCodeRaw : "");
        String accountNumber = accountNumberRaw != null ? normalizeBankValue(accountNumberRaw) : "";
        String accountName = w.getBankAccount().getAccountName() != null ? w.getBankAccount().getAccountName() : "";
        return Map.of("accountName", accountName, "sortCode", sortCodeFormatted, "accountNumber", accountNumber);
    }

    public PagedControllerWithdrawalResult getWithdrawalsForControllerPaged(String status, int page, int limit) {
        int safePage = Math.max(1, page);
        int safeLimit = Math.max(1, limit);
        Pageable pageable = PageRequest.of(safePage - 1, safeLimit, DEFAULT_WITHDRAWAL_PAGE_SORT);

        Page<WithdrawalRequest> pageData = hasText(status)
                ? withdrawalRequestRepository.findByStatus(status, pageable)
                : withdrawalRequestRepository.findAll(pageable);

        Set<String> clubIds = pageData.getContent().stream()
                .map(WithdrawalRequest::getClubId)
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toSet());
        Map<String, String> clubIdToName = new HashMap<>();
        if (!clubIds.isEmpty()) {
            clubRepository.findAllById(clubIds).forEach(c -> clubIdToName.put(c.getId(), c.getClubName()));
        }

        Map<String, String> authIdToDisplayName = buildAuthDisplayNameMap(pageData.getContent());
        List<WithdrawalRequestResponse> rows = pageData.getContent().stream()
                .map(w -> {
                    WithdrawalRequestResponse r = mapToWithdrawalResponse(w, authIdToDisplayName);
                    r.setClubName(clubIdToName.get(w.getClubId()));
                    return r;
                })
                .collect(Collectors.toList());

        return new PagedControllerWithdrawalResult(
                rows,
                safePage,
                safeLimit,
                pageData.getTotalElements(),
                pageData.getTotalPages());
    }

    public Map<String, Object> getWithdrawalSummary(String clubId, Instant rangeStart, Instant rangeEnd) {
        List<WithdrawalRequest> list = withdrawalRequestRepository.findByClubId(clubId);
        if (rangeStart != null || rangeEnd != null) {
            list = list.stream()
                    .filter(w -> {
                        Instant ts = w.getRequestedAt() != null ? w.getRequestedAt() : w.getCreatedAt();
                        if (ts == null) return false;
                        if (rangeStart != null && ts.isBefore(rangeStart)) return false;
                        if (rangeEnd != null && ts.isAfter(rangeEnd)) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        }

        return list.stream().collect(Collectors.groupingBy(
                w -> w.getStatus() != null ? w.getStatus() : "unknown",
                Collectors.collectingAndThen(Collectors.toList(), grouped -> {
                    Map<String, Object> summary = new HashMap<>();
                    summary.put("count", grouped.size());
                    summary.put("totalAmount", grouped.stream()
                            .mapToDouble(w -> w.getAmount() != null ? w.getAmount() : 0.0)
                            .sum());
                    summary.put("totalFees", grouped.stream()
                            .mapToDouble(w -> w.getProcessingFee() != null ? w.getProcessingFee() : 0.0)
                            .sum());
                    return summary;
                })
        ));
    }

    public WithdrawalRequestResponse updateWithdrawalStatusByController(String withdrawalId, UpdateWithdrawalRequest request) {
        WithdrawalRequest withdrawal = withdrawalRequestRepository.findById(withdrawalId)
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal request not found"));
        return updateWithdrawal(withdrawal.getClubId(), withdrawalId, request);
    }

    @Transactional
    public ClubWalletResponse updateWallet(String clubId, UpdateWalletRequest request) {
        ClubWallet wallet = getOrCreateWallet(clubId);

        if (request.getTotalEarnings() != null) {
            wallet.setTotalEarnings(request.getTotalEarnings());
        }
        if (request.getLockedEarnings() != null) {
            wallet.setLockedEarnings(request.getLockedEarnings());
        }
        if (request.getAvailableForWithdrawal() != null) {
            wallet.setAvailableForWithdrawal(request.getAvailableForWithdrawal());
        }
        if (request.getTotalWithdrawn() != null) {
            wallet.setTotalWithdrawn(request.getTotalWithdrawn());
        }
        if (request.getPendingWithdrawals() != null) {
            wallet.setPendingWithdrawals(request.getPendingWithdrawals());
        }
        if (request.getProcessingFees() != null) {
            wallet.setProcessingFees(request.getProcessingFees());
        }
        if (request.getMonthlyRevenue() != null) {
            wallet.setMonthlyRevenue(request.getMonthlyRevenue());
        }
        if (request.getYearlyRevenue() != null) {
            wallet.setYearlyRevenue(request.getYearlyRevenue());
        }
        if (request.getTotalRefunds() != null) {
            wallet.setTotalRefunds(request.getTotalRefunds());
        }

        wallet.setLastUpdated(Instant.now());
        wallet = clubWalletRepository.save(wallet);
        return mapWalletToResponse(wallet);
    }

    @Transactional
    public WithdrawalRequestResponse updateWithdrawal(String clubId, String withdrawalId, UpdateWithdrawalRequest request) {
        WithdrawalRequest withdrawal = withdrawalRequestRepository.findById(withdrawalId)
                .filter(w -> clubId.equals(w.getClubId()))
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal request not found"));

        String previousStatus = withdrawal.getStatus();
        boolean wasFinal = WithdrawalStatus.COMPLETED.equals(previousStatus) || WithdrawalStatus.FAILED.equals(previousStatus);

        if (request.getStatus() != null) {
            if (WithdrawalStatus.ALL.contains(request.getStatus())) {
                withdrawal.setStatus(request.getStatus());
            }
        }
        if (request.getProcessedBy() != null) {
            withdrawal.setProcessedBy(request.getProcessedBy());
        }
        if (request.getNotes() != null) {
            withdrawal.setNotes(request.getNotes());
        }

        if (WithdrawalStatus.COMPLETED.equals(withdrawal.getStatus()) || WithdrawalStatus.FAILED.equals(withdrawal.getStatus())) {
            withdrawal.setProcessedAt(Instant.now());
        }

        withdrawal = withdrawalRequestRepository.save(withdrawal);

        // When transitioning to COMPLETED or FAILED, update club wallet (pending → total withdrawn or back to available)
        if (!wasFinal) {
            String newStatus = withdrawal.getStatus();
            Double amount = withdrawal.getAmount() != null ? withdrawal.getAmount() : 0.0;
            if (amount > 0 && WithdrawalStatus.COMPLETED.equals(newStatus)) {
                ClubWallet wallet = getOrCreateWallet(clubId);
                wallet.setPendingWithdrawals(Math.max(0.0, wallet.getPendingWithdrawals() - amount));
                wallet.setTotalWithdrawn(wallet.getTotalWithdrawn() + amount);
                wallet.setLastUpdated(Instant.now());
                clubWalletRepository.save(wallet);
            } else if (amount > 0 && WithdrawalStatus.FAILED.equals(newStatus)) {
                ClubWallet wallet = getOrCreateWallet(clubId);
                wallet.setPendingWithdrawals(Math.max(0.0, wallet.getPendingWithdrawals() - amount));
                wallet.setAvailableForWithdrawal(wallet.getAvailableForWithdrawal() + amount);
                wallet.setLastUpdated(Instant.now());
                clubWalletRepository.save(wallet);
            }
        }

        // Notify Admin and Treasurer when withdrawal is completed or failed
        if (!wasFinal && (WithdrawalStatus.COMPLETED.equals(withdrawal.getStatus()) || WithdrawalStatus.FAILED.equals(withdrawal.getStatus()))) {
            try {
                sendWithdrawalCompletedOrFailedEmails(withdrawal, WithdrawalStatus.COMPLETED.equals(withdrawal.getStatus()));
            } catch (Exception e) {
                log.warn("Failed to send withdrawal completed/failed emails: {}", e.getMessage());
            }
        }

        return mapToWithdrawalResponse(withdrawal);
    }

    @Transactional
    public WithdrawalRequestResponse createWithdrawal(CreateWithdrawalRequest request, Auth auth) {
        // Ensure any newly eligible earnings are unlocked before balance check.
        unlockEligibleForClub(request.getClubId());
        ClubWallet wallet = getOrCreateWallet(request.getClubId());

        if (wallet.getAvailableForWithdrawal() < request.getAmount()) {
            throw new IllegalArgumentException(ErrorMessages.INSUFFICIENT_BALANCE);
        }

        WithdrawalRequest withdrawal = new WithdrawalRequest();
        mapToModel(request, withdrawal, wallet);

        if (withdrawal.getBankAccount() == null || withdrawal.getBankAccount().getAccountName() == null
                || withdrawal.getBankAccount().getSortCode() == null || withdrawal.getBankAccount().getAccountNumber() == null) {
            throw new IllegalArgumentException("Payout account is required. Set your bank details in Club Wallet first.");
        }

        withdrawal.setCurrency(request.getCurrency() != null ? request.getCurrency() : "GBP");
        boolean requesterIsAdmin = auth.getRole() != null && auth.getRole().equalsIgnoreCase("Admin");
        List<Auth> verifiers = getVerifierAuths(request.getClubId(), requesterIsAdmin);
        withdrawal.setStatus(verifiers.isEmpty() ? WithdrawalStatus.VERIFIED : WithdrawalStatus.WAITING_FOR_APPROVAL);
        withdrawal.setCreatedBy(auth.getId());
        withdrawal.setRequestedAt(Instant.now());

        withdrawal = withdrawalRequestRepository.save(withdrawal);

        wallet.setPendingWithdrawals(wallet.getPendingWithdrawals() + request.getAmount());
        wallet.setAvailableForWithdrawal(Math.max(0.0, wallet.getAvailableForWithdrawal() - request.getAmount()));
        wallet.setLastUpdated(Instant.now());
        clubWalletRepository.save(wallet);

        sendWithdrawalEmails(withdrawal, auth, verifiers);
        return mapToWithdrawalResponse(withdrawal);
    }

    private List<Auth> getVerifierAuths(String clubId, boolean requesterIsAdmin) {
        String roleName = requesterIsAdmin ? "Treasurer" : "Admin";
        return roleRepository.findByNameIgnoreCaseAndClubId(roleName, clubId)
                .map(role -> authRepository.findByClubIdAndRoleId(clubId, role.getId()))
                .orElse(List.of());
    }

    /** Returns all Auth users who have Admin or Treasurer role for the club (for completed/failed notification). */
    private List<Auth> getAdminAndTreasurerAuths(String clubId) {
        Set<String> seenIds = new LinkedHashSet<>();
        List<Auth> result = new ArrayList<>();
        for (String roleName : List.of("Admin", "Treasurer")) {
            roleRepository.findByNameIgnoreCaseAndClubId(roleName, clubId)
                    .filter(role -> Boolean.TRUE.equals(role.getIsActive()))
                    .map(role -> authRepository.findByClubIdAndRoleId(clubId, role.getId()))
                    .orElse(List.of())
                    .stream()
                    .filter(a -> !Boolean.TRUE.equals(a.getIsBlocked()))
                    .filter(a -> a.getId() != null && seenIds.add(a.getId()))
                    .forEach(result::add);
        }
        return result;
    }

    private void sendWithdrawalCompletedOrFailedEmails(WithdrawalRequest withdrawal, boolean completed) {
        String clubId = withdrawal.getClubId();
        List<Auth> recipients = getAdminAndTreasurerAuths(clubId);
        if (recipients.isEmpty()) return;

        String requesterName = resolveCreatedByName(withdrawal.getCreatedBy());
        if (requesterName == null || requesterName.isEmpty()) requesterName = "A user";
        String amountStr = String.format("%.2f", withdrawal.getAmount() != null ? withdrawal.getAmount() : 0.0);
        String currency = withdrawal.getCurrency() != null ? withdrawal.getCurrency() : "GBP";
        String accountName = withdrawal.getBankAccount() != null && withdrawal.getBankAccount().getAccountName() != null
                ? withdrawal.getBankAccount().getAccountName() : "—";
        String note = withdrawal.getNotes() != null && !withdrawal.getNotes().trim().isEmpty()
                ? withdrawal.getNotes().trim()
                : "No note.";
        String requestedDateStr = withdrawal.getRequestedAt() != null
                ? DateTimeFormatter.ofPattern("d MMM yyyy").format(withdrawal.getRequestedAt().atZone(ZoneId.systemDefault()))
                : "—";

        String subject = completed ? "Squad STM – Withdrawal completed" : "Squad STM – Withdrawal failed";

        // HTML body (used inside the shared email template; template already includes \"Hello,\")
        StringBuilder html = new StringBuilder();
        html.append("<p>A withdrawal request has been ")
                .append(completed ? "<strong>completed successfully</strong>." : "<strong>marked as failed</strong>.")
                .append("</p>");

        html.append("<p><strong>Details</strong><br>")
                .append("- <strong>Requested by</strong>: ").append(escapeForHtml(requesterName)).append("<br>")
                .append("- <strong>Amount</strong>: £").append(amountStr).append(" ").append(escapeForHtml(currency)).append("<br>")
                .append("- <strong>Requested on</strong>: ").append(escapeForHtml(requestedDateStr)).append("<br>")
                .append("- <strong>Account holder</strong>: ").append(escapeForHtml(accountName))
                .append("</p>");

        if (completed) {
            html.append("<p>This amount has been <strong>successfully transferred</strong> to the account in the name of <strong>")
                    .append(escapeForHtml(accountName)).append("</strong>.</p>");
        } else {
            html.append("<p>This withdrawal <strong>could not be completed</strong> (marked as failed). ")
                    .append("The amount has been returned to <strong>Available for withdrawal</strong> in Club Wallet.</p>");
        }

        html.append("<p><strong>Note</strong><br>")
                .append(escapeForHtml(note))
                .append("</p>");

        String emailMessageHtml = html.toString();

        // Plain-text body (fallback) – keep same structure, but without the extra \"Hello\" (template adds it for HTML only)
        StringBuilder text = new StringBuilder();
        text.append("A withdrawal request has been ")
                .append(completed ? "completed successfully.\n\n" : "marked as failed.\n\n");
        text.append("Details\n");
        text.append("- Requested by: ").append(requesterName).append("\n");
        text.append("- Amount: £").append(amountStr).append(" ").append(currency).append("\n");
        text.append("- Requested on: ").append(requestedDateStr).append("\n");
        text.append("- Account holder: ").append(accountName).append("\n\n");
        if (completed) {
            text.append("This amount has been successfully transferred to the account in the name of ")
                    .append(accountName).append(".\n\n");
        } else {
            text.append("This withdrawal could not be completed (marked as failed). ")
                    .append("The amount has been returned to Available for withdrawal in Club Wallet.\n\n");
        }
        text.append("Note\n");
        text.append(note).append("\n");

        Map<String, String> data = new HashMap<>();
        data.put("emailTitle", subject);
        data.put("emailHeading", completed ? "Withdrawal completed" : "Withdrawal failed");
        data.put("emailMessage", emailMessageHtml);
        data.put("buttonText", "View Club Wallet");
        data.put("buttonLink", frontendUrl + "#/club-wallet");
        data.put("buttonColor", "#007bff");
        data.put("footerMessage", "Squad STM");

        String plainText = text.toString();

        for (Auth recipient : recipients) {
            if (recipient.getEmail() != null && !recipient.getEmail().trim().isEmpty()) {
                try {
                    emailService.sendEmail(recipient.getEmail(), subject, plainText, data);
                } catch (Exception e) {
                    log.warn("Failed to send withdrawal completed/failed email to {}: {}", recipient.getEmail(), e.getMessage());
                }
            }
        }
    }

    /** Very small HTML escaper for dynamic text parts used inside emailMessage. */
    private String escapeForHtml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private List<Auth> getOtherClubUsers(String clubId, String excludeAuthId) {
        return authRepository.findByClubId(clubId).stream()
                .filter(a -> !Boolean.TRUE.equals(a.getIsBlocked()))
                .filter(a -> !a.getId().equals(excludeAuthId))
                .collect(Collectors.toList());
    }

    private void sendWithdrawalEmails(WithdrawalRequest withdrawal, Auth requester, List<Auth> verifiers) {
        String clubId = withdrawal.getClubId();
        String requesterName = formatAuthDisplayName(requester);
        String amountStr = String.format("%.2f", withdrawal.getAmount());
        String currency = withdrawal.getCurrency() != null ? withdrawal.getCurrency() : "GBP";
        String accountName = withdrawal.getBankAccount() != null ? withdrawal.getBankAccount().getAccountName() : "";
        if (accountName == null) accountName = "";
        String clubName = clubRepository.findById(clubId).map(c -> c.getClubName()).orElse("Club");

        if (!verifiers.isEmpty()) {
            Map<String, String> verifyData = new HashMap<>();
            verifyData.put("emailTitle", "Squad STM - Withdrawal verification");
            verifyData.put("emailHeading", "Withdrawal request pending verification");
            verifyData.put("emailMessage", "<strong>" + requesterName + "</strong> requested a withdrawal of <strong>£" + amountStr + " " + currency + "</strong>. Account name: <strong>" + accountName + "</strong>. Please log in to verify the request.");
            verifyData.put("buttonText", "Open Club Wallet");
            verifyData.put("buttonLink", frontendUrl + "#/club-wallet");
            verifyData.put("buttonColor", "#007bff");
            verifyData.put("footerMessage", "Squad STM");
            for (Auth verifier : verifiers) {
                if (verifier.getEmail() != null && !verifier.getEmail().isEmpty()) {
                    try {
                        emailService.sendEmail(verifier.getEmail(), "Squad STM - Withdrawal request pending verification",
                                "Please verify the withdrawal request: " + frontendUrl + "#/club-wallet", verifyData);
                    } catch (Exception e) {
                        log.warn("Failed to send verification email to {}: {}", verifier.getEmail(), e.getMessage());
                    }
                }
            }
        }

        List<Auth> infoRecipients = getOtherClubUsers(clubId, requester.getId());
        String statusLabel = verifiers.isEmpty() ? "Verified" : "Waiting for approval";
        Map<String, String> infoData = new HashMap<>();
        infoData.put("emailTitle", "Squad STM - Withdrawal request");
        infoData.put("emailHeading", "Withdrawal request");
        infoData.put("emailMessage", "<strong>" + clubName + "</strong>: <strong>" + requesterName + "</strong> requested withdrawal of <strong>£" + amountStr + " " + currency + "</strong> on the account name of <strong>" + accountName + "</strong>. Status: <strong>" + statusLabel + "</strong>.");
        infoData.put("buttonText", "View Club Wallet");
        infoData.put("buttonLink", frontendUrl + "#/club-wallet");
        infoData.put("buttonColor", "#007bff");
        infoData.put("footerMessage", "Squad STM");
        for (Auth recipient : infoRecipients) {
            if (recipient.getEmail() != null && !recipient.getEmail().isEmpty()) {
                try {
                    emailService.sendEmail(recipient.getEmail(), "Squad STM - Withdrawal request", requesterName + " requested withdrawal of £" + amountStr + " for account " + accountName, infoData);
                } catch (Exception e) {
                    log.warn("Failed to send information email to {}: {}", recipient.getEmail(), e.getMessage());
                }
            }
        }
    }

    private void mapToModel(CreateWithdrawalRequest request, WithdrawalRequest withdrawal, ClubWallet wallet) {
        withdrawal.setClubId(request.getClubId());
        withdrawal.setAmount(request.getAmount());
        WithdrawalRequest.BankAccount bank = request.getBankAccount();
        if (bank != null && isRealSortCode(bank.getSortCode()) && isRealAccountNumber(bank.getAccountNumber())) {
            WithdrawalRequest.BankAccount enc = new WithdrawalRequest.BankAccount();
            enc.setAccountName(bank.getAccountName());
            enc.setSortCode(encryptionService.encrypt(normalizeBankValue(bank.getSortCode())));
            enc.setAccountNumber(encryptionService.encrypt(normalizeBankValue(bank.getAccountNumber())));
            withdrawal.setBankAccount(enc);
            return;
        }
        if (wallet.getPayoutSortCode() != null && wallet.getPayoutAccountNumber() != null) {
            WithdrawalRequest.BankAccount fromWallet = new WithdrawalRequest.BankAccount();
            fromWallet.setAccountName(wallet.getPayoutAccountName());
            fromWallet.setSortCode(wallet.getPayoutSortCode());
            fromWallet.setAccountNumber(wallet.getPayoutAccountNumber());
            withdrawal.setBankAccount(fromWallet);
        } else {
            withdrawal.setBankAccount(request.getBankAccount());
        }
    }

    public ClubWalletResponse mapWalletToResponse(ClubWallet wallet) {
        return ClubWalletResponse.builder()
                .id(wallet.getId())
                .clubId(wallet.getClubId())
                .totalEarnings(wallet.getTotalEarnings())
                .lockedEarnings(wallet.getLockedEarnings())
                .availableForWithdrawal(wallet.getAvailableForWithdrawal())
                .totalWithdrawn(wallet.getTotalWithdrawn())
                .pendingWithdrawals(wallet.getPendingWithdrawals())
                .processingFees(wallet.getProcessingFees())
                .monthlyRevenue(wallet.getMonthlyRevenue())
                .yearlyRevenue(wallet.getYearlyRevenue())
                .totalRefunds(wallet.getTotalRefunds())
                .build();
    }

    private WithdrawalRequestResponse mapToWithdrawalResponse(WithdrawalRequest w) {
        return mapToWithdrawalResponse(w, null);
    }

    /**
     * Maps a withdrawal to response. When createdByNameMap is non-null (e.g. from getWithdrawals batch),
     * uses it to resolve createdByName in one go; otherwise resolves with a single findById.
     */
    private WithdrawalRequestResponse mapToWithdrawalResponse(WithdrawalRequest w, Map<String, String> createdByNameMap) {
        WithdrawalRequestResponse.BankAccountInfo bankInfo = null;
        if (w.getBankAccount() != null) {
            String sortCodeDisplay = formatSortCodeDisplay(encryptionService.decrypt(w.getBankAccount().getSortCode()));
            String accountNumberDisplay = maskAccountNumberDisplay(encryptionService.decrypt(w.getBankAccount().getAccountNumber()));
            bankInfo = new WithdrawalRequestResponse.BankAccountInfo(
                    accountNumberDisplay,
                    sortCodeDisplay,
                    w.getBankAccount().getAccountName());
        }
        String createdByName = createdByNameMap != null
                ? createdByNameMap.get(w.getCreatedBy())
                : resolveCreatedByName(w.getCreatedBy());
        return WithdrawalRequestResponse.builder()
                .id(w.getId())
                .clubId(w.getClubId())
                .amount(w.getAmount())
                .currency(w.getCurrency())
                .status(w.getStatus())
                .processingFee(w.getProcessingFee())
                .netAmount(w.getNetAmount())
                .bankAccount(bankInfo)
                .requestedAt(w.getRequestedAt())
                .estimatedCompletion(w.getEstimatedCompletion())
                .processedAt(w.getProcessedAt())
                .bankReference(w.getBankReference())
                .stripeTransferId(w.getStripeTransferId())
                .description(w.getDescription())
                .notes(w.getNotes())
                .createdBy(w.getCreatedBy())
                .createdByName(createdByName)
                .processedBy(w.getProcessedBy())
                .createdAt(w.getCreatedAt())
                .updatedAt(w.getUpdatedAt())
                .build();
    }

    /** Resolve display name for createdBy (auth id): firstName + lastName, or userName, or null. */
    private String resolveCreatedByName(String authId) {
        if (authId == null) return null;
        return authRepository.findById(authId)
                .map(this::formatAuthDisplayName)
                .orElse(null);
    }

    private String formatAuthDisplayName(Auth auth) {
        String first = auth.getFirstName() != null ? auth.getFirstName().trim() : "";
        String last = auth.getLastName() != null ? auth.getLastName().trim() : "";
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) return full;
        return auth.getUserName() != null ? auth.getUserName().trim() : null;
    }

    public record PagedWithdrawalResult(
            List<WithdrawalRequestResponse> withdrawals,
            int page,
            int limit,
            long total,
            int pages) {
    }

    public record PagedControllerWithdrawalResult(
            List<WithdrawalRequestResponse> withdrawals,
            int page,
            int limit,
            long total,
            int pages) {
    }
}
