package com.squad.backend.service;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.constants.InviteChannel;
import com.squad.backend.constants.InvitePurpose;
import com.squad.backend.dto.request.payment.CreateOutstandingInvoiceRequest;
import com.squad.backend.dto.response.payment.CreateOutstandingInvoiceResponse;
import com.squad.backend.dto.response.payment.PaymentInvoiceResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.model.ConfirmationRequest;
import com.squad.backend.model.PaymentInvoice;
import com.squad.backend.model.Player;
import com.squad.backend.model.Session;
import com.squad.backend.repository.ConfirmationRequestRepository;
import com.squad.backend.repository.PaymentInvoiceRepository;
import com.squad.backend.repository.PlayerRepository;
import com.squad.backend.repository.SessionRepository;
import com.squad.backend.security.JwtTokenProvider;
import com.squad.backend.security.TenantScope;
import com.squad.backend.utils.AmountParseUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentInvoiceService {

    private final PaymentInvoiceRepository paymentInvoiceRepository;
    private final ConfirmationRequestRepository confirmationRequestRepository;
    private final PlayerRepository playerRepository;
    private final SessionRepository sessionRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;

    @Autowired
    @Lazy
    private InviteTokenService inviteTokenService;

    @Value("${jwt.invite-expiration:604800000}")
    private long inviteExpirationMs;

    public CreateOutstandingInvoiceResponse createOutstandingInvoice(
            CreateOutstandingInvoiceRequest request,
            Auth auth) {
        String method = request.getCommunicationMethod() != null
                ? request.getCommunicationMethod().trim().toLowerCase()
                : "email";
        if (!"email".equals(method) && !"whatsapp".equals(method)) {
            throw new IllegalArgumentException("communicationMethod must be email or whatsapp");
        }

        Player player = playerRepository.findById(request.getPlayerId())
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.PLAYER_NOT_FOUND));
        if (!Boolean.TRUE.equals(player.getIsActive())) {
            throw new IllegalArgumentException(ErrorMessages.PLAYER_NOT_FOUND);
        }
        if (TenantScope.denyClubScopedEntity(auth, player.getClubId(), player.getSeasonId())) {
            throw new IllegalArgumentException(ErrorMessages.PLAYER_NOT_FOUND);
        }

        List<ConfirmationRequest> pending = confirmationRequestRepository.findByPlayerId(player.getId()).stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .filter(r -> "No".equalsIgnoreCase(r.getPayment()))
                .filter(r -> "Yes".equalsIgnoreCase(r.getPlayerAttendanceResponse()))
                .filter(r -> auth.getClubId().equals(r.getClubId()))
                .filter(r -> auth.getSeasonId() == null || auth.getSeasonId().equals(r.getSeasonId()))
                .collect(Collectors.toList());

        if (pending.isEmpty()) {
            throw new IllegalArgumentException("No pending payments found for this player");
        }

        if ("email".equals(method) && (player.getEmail() == null || player.getEmail().isBlank())) {
            throw new IllegalArgumentException(
                    "Player does not have an email address. Use WhatsApp or update the player profile.");
        }

        List<PaymentInvoice.LineItem> lineItems = buildLineItems(pending);
        double rawTotal = lineItems.stream()
                .mapToDouble(li -> li.getAmount() != null ? li.getAmount() : 0.0)
                .sum();
        if (rawTotal <= 0) {
            throw new IllegalArgumentException("Outstanding total must be greater than zero");
        }
        final double total = roundMoney(rawTotal);

        List<PaymentInvoice> openInvoices = paymentInvoiceRepository
                .findByPlayerIdAndClubIdAndStatus(player.getId(), auth.getClubId(), "PENDING")
                .stream()
                .filter(inv -> auth.getSeasonId() == null || auth.getSeasonId().equals(inv.getSeasonId()))
                .sorted(Comparator.comparing(PaymentInvoice::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        PaymentInvoice reusable = openInvoices.stream()
                .filter(inv -> sameOutstandingLines(inv, lineItems, total))
                .findFirst()
                .orElse(null);

        PaymentInvoice saved;
        boolean reused;
        if (reusable != null) {
            saved = reusable;
            reused = true;
            // Supersede any other open invoices that don't match (cleanup duplicates).
            cancelOtherOpenInvoices(openInvoices, saved.getId());
            saved.setUpdatedAt(Instant.now());
            saved = paymentInvoiceRepository.save(saved);
            log.info("Reusing PENDING invoice {} for player {}", saved.getId(), player.getId());
        } else {
            // Soft settle: freeze a new bundle; cancel previous open invoices for this player.
            cancelOtherOpenInvoices(openInvoices, null);
            PaymentInvoice invoice = new PaymentInvoice();
            invoice.setClubId(auth.getClubId());
            invoice.setSeasonId(auth.getSeasonId());
            invoice.setPlayerId(player.getId());
            invoice.setStatus("PENDING");
            invoice.setTotalAmount(total);
            invoice.setCurrency("GBP");
            invoice.setLineItems(lineItems);
            invoice.setCreatedBy(auth.getId());
            invoice.setCreatedAt(Instant.now());
            invoice.setUpdatedAt(Instant.now());
            saved = paymentInvoiceRepository.save(invoice);
            reused = false;
            log.info("Created new PENDING invoice {} for player {}", saved.getId(), player.getId());
        }

        // Short share link only: /#/i/{code}. entityId = invoice id. createToken revokes prior codes for this invoice.
        InviteTokenService.InviteLinkResult invite = inviteTokenService.createToken(
                InvitePurpose.PAYMENT_INVOICE,
                saved.getId(),
                auth.getClubId(),
                auth.getSeasonId(),
                "whatsapp".equals(method) ? InviteChannel.WHATSAPP : InviteChannel.EMAIL,
                auth.getId());
        String paymentUrl = invite.inviteLink();

        if ("whatsapp".equals(method)) {
            return CreateOutstandingInvoiceResponse.builder()
                    .invoiceId(saved.getId())
                    .paymentUrl(paymentUrl)
                    .totalAmount(saved.getTotalAmount())
                    .lineItemCount(saved.getLineItems() != null ? saved.getLineItems().size() : 0)
                    .communicationMethod("whatsapp")
                    .message(reused
                            ? "Outstanding payment link refreshed — ready to share on WhatsApp"
                            : "Outstanding payment link ready to share on WhatsApp")
                    .build();
        }

        String playerName = ((player.getFirstName() != null ? player.getFirstName() : "")
                + " " + (player.getLastName() != null ? player.getLastName() : "")).trim();
        Map<String, String> templateData = new HashMap<>();
        templateData.put("emailTitle", "Squad STM - Outstanding Payment");
        templateData.put("emailHeading", "Outstanding Payment Invoice");
        templateData.put("emailMessage",
                "You have an outstanding balance of £" + String.format("%.2f", saved.getTotalAmount())
                        + " across " + (saved.getLineItems() != null ? saved.getLineItems().size() : 0)
                        + " session(s). Please review the invoice and complete payment.");
        templateData.put("buttonColor", "#28a745");
        templateData.put("buttonText", "View Invoice & Pay");
        templateData.put("buttonLink", paymentUrl);
        templateData.put("additionalInfo", "Paying this invoice settles the listed outstanding sessions only.");
        templateData.put("footerMessage", "If you believe this was sent in error, contact your club administrator.");

        boolean mailSent = emailService.sendEmail(
                player.getEmail(),
                "Squad STM - Outstanding Payment (£" + String.format("%.2f", saved.getTotalAmount()) + ")",
                "Outstanding payment for " + (playerName.isEmpty() ? "player" : playerName)
                        + ": £" + String.format("%.2f", saved.getTotalAmount())
                        + ". View invoice: " + paymentUrl,
                templateData);
        if (!mailSent) {
            throw new RuntimeException("Mail sending error.");
        }

        return CreateOutstandingInvoiceResponse.builder()
                .invoiceId(saved.getId())
                .paymentUrl(paymentUrl)
                .totalAmount(saved.getTotalAmount())
                .lineItemCount(saved.getLineItems() != null ? saved.getLineItems().size() : 0)
                .communicationMethod("email")
                .message(reused
                        ? "Outstanding payment link refreshed and emailed"
                        : "Outstanding payment invoice sent successfully")
                .build();
    }

    private List<PaymentInvoice.LineItem> buildLineItems(List<ConfirmationRequest> pending) {
        List<PaymentInvoice.LineItem> lineItems = new ArrayList<>();
        for (ConfirmationRequest cr : pending) {
            Session session = sessionRepository.findById(cr.getSessionId()).orElse(null);
            double amount = AmountParseUtils.parseToDoubleSafe(cr.getAmount());
            if (amount <= 0 && session != null) {
                amount = AmountParseUtils.parseToDoubleSafe(session.getPrice());
            }
            PaymentInvoice.LineItem item = new PaymentInvoice.LineItem();
            item.setRequestId(cr.getId());
            item.setSessionId(cr.getSessionId());
            item.setSessionName(session != null ? session.getSessionName() : "Session");
            item.setSessionDate(session != null ? session.getDate() : null);
            item.setAmount(roundMoney(amount));
            lineItems.add(item);
        }
        return lineItems;
    }

    /**
     * Same outstanding freeze fingerprint: same request ids and same total.
     * New unpaid sessions (or paid lines dropped) force a new invoice so soft settle stays correct.
     */
    private boolean sameOutstandingLines(
            PaymentInvoice existing,
            List<PaymentInvoice.LineItem> desiredLines,
            double desiredTotal) {
        if (existing.getLineItems() == null || existing.getLineItems().isEmpty()) {
            return false;
        }
        if (Math.abs(roundMoney(existing.getTotalAmount() != null ? existing.getTotalAmount() : 0.0) - desiredTotal) > 0.009) {
            return false;
        }
        Set<String> existingIds = existing.getLineItems().stream()
                .map(PaymentInvoice.LineItem::getRequestId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> desiredIds = desiredLines.stream()
                .map(PaymentInvoice.LineItem::getRequestId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        return existingIds.equals(desiredIds);
    }

    private void cancelOtherOpenInvoices(List<PaymentInvoice> openInvoices, String keepId) {
        Instant now = Instant.now();
        for (PaymentInvoice inv : openInvoices) {
            if (keepId != null && keepId.equals(inv.getId())) {
                continue;
            }
            inv.setStatus("CANCELLED");
            inv.setUpdatedAt(now);
            paymentInvoiceRepository.save(inv);
            inviteTokenService.revokeActiveTokensForEntity(InvitePurpose.PAYMENT_INVOICE, inv.getId());
            log.info("Cancelled superseded PENDING invoice {}", inv.getId());
        }
    }

    public PaymentInvoiceResponse getInvoiceForPayment(String invoiceId, String token) {
        PaymentInvoice invoice = validateInvoiceAccess(invoiceId, token);
        return toResponse(invoice);
    }

    /** Build a one-line invoice view from a single confirmation request (legacy payment links). */
    public PaymentInvoiceResponse getInvoiceFromConfirmationRequest(String requestId, String token) {
        if (token == null || token.isBlank() || !jwtTokenProvider.validateToken(token)) {
            throw new SecurityException("This Link Is Expired");
        }
        String subject = jwtTokenProvider.getUserIdFromToken(token);
        if (requestId == null || !requestId.equals(subject)) {
            throw new SecurityException("Invalid payment link");
        }

        ConfirmationRequest cr = confirmationRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!Boolean.TRUE.equals(cr.getIsActive())) {
            throw new IllegalArgumentException("Request not found");
        }

        Session session = sessionRepository.findById(cr.getSessionId()).orElse(null);
        Player player = playerRepository.findById(cr.getPlayerId()).orElse(null);
        double amount = AmountParseUtils.parseToDoubleSafe(cr.getAmount());
        if (amount <= 0 && session != null) {
            amount = AmountParseUtils.parseToDoubleSafe(session.getPrice());
        }

        PaymentInvoiceResponse.LineItemResponse line = PaymentInvoiceResponse.LineItemResponse.builder()
                .requestId(cr.getId())
                .sessionId(cr.getSessionId())
                .sessionName(session != null ? session.getSessionName() : "Session")
                .sessionDate(session != null ? session.getDate() : null)
                .amount(amount)
                .build();

        String playerName = player == null ? "Player" : ((player.getFirstName() != null ? player.getFirstName() : "")
                + " " + (player.getLastName() != null ? player.getLastName() : "")).trim();

        return PaymentInvoiceResponse.builder()
                .id(cr.getId())
                .clubId(cr.getClubId())
                .seasonId(cr.getSeasonId())
                .playerId(cr.getPlayerId())
                .playerName(playerName)
                .playerEmail(player != null ? player.getEmail() : null)
                .status("Yes".equalsIgnoreCase(cr.getPayment()) ? "PAID" : "PENDING")
                .totalAmount(roundMoney(amount))
                .currency("GBP")
                .lineItems(List.of(line))
                .alreadyPaid("Yes".equalsIgnoreCase(cr.getPayment()))
                .build();
    }

    public PaymentInvoice validateInvoiceAccess(String invoiceId, String token) {
        if (token == null || token.isBlank() || !jwtTokenProvider.validateToken(token)) {
            throw new SecurityException("This Link Is Expired");
        }
        String subject = jwtTokenProvider.getUserIdFromToken(token);
        if (invoiceId == null || !invoiceId.equals(subject)) {
            throw new SecurityException("Invalid payment link");
        }
        PaymentInvoice invoice = paymentInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        if ("CANCELLED".equalsIgnoreCase(invoice.getStatus())) {
            throw new IllegalArgumentException("This invoice is no longer valid. Ask your club for a new payment link.");
        }
        return invoice;
    }

    public PaymentInvoiceResponse toResponse(PaymentInvoice invoice) {
        Player player = playerRepository.findById(invoice.getPlayerId()).orElse(null);
        String playerName = player == null ? "Player" : ((player.getFirstName() != null ? player.getFirstName() : "")
                + " " + (player.getLastName() != null ? player.getLastName() : "")).trim();

        List<PaymentInvoiceResponse.LineItemResponse> lines = invoice.getLineItems() == null
                ? List.of()
                : invoice.getLineItems().stream()
                .map(li -> PaymentInvoiceResponse.LineItemResponse.builder()
                        .requestId(li.getRequestId())
                        .sessionId(li.getSessionId())
                        .sessionName(li.getSessionName())
                        .sessionDate(li.getSessionDate())
                        .amount(li.getAmount())
                        .build())
                .collect(Collectors.toList());

        return PaymentInvoiceResponse.builder()
                .id(invoice.getId())
                .clubId(invoice.getClubId())
                .seasonId(invoice.getSeasonId())
                .playerId(invoice.getPlayerId())
                .playerName(playerName)
                .playerEmail(player != null ? player.getEmail() : null)
                .status(invoice.getStatus())
                .totalAmount(invoice.getTotalAmount())
                .currency(invoice.getCurrency() != null ? invoice.getCurrency() : "GBP")
                .lineItems(lines)
                .alreadyPaid("PAID".equalsIgnoreCase(invoice.getStatus()))
                .build();
    }

    private static double roundMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
