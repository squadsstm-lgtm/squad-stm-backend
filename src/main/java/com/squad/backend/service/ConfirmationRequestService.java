package com.squad.backend.service;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.constants.InviteChannel;
import com.squad.backend.constants.InvitePurpose;
import com.squad.backend.dto.request.confirmationrequest.SendPaymentRequestRequest;
import com.squad.backend.dto.request.confirmationrequest.UpdatePaymentRequest;
import com.squad.backend.dto.request.finance.CreateTransactionRequest;
import com.squad.backend.dto.response.confirmationrequest.ConfirmationRequestDetailResponse;
import com.squad.backend.dto.response.confirmationrequest.ConfirmationRequestResponse;
import com.squad.backend.dto.response.confirmationrequest.SendPaymentRequestResponse;
import com.squad.backend.dto.response.confirmationrequest.UpdatePaymentResponse;
import com.squad.backend.dto.response.finance.TransactionResponse;
import com.squad.backend.dto.response.session.SendSessionConfirmationsResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.model.Club;
import com.squad.backend.model.ConfirmationRequest;
import com.squad.backend.model.Player;
import com.squad.backend.model.Session;
import com.squad.backend.repository.ClubRepository;
import com.squad.backend.repository.ConfirmationRequestRepository;
import com.squad.backend.repository.PlayerRepository;
import com.squad.backend.repository.SessionRepository;
import com.squad.backend.security.JwtTokenProvider;
import com.squad.backend.utils.AmountParseUtils;
import com.squad.backend.utils.FrontendLinkUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfirmationRequestService {
    private static final Sort DEFAULT_PAGED_SORT = Sort.by(
            Sort.Order.desc("updatedAt"),
            Sort.Order.desc("_id"));

    private final ConfirmationRequestRepository confirmationRequestRepository;
    private final MongoTemplate mongoTemplateClient;
    private final ClubRepository clubRepository;
    private final PlayerRepository playerRepository;
    private final SessionRepository sessionRepository;
    private final com.squad.backend.repository.TeamRepository teamRepository;
    private final FinanceService financeService;
    private final ClubWalletService clubWalletService;
    private final EmailService emailService;
    private final JwtTokenProvider jwtTokenProvider;

    @Autowired
    @Lazy
    private InviteTokenService inviteTokenService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public PendingPaymentsPagedResult getPendingPaymentsPaged(
            String clubId,
            String seasonId,
            String teamId,
            String playerId,
            String sessionId,
            int pageNumber,
            int pageSize) {
        PageSpec pageSpec = toPageSpec(pageNumber, pageSize);

        Criteria criteria = new Criteria()
                .andOperator(
                        Criteria.where("clubId").is(clubId),
                        Criteria.where("seasonId").is(seasonId),
                        Criteria.where("payment").is("No"),
                        Criteria.where("playerAttendanceResponse").is("Yes"),
                        Criteria.where("isActive").is(true)
                );

        criteria = applyOptionalFilters(criteria, teamId, playerId, sessionId);
        Query pageQuery = new Query(criteria).with(pageSpec.pageable());

        List<ConfirmationRequest> pageRequests = mongoTemplateClient.find(pageQuery, ConfirmationRequest.class);
        long totalItems = mongoTemplateClient.count(new Query(criteria), ConfirmationRequest.class);
        int totalPages = calculateTotalPages(totalItems, pageSpec.size());

        return new PendingPaymentsPagedResult(
                toResponseList(pageRequests),
                totalItems,
                totalPages,
                pageSpec.page(),
                pageSpec.size()
        );
    }

    public AllPaymentsPagedResult getAllPaymentsPaged(
            String clubId,
            String seasonId,
            String teamId,
            String playerId,
            String sessionId,
            String paymentStatus,
            int pageNumber,
            int pageSize) {
        PageSpec pageSpec = toPageSpec(pageNumber, pageSize);

        Criteria baseCriteria = new Criteria()
                .andOperator(
                        Criteria.where("clubId").is(clubId),
                        Criteria.where("seasonId").is(seasonId),
                        Criteria.where("playerAttendanceResponse").is("Yes"),
                        Criteria.where("isActive").is(true)
                );

        baseCriteria = applyOptionalFilters(baseCriteria, teamId, playerId, sessionId);

        boolean hasPaymentFilter = hasFilterValue(paymentStatus);
        Criteria criteriaWithPayment = hasPaymentFilter
                ? new Criteria().andOperator(baseCriteria, Criteria.where("payment").is(paymentStatus))
                : baseCriteria;
        Query pageQuery = new Query(criteriaWithPayment).with(pageSpec.pageable());

        List<ConfirmationRequest> pageRequests = mongoTemplateClient.find(pageQuery, ConfirmationRequest.class);
        long totalItems = mongoTemplateClient.count(new Query(criteriaWithPayment), ConfirmationRequest.class);
        int totalPages = calculateTotalPages(totalItems, pageSpec.size());

        // Meta counts/totals must reflect the same filter set the list uses (same as old controller behavior).
        long pendingCount = countByPayment(criteriaWithPayment, "No");
        long paidCount = countByPayment(criteriaWithPayment, "Yes");

        double totalPendingAmount = sumAmountForPayment(criteriaWithPayment, "No");
        double totalPaidAmount = sumAmountForPayment(criteriaWithPayment, "Yes");

        return new AllPaymentsPagedResult(
                toResponseList(pageRequests),
                totalItems,
                totalPages,
                pageSpec.page(),
                pageSpec.size(),
                pendingCount,
                paidCount,
                totalPendingAmount,
                totalPaidAmount
        );
    }

    private Criteria applyOptionalFilters(Criteria criteria, String teamId, String playerId, String sessionId) {
        List<Criteria> andList = new ArrayList<>();
        andList.add(criteria);

        if (hasFilterValue(teamId)) {
            String[] teamIds = teamId.split(",");
            List<Criteria> teamOrList = new ArrayList<>();
            for (String tid : teamIds) {
                if (tid == null || tid.isBlank()) continue;
                // teamId is stored as a string that may contain multiple ids.
                teamOrList.add(Criteria.where("teamId").regex(".*" + Pattern.quote(tid.trim()) + ".*"));
            }
            if (!teamOrList.isEmpty()) {
                andList.add(new Criteria().orOperator(teamOrList.toArray(new Criteria[0])));
            }
        }

        if (hasFilterValue(playerId)) {
            andList.add(Criteria.where("playerId").is(playerId));
        }

        if (hasFilterValue(sessionId)) {
            andList.add(Criteria.where("sessionId").is(sessionId));
        }

        return new Criteria().andOperator(andList.toArray(new Criteria[0]));
    }

    private long countByPayment(Criteria baseCriteria, String paymentValue) {
        Criteria criteria = new Criteria().andOperator(baseCriteria, Criteria.where("payment").is(paymentValue));
        return mongoTemplateClient.count(new Query(criteria), ConfirmationRequest.class);
    }

    private List<ConfirmationRequestResponse> toResponseList(List<ConfirmationRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> clubIds = new HashSet<>();
        Set<String> playerIds = new HashSet<>();
        Set<String> sessionIds = new HashSet<>();
        for (ConfirmationRequest request : requests) {
            if (request.getClubId() != null) clubIds.add(request.getClubId());
            if (request.getPlayerId() != null) playerIds.add(request.getPlayerId());
            if (request.getSessionId() != null) sessionIds.add(request.getSessionId());
        }

        Map<String, String> clubNames = clubRepository.findAllById(clubIds).stream()
                .collect(Collectors.toMap(Club::getId, c -> c.getClubName() != null ? c.getClubName() : "N/A", (a, b) -> a));
        Map<String, String> playerNames = playerRepository.findAllById(playerIds).stream()
                .collect(Collectors.toMap(Player::getId, p -> {
                    String first = p.getFirstName() != null ? p.getFirstName() : "";
                    String last = p.getLastName() != null ? p.getLastName() : "";
                    String full = (first + " " + last).trim();
                    return full.isEmpty() ? "N/A" : full;
                }, (a, b) -> a));
        Map<String, String> sessionNames = sessionRepository.findAllById(sessionIds).stream()
                .collect(Collectors.toMap(Session::getId, s -> s.getSessionName() != null ? s.getSessionName() : "N/A", (a, b) -> a));

        return requests.stream()
                .map(request -> mapToResponse(request, clubNames, playerNames, sessionNames))
                .collect(Collectors.toList());
    }

    private PageSpec toPageSpec(int pageNumber, int pageSize) {
        int safePage = Math.max(1, pageNumber);
        int safeSize = Math.max(1, pageSize);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, DEFAULT_PAGED_SORT);
        return new PageSpec(safePage, safeSize, pageable);
    }

    private int calculateTotalPages(long totalItems, int pageSize) {
        return (int) Math.ceil((double) totalItems / pageSize);
    }

    private boolean hasFilterValue(String value) {
        return value != null && !value.isEmpty() && !"null".equals(value);
    }

    private record PageSpec(int page, int size, Pageable pageable) {
    }

    private double sumAmountForPayment(Criteria criteriaWithOtherFilters, String paymentValue) {
        Criteria sumCriteria = new Criteria().andOperator(
                criteriaWithOtherFilters,
                Criteria.where("payment").is(paymentValue)
        );
        // Project only amount — avoid loading full confirmation-request documents into memory.
        Query sumQuery = new Query(sumCriteria);
        sumQuery.fields().include("amount");
        List<ConfirmationRequest> rows = mongoTemplateClient.find(sumQuery, ConfirmationRequest.class);
        return rows.stream()
                .mapToDouble(r -> AmountParseUtils.parseToDoubleSafe(r.getAmount()))
                .sum();
    }

    public record PendingPaymentsPagedResult(
            List<ConfirmationRequestResponse> requests,
            long totalItems,
            int totalPages,
            int currentPage,
            int pageSize) {
    }

    public record AllPaymentsPagedResult(
            List<ConfirmationRequestResponse> requests,
            long totalItems,
            int totalPages,
            int currentPage,
            int pageSize,
            long pendingCount,
            long paidCount,
            double totalPendingAmount,
            double totalPaidAmount) {
    }

    public List<ConfirmationRequestResponse> getConfirmPayments(String clubId, String seasonId) {
        List<ConfirmationRequest> requests = confirmationRequestRepository
                .findByClubIdAndSeasonId(clubId, seasonId)
                .stream()
                .filter(r -> "Yes".equals(r.getPayment()) && "Yes".equals(r.getPlayerAttendanceResponse()) && Boolean.TRUE.equals(r.getIsActive()))
                .sorted((r1, r2) -> {
                    Instant updatedAt1 = r1.getUpdatedAt() != null ? r1.getUpdatedAt() : Instant.MIN;
                    Instant updatedAt2 = r2.getUpdatedAt() != null ? r2.getUpdatedAt() : Instant.MIN;
                    return updatedAt2.compareTo(updatedAt1);
                })
                .limit(20)
                .collect(Collectors.toList());

        return toResponseList(requests);
    }

    public Integer getTotalPayments(String clubId, String seasonId) {
        List<ConfirmationRequest> requests = confirmationRequestRepository
                .findByClubIdAndSeasonId(clubId, seasonId)
                .stream()
                .filter(r -> "Yes".equals(r.getPayment()) && "Yes".equals(r.getPlayerAttendanceResponse()) && Boolean.TRUE.equals(r.getIsActive()))
                .collect(Collectors.toList());

        int total = 0;
        for (ConfirmationRequest request : requests) {
            try {
                total += Integer.parseInt(request.getAmount());
            } catch (NumberFormatException e) {
                log.warn("Invalid amount format for request {}: {}", request.getId(), request.getAmount());
            }
        }
        return total;
    }

    public ConfirmationRequestDetailResponse getRequestById(String id, String token) {
        ConfirmationRequest request = confirmationRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!Boolean.TRUE.equals(request.getIsActive())) {
            throw new IllegalArgumentException("Request not found");
        }

        if (token != null && !token.isEmpty()) {
            if (!jwtTokenProvider.validateToken(token)) {
                throw new IllegalArgumentException("This Link Is Expired");
            }
        }

        Session session = sessionRepository.findById(request.getSessionId()).orElse(null);
        Player player = playerRepository.findById(request.getPlayerId()).orElse(null);
        if (session == null || !Boolean.TRUE.equals(session.getIsActive())) {
            throw new IllegalArgumentException(ErrorMessages.SESSION_NOT_FOUND);
        }
        if (player == null || !Boolean.TRUE.equals(player.getIsActive())) {
            throw new IllegalArgumentException(ErrorMessages.PLAYER_NOT_FOUND);
        }

        return ConfirmationRequestDetailResponse.builder()
                .session(session)
                .player(player)
                .request(request)
                .build();
    }

    public ConfirmationRequestResponse updateAttendance(String id, String playerAttendanceResponse, String token) {
        if (token == null || token.isBlank() || !jwtTokenProvider.validateToken(token)) {
            throw new SecurityException("This Link Is Expired");
        }
        String tokenSubject = jwtTokenProvider.getUserIdFromToken(token);
        if (!id.equals(tokenSubject)) {
            throw new SecurityException("Invalid attendance link");
        }

        ConfirmationRequest request = confirmationRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!Boolean.TRUE.equals(request.getIsActive())) {
            throw new IllegalArgumentException("Request not found");
        }
        Session session = sessionRepository.findById(request.getSessionId()).orElse(null);
        if (session == null || !Boolean.TRUE.equals(session.getIsActive())) {
            throw new IllegalArgumentException(ErrorMessages.SESSION_NOT_FOUND);
        }
        Player player = playerRepository.findById(request.getPlayerId()).orElse(null);
        if (player == null || !Boolean.TRUE.equals(player.getIsActive())) {
            throw new IllegalArgumentException(ErrorMessages.PLAYER_NOT_FOUND);
        }

        request.setPlayerAttendanceResponse(playerAttendanceResponse);
        ConfirmationRequest saved = confirmationRequestRepository.save(request);
        return mapToResponse(saved);
    }

    public UpdatePaymentResponse updatePayment(UpdatePaymentRequest updateRequest) {
        ConfirmationRequest confirmationRequest = confirmationRequestRepository.findById(updateRequest.getRequestId())
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!Boolean.TRUE.equals(confirmationRequest.getIsActive())) {
            throw new IllegalArgumentException("Request not found");
        }

        Session session = sessionRepository.findById(confirmationRequest.getSessionId()).orElseThrow(
                () -> new IllegalArgumentException("Session not found"));
        if (!Boolean.TRUE.equals(session.getIsActive())) {
            throw new IllegalArgumentException(ErrorMessages.SESSION_NOT_FOUND);
        }
        Player player = playerRepository.findById(confirmationRequest.getPlayerId()).orElse(null);
        if (player == null || !Boolean.TRUE.equals(player.getIsActive())) {
            throw new IllegalArgumentException(ErrorMessages.PLAYER_NOT_FOUND);
        }
        confirmationRequest.setPayment(updateRequest.getPayment());
        ConfirmationRequest updatedRequest = confirmationRequestRepository.save(confirmationRequest);

        UpdatePaymentResponse.TransactionInfo transactionInfo = null;
        UpdatePaymentResponse.WalletInfo walletInfo = null;

        if ("Yes".equals(updateRequest.getPayment())) {
            try {
                Double amount = Double.parseDouble(confirmationRequest.getAmount());
                
                CreateTransactionRequest transactionRequest = new CreateTransactionRequest();
                transactionRequest.setClubId(confirmationRequest.getClubId());
                transactionRequest.setSeasonId(confirmationRequest.getSeasonId());
                transactionRequest.setPlayerId(confirmationRequest.getPlayerId());
                transactionRequest.setSessionId(confirmationRequest.getSessionId());
                transactionRequest.setTeamId(confirmationRequest.getTeamId());
                transactionRequest.setAmount(amount);
                transactionRequest.setCurrency("GBP");
                transactionRequest.setType("payment");
                transactionRequest.setStatus("completed");
                transactionRequest.setPaymentMethod(updateRequest.getPaymentMethod());
                transactionRequest.setStripeTransactionId(updateRequest.getStripeTransactionId());
                transactionRequest.setDescription("Session payment - " + (session.getSessionName() != null ? session.getSessionName() : "Session"));
                transactionRequest.setNotes("Payment confirmed via confirmation request: " + updateRequest.getRequestId());
                
                if (session.getDate() != null && !session.getDate().isEmpty()) {
                    try {
                        java.time.LocalDate sessionDate = java.time.LocalDate.parse(session.getDate());
                        transactionRequest.setSessionDate(sessionDate);
                    } catch (Exception e) {
                        try {
                            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
                            java.time.LocalDate sessionDate = java.time.LocalDate.parse(session.getDate(), formatter);
                            transactionRequest.setSessionDate(sessionDate);
                        } catch (Exception e2) {
                            log.warn("Could not parse session date: {}, using current date", session.getDate());
                            transactionRequest.setSessionDate(java.time.LocalDate.now());
                        }
                    }
                } else {
                    transactionRequest.setSessionDate(java.time.LocalDate.now());
                }

                TransactionResponse transaction = financeService.createTransaction(transactionRequest);

                transactionInfo = UpdatePaymentResponse.TransactionInfo.builder()
                        .id(transaction.getId())
                        .amount(transaction.getAmount())
                        .status(transaction.getStatus())
                        .build();

                com.squad.backend.dto.response.clubwallet.ClubWalletResponse wallet = clubWalletService.getWallet(confirmationRequest.getClubId());
                walletInfo = UpdatePaymentResponse.WalletInfo.builder()
                        .totalEarnings(wallet.getTotalEarnings())
                        .availableForWithdrawal(wallet.getAvailableForWithdrawal())
                        .build();

                log.info("✅ Payment processed: Transaction {} created, Wallet updated for club {}", transaction.getId(), confirmationRequest.getClubId());
            } catch (Exception financialError) {
                log.error("Error creating financial transaction:", financialError);
            }
        }

        return UpdatePaymentResponse.builder()
                .request(updatedRequest)
                .transaction(transactionInfo)
                .wallet(walletInfo)
                .build();
    }

    public SendPaymentRequestResponse sendPaymentRequest(SendPaymentRequestRequest requestData, Auth auth) {
        Session session = sessionRepository.findById(requestData.getSessionId())
                .orElseThrow(() -> new IllegalArgumentException("Session not found for ID: " + requestData.getSessionId()));
        if (!Boolean.TRUE.equals(session.getIsActive())) {
            throw new IllegalArgumentException(ErrorMessages.SESSION_NOT_FOUND);
        }

        Player player = playerRepository.findById(requestData.getPlayerId())
                .orElseThrow(() -> new IllegalArgumentException("Player not found for ID: " + requestData.getPlayerId()));
        if (!Boolean.TRUE.equals(player.getIsActive())) {
            throw new IllegalArgumentException(ErrorMessages.PLAYER_NOT_FOUND);
        }

        ConfirmationRequest existingRequest = confirmationRequestRepository
                .findBySessionIdAndPlayerId(requestData.getSessionId(), requestData.getPlayerId())
                .orElse(null);

        String requestId;
        if (existingRequest == null || !Boolean.TRUE.equals(existingRequest.getIsActive())) {
            ConfirmationRequest newRequest = new ConfirmationRequest();
            newRequest.setClubId(auth.getClubId());
            newRequest.setSeasonId(auth.getSeasonId());
            newRequest.setSessionId(requestData.getSessionId());
            newRequest.setPlayerId(player.getId());
            newRequest.setTeamId(null);
            newRequest.setPlayerAttendanceResponse("confirmed");
            newRequest.setSessionAttendance("pending");
            newRequest.setPayment("No");
            newRequest.setAmount(session.getPrice());
            newRequest.setIsActive(true);
            newRequest.setCreatedBy(auth.getId());
            newRequest.setCreatedAt(Instant.now());
            newRequest.setUpdatedAt(Instant.now());
            ConfirmationRequest saved = confirmationRequestRepository.save(newRequest);
            requestId = saved.getId();
        } else {
            requestId = existingRequest.getId();
            existingRequest.setAmount(session.getPrice());
            existingRequest.setPayment("No");
            existingRequest.setUpdatedAt(Instant.now());
            confirmationRequestRepository.save(existingRequest);
        }

        String method = requestData.getCommunicationMethod() != null
                ? requestData.getCommunicationMethod().trim().toLowerCase()
                : "email";
        boolean isWhatsApp = "whatsapp".equals(method);

        if (isWhatsApp) {
            String phone = player.getPhone();
            if (phone == null || phone.isBlank()) {
                throw new IllegalArgumentException(
                        "Player does not have a phone number for WhatsApp. Use Email or update the player profile.");
            }
        } else if (player.getEmail() == null || player.getEmail().isBlank()) {
            throw new IllegalArgumentException(
                    "Player does not have an email address. Use WhatsApp or update the player profile.");
        }

        // Short invite-token link: /#/i/{code} (same pattern as player WhatsApp invites)
        InviteTokenService.InviteLinkResult inviteLink = inviteTokenService.createToken(
                InvitePurpose.PAYMENT_REQUEST,
                requestId,
                auth.getClubId(),
                auth.getSeasonId(),
                isWhatsApp ? InviteChannel.WHATSAPP : InviteChannel.EMAIL,
                auth.getId());
        String paymentUrl = inviteLink.inviteLink();

        if (isWhatsApp) {
            return SendPaymentRequestResponse.builder()
                    .success(true)
                    .requestId(requestId)
                    .paymentUrl(paymentUrl)
                    .message("Payment link ready to share on WhatsApp")
                    .build();
        }

        Map<String, String> templateData = new HashMap<>();
        templateData.put("emailTitle", "Squad STM - Payment Request");
        templateData.put("emailHeading", "Session Payment Request");
        templateData.put("emailMessage", buildPaymentRequestMessage(auth, session));
        templateData.put("buttonColor", "#28a745");
        templateData.put("buttonText", "Make Payment");
        templateData.put("buttonLink", paymentUrl);
        templateData.put("additionalInfo", "Your prompt response helps with session planning.");
        templateData.put("footerMessage", "If you believe this request was sent in error, please contact your team administrator.");

        boolean mailSent = emailService.sendEmail(
                player.getEmail(),
                "Squad STM - Payment Request",
                "Payment required for session \"" + session.getSessionName() + "\". Amount: " + session.getPrice() + ". Click here to pay: " + paymentUrl,
                templateData
        );

        if (!mailSent) {
            throw new RuntimeException("Mail sending error.");
        }

        return SendPaymentRequestResponse.builder()
                .success(true)
                .requestId(requestId)
                .paymentUrl(paymentUrl)
                .message("Payment request sent successfully")
                .build();
    }

    private String buildPaymentRequestMessage(Auth auth, Session session) {
        StringBuilder message = new StringBuilder();
        message.append((auth.getFirstName() != null ? auth.getFirstName() : "Squad STM"))
                .append(" has requested payment for the session \"")
                .append(session.getSessionName() != null ? session.getSessionName() : "")
                .append("\". Please complete your payment using the link below.")
                .append("<div style=\"margin: 20px 0;\">")
                .append("<strong>Session Details:</strong>")
                .append("<ul style=\"list-style-type: none; padding-left: 0; margin: 10px 0;\">")
                .append("<li style=\"margin: 5px 0;\">• Session Name: ").append(session.getSessionName() != null ? session.getSessionName() : "Not specified").append("</li>")
                .append("<li style=\"margin: 5px 0;\">• Date & Time: ").append(session.getDate() != null ? session.getDate() : "Not specified").append("</li>")
                .append("<li style=\"margin: 5px 0;\">• Type: ").append(session.getSessionType() != null ? session.getSessionType() : "Not specified").append("</li>")
                .append("<li style=\"margin: 5px 0;\">• Location: ").append(session.getLocation() != null ? session.getLocation() : "Not specified").append("</li>")
                .append("<li style=\"margin: 5px 0;\">• Session Fee: ").append(session.getPrice() != null ? session.getPrice() : "Not specified").append("</li>")
                .append("</ul>")
                .append("</div>")
                .append("Please complete your payment as soon as possible.");
        return message.toString();
    }

    public void sendConfirmationRequestsForSession(Session session, Auth auth) {
        List<Player> players = ensureConfirmationRequestsForSession(session, auth);
        emailConfirmationRequests(session, auth, players);
    }

    /**
     * Sends session RSVP invites via email (per-player links) or WhatsApp (one shared short link).
     */
    public SendSessionConfirmationsResponse sendSessionConfirmations(
            Session session,
            String communicationMethod,
            Auth auth) {
        String method = communicationMethod != null ? communicationMethod.trim().toLowerCase() : "";
        if (!"email".equals(method) && !"whatsapp".equals(method)) {
            throw new IllegalArgumentException("communicationMethod must be email or whatsapp");
        }

        List<Player> players = ensureConfirmationRequestsForSession(session, auth);
        if (players.isEmpty()) {
            throw new IllegalArgumentException("No active players found for this session");
        }

        if ("whatsapp".equals(method)) {
            InviteTokenService.InviteLinkResult inviteLink = inviteTokenService.createToken(
                    InvitePurpose.SESSION_CONFIRMATION,
                    session.getId(),
                    auth.getClubId(),
                    auth.getSeasonId(),
                    InviteChannel.WHATSAPP,
                    auth.getId());
            return SendSessionConfirmationsResponse.builder()
                    .communicationMethod("whatsapp")
                    .inviteLink(inviteLink.inviteLink())
                    .playerCount(players.size())
                    .message("Session confirmation link ready to share on WhatsApp")
                    .build();
        }

        List<Player> missingEmail = players.stream()
                .filter(p -> p.getEmail() == null || p.getEmail().isBlank())
                .toList();
        if (!missingEmail.isEmpty()) {
            String names = missingEmail.stream()
                    .map(p -> {
                        String first = p.getFirstName() != null ? p.getFirstName() : "";
                        String last = p.getLastName() != null ? p.getLastName() : "";
                        String full = (first + " " + last).trim();
                        return full.isEmpty() ? p.getId() : full;
                    })
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    "Cannot send email invites. Missing email for: " + names);
        }

        emailConfirmationRequests(session, auth, players);
        return SendSessionConfirmationsResponse.builder()
                .communicationMethod("email")
                .inviteLink(null)
                .playerCount(players.size())
                .message("Session confirmation emails sent successfully")
                .build();
    }

    /**
     * Creates or reuses active ConfirmationRequest rows for every invited player.
     * @return distinct active players that received a request row
     */
    public List<Player> ensureConfirmationRequestsForSession(Session session, Auth auth) {
        String sessionId = session.getId();
        Set<String> allPlayerIds = resolveSessionPlayerIds(session);
        List<Player> players = new ArrayList<>();

        for (String playerId : allPlayerIds) {
            Player player = playerRepository.findById(playerId).orElse(null);
            if (player == null || !Boolean.TRUE.equals(player.getIsActive())) {
                if (player == null) {
                    log.warn("Player not found for ID: {}", playerId);
                }
                continue;
            }

            Optional<ConfirmationRequest> existingRequestOpt = confirmationRequestRepository
                    .findBySessionIdAndPlayerId(sessionId, playerId);

            if (existingRequestOpt.isPresent() && Boolean.TRUE.equals(existingRequestOpt.get().getIsActive())) {
                ConfirmationRequest req = existingRequestOpt.get();
                req.setUpdatedAt(Instant.now());
                confirmationRequestRepository.save(req);
            } else {
                ConfirmationRequest newRequest = new ConfirmationRequest();
                newRequest.setClubId(auth.getClubId());
                newRequest.setSeasonId(auth.getSeasonId());
                newRequest.setSessionId(sessionId);
                newRequest.setPlayerId(player.getId());
                newRequest.setTeamId(null);
                newRequest.setPlayerAttendanceResponse("pending");
                newRequest.setSessionAttendance("pending");
                newRequest.setPayment("No");
                newRequest.setAmount(session.getPrice());
                newRequest.setIsActive(true);
                newRequest.setCreatedBy(auth.getId());
                newRequest.setCreatedAt(Instant.now());
                newRequest.setUpdatedAt(Instant.now());
                confirmationRequestRepository.save(newRequest);
            }
            players.add(player);
        }
        return players;
    }

    private Set<String> resolveSessionPlayerIds(Session session) {
        List<String> teamList = session.getTeamList() != null && !session.getTeamList().isEmpty() ?
                Arrays.asList(session.getTeamList().split(",")) : new ArrayList<>();
        List<String> additionalPlayers = session.getAdditionalPlayers() != null && !session.getAdditionalPlayers().isEmpty() ?
                Arrays.asList(session.getAdditionalPlayers().split(",")) : new ArrayList<>();

        Set<String> allPlayerIds = new HashSet<>();
        for (String teamId : teamList) {
            com.squad.backend.model.Team team = teamRepository.findById(teamId.trim()).orElse(null);
            if (team != null && Boolean.TRUE.equals(team.getIsActive())
                    && team.getPlayersList() != null && !team.getPlayersList().isEmpty()) {
                allPlayerIds.addAll(Arrays.asList(team.getPlayersList().split(",")));
            }
        }
        for (String playerId : additionalPlayers) {
            if (playerId != null && !playerId.isBlank()) {
                allPlayerIds.add(playerId.trim());
            }
        }
        return allPlayerIds;
    }

    private void emailConfirmationRequests(Session session, Auth auth, List<Player> players) {
        try {
            for (Player player : players) {
                ConfirmationRequest request = confirmationRequestRepository
                        .findBySessionIdAndPlayerId(session.getId(), player.getId())
                        .orElse(null);
                if (request == null || !Boolean.TRUE.equals(request.getIsActive())) {
                    continue;
                }

                String requestId = request.getId();
                String verificationToken = jwtTokenProvider.generateToken(requestId);
                String confirmationUrl = FrontendLinkUtils.hashLink(
                        frontendUrl,
                        "confirmation-request/" + requestId + "/" + verificationToken);

                Map<String, String> templateData = new HashMap<>();
                templateData.put("emailTitle", "Squad STM - Session Confirmation Request");
                templateData.put("emailHeading", "Session Confirmation Request");
                templateData.put("emailMessage", buildConfirmationMessage(auth, session));
                templateData.put("buttonColor", "#007bff");
                templateData.put("buttonText", "Confirm Attendance");
                templateData.put("buttonLink", confirmationUrl);
                templateData.put("additionalInfo", "Your prompt response helps with session planning.");
                templateData.put("footerMessage", "If you believe this request was sent in error, please contact your team administrator.");

                boolean mailSent = emailService.sendEmail(
                        player.getEmail(),
                        "Squad STM - Session Confirmation Request",
                        "Confirmation required for session \"" + session.getSessionName()
                                + "\". Click here to confirm: " + confirmationUrl,
                        templateData
                );

                if (!mailSent) {
                    log.error("Failed to send email to player {}", player.getId());
                }
            }
        } catch (Exception e) {
            log.error("Error sending confirmation requests for session: ", e);
            throw new RuntimeException("Error sending confirmation requests", e);
        }
    }

    private String buildConfirmationMessage(Auth auth, Session session) {
        StringBuilder message = new StringBuilder();
        message.append((auth.getFirstName() != null ? auth.getFirstName() : "Squad STM"))
                .append(" has requested your confirmation for the session \"")
                .append(session.getSessionName() != null ? session.getSessionName() : "")
                .append("\". Please confirm your attendance using the link below.")
                .append("<div style=\"margin: 20px 0;\">")
                .append("<strong>Session Details:</strong>")
                .append("<ul style=\"list-style-type: none; padding-left: 0; margin: 10px 0;\">")
                .append("<li style=\"margin: 5px 0;\">• Session Name: ").append(session.getSessionName() != null ? session.getSessionName() : "Not specified").append("</li>")
                .append("<li style=\"margin: 5px 0;\">• Date & Time: ").append(session.getDate() != null ? session.getDate() : "Not specified").append("</li>")
                .append("<li style=\"margin: 5px 0;\">• Type: ").append(session.getSessionType() != null ? session.getSessionType() : "Not specified").append("</li>")
                .append("<li style=\"margin: 5px 0;\">• Location: ").append(session.getLocation() != null ? session.getLocation() : "Not specified").append("</li>")
                .append("<li style=\"margin: 5px 0;\">• Session Fee: ").append(session.getPrice() != null ? session.getPrice() : "Not specified").append("</li>")
                .append("</ul>")
                .append("</div>")
                .append("Please confirm your attendance as soon as possible.");
        return message.toString();
    }

    private ConfirmationRequestResponse mapToResponse(ConfirmationRequest request) {
        return mapToResponse(request, Map.of(), Map.of(), Map.of());
    }

    private ConfirmationRequestResponse mapToResponse(
            ConfirmationRequest request,
            Map<String, String> clubNames,
            Map<String, String> playerNames,
            Map<String, String> sessionNames) {
        String clubName;
        if (!clubNames.isEmpty()) {
            clubName = clubNames.getOrDefault(request.getClubId(), "N/A");
        } else {
            clubName = clubRepository.findById(request.getClubId())
                    .map(Club::getClubName)
                    .orElse("N/A");
        }

        String playerName;
        if (!playerNames.isEmpty()) {
            playerName = playerNames.getOrDefault(request.getPlayerId(), "N/A");
        } else {
            playerName = playerRepository.findById(request.getPlayerId())
                    .map(p -> {
                        String first = p.getFirstName() != null ? p.getFirstName() : "";
                        String last = p.getLastName() != null ? p.getLastName() : "";
                        String full = (first + " " + last).trim();
                        return full.isEmpty() ? "N/A" : full;
                    })
                    .orElse("N/A");
        }

        String sessionName;
        if (!sessionNames.isEmpty()) {
            sessionName = sessionNames.getOrDefault(request.getSessionId(), "N/A");
        } else {
            sessionName = sessionRepository.findById(request.getSessionId())
                    .map(Session::getSessionName)
                    .orElse("N/A");
        }

        return ConfirmationRequestResponse.builder()
                .id(request.getId())
                .clubId(request.getClubId())
                .seasonId(request.getSeasonId())
                .sessionId(request.getSessionId())
                .playerId(request.getPlayerId())
                .teamId(request.getTeamId())
                .playerAttendanceResponse(request.getPlayerAttendanceResponse())
                .sessionAttendance(request.getSessionAttendance())
                .attendanceMarkedBy(request.getAttendanceMarkedBy())
                .attendanceMarkedAt(request.getAttendanceMarkedAt())
                .payment(request.getPayment())
                .amount(request.getAmount())
                .isActive(request.getIsActive())
                .clubName(clubName)
                .playerName(playerName)
                .sessionName(sessionName)
                .build();
    }
}
