package com.squad.backend.service;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.constants.InviteChannel;
import com.squad.backend.constants.InvitePurpose;
import com.squad.backend.dto.request.masterpanel.CompleteControllerInviteRequest;
import com.squad.backend.dto.request.masterpanel.CreateControllerRequest;
import com.squad.backend.dto.request.masterpanel.InviteControllerRequest;
import com.squad.backend.dto.request.masterpanel.UpdateControllerDetailsRequest;
import com.squad.backend.dto.request.masterpanel.UpdateControllerPermissionsRequest;
import com.squad.backend.dto.response.masterpanel.ControllerInviteResponse;
import com.squad.backend.dto.response.masterpanel.ControllerListItemResponse;
import com.squad.backend.dto.response.masterpanel.ControllerPermissionsResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.model.ControllerPermissions;
import com.squad.backend.model.InviteToken;
import com.squad.backend.model.Role;
import com.squad.backend.repository.AuthRepository;
import com.squad.backend.repository.ControllerPermissionsRepository;
import com.squad.backend.repository.InviteTokenRepository;
import com.squad.backend.repository.RoleRepository;
import com.squad.backend.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ControllerPermissionsService {

    private final ControllerPermissionsRepository controllerPermissionsRepository;
    private final AuthRepository authRepository;
    private final RoleRepository roleRepository;
    private final SeasonRepository seasonRepository;
    private final InviteTokenRepository inviteTokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    @Lazy
    private InviteTokenService inviteTokenService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public boolean isController(Auth auth) {
        return auth != null && "Controller".equalsIgnoreCase(auth.getRole());
    }

    public void requireController(Auth auth) {
        if (!isController(auth)) {
            throw new SecurityException(ErrorMessages.FORBIDDEN);
        }
        if (Boolean.TRUE.equals(auth.getIsBlocked())) {
            throw new SecurityException(ErrorMessages.USER_BLOCKED);
        }
        if (Boolean.TRUE.equals(auth.getIsInactive())) {
            throw new SecurityException(ErrorMessages.USER_INACTIVE);
        }
    }

    public ControllerPermissionsResponse requirePermission(Auth auth, Function<ControllerPermissionsResponse, Boolean> flag) {
        requireController(auth);
        ControllerPermissionsResponse perms = getOrBootstrapPermissions(auth.getId());
        if (!Boolean.TRUE.equals(flag.apply(perms))) {
            throw new SecurityException(ErrorMessages.CONTROLLER_PERMISSION_DENIED);
        }
        return perms;
    }

    /**
     * Load permissions for a Controller. Missing row for an existing active Controller
     * is bootstrapped to full access (covers pre-feature seed users).
     */
    public ControllerPermissionsResponse getOrBootstrapPermissions(String authId) {
        Optional<ControllerPermissions> existing = controllerPermissionsRepository.findByAuthId(authId);
        if (existing.isPresent()) {
            ControllerPermissions row = existing.get();
            // Repair broken/empty rows for active Controllers (e.g. partial save → all false → empty sidebar)
            if (isAllFalse(row)) {
                Auth auth = authRepository.findById(authId).orElse(null);
                if (auth != null && isActiveController(auth)) {
                    applyAllTrue(row);
                    row.setUpdatedBy("system-repair");
                    row.setUpdatedAt(Instant.now());
                    controllerPermissionsRepository.save(row);
                    return toResponse(row);
                }
            }
            return toResponse(row);
        }
        Auth auth = authRepository.findById(authId).orElse(null);
        if (auth == null || !"Controller".equalsIgnoreCase(auth.getRole())) {
            return ControllerPermissionsResponse.none();
        }
        // Active (verified + password) Controllers without a row get full access once.
        if (isActiveController(auth)) {
            ControllerPermissions created = createFullPermissions(authId, "system-bootstrap");
            return toResponse(created);
        }
        return ControllerPermissionsResponse.basicDefaults();
    }

    private boolean isAllFalse(ControllerPermissions row) {
        return !Boolean.TRUE.equals(row.getViewDashboard())
                && !Boolean.TRUE.equals(row.getViewRequests())
                && !Boolean.TRUE.equals(row.getWorkRequests())
                && !Boolean.TRUE.equals(row.getViewRequestPaymentDetails())
                && !Boolean.TRUE.equals(row.getViewClubs())
                && !Boolean.TRUE.equals(row.getViewClubMoney())
                && !Boolean.TRUE.equals(row.getInviteControllers())
                && !Boolean.TRUE.equals(row.getManageControllerPermissions());
    }

    public ControllerPermissions createFullPermissions(String authId, String createdBy) {
        ControllerPermissions row = newPermissionsShell(authId, createdBy);
        applyAllTrue(row);
        return controllerPermissionsRepository.save(row);
    }

    public ControllerPermissions createBasicPermissions(String authId, String createdBy) {
        ControllerPermissions row = newPermissionsShell(authId, createdBy);
        applyBasicDefaults(row);
        return controllerPermissionsRepository.save(row);
    }

    public List<ControllerListItemResponse> listControllersForActor(Auth actor) {
        requireController(actor);
        ControllerPermissionsResponse perms = getOrBootstrapPermissions(actor.getId());
        if (!Boolean.TRUE.equals(perms.getInviteControllers())
                && !Boolean.TRUE.equals(perms.getManageControllerPermissions())) {
            throw new SecurityException(ErrorMessages.CONTROLLER_PERMISSION_DENIED);
        }
        return listControllersInternal();
    }

    private List<ControllerListItemResponse> listControllersInternal() {
        List<Auth> controllers = authRepository.findByRoleIgnoreCase("Controller");
        if (controllers.isEmpty()) {
            return List.of();
        }
        List<String> ids = controllers.stream().map(Auth::getId).toList();
        Map<String, ControllerPermissions> permMap = controllerPermissionsRepository.findByAuthIdIn(ids)
                .stream()
                .collect(Collectors.toMap(ControllerPermissions::getAuthId, p -> p, (a, b) -> a));

        Map<String, Instant> inviteExpiry = loadActiveInviteExpiry(ids);

        List<ControllerListItemResponse> result = new ArrayList<>();
        for (Auth auth : controllers) {
            ControllerPermissions row = permMap.get(auth.getId());
            ControllerPermissionsResponse perms = row != null
                    ? toResponse(row)
                    : (isActiveController(auth)
                        ? ControllerPermissionsResponse.allTrue()
                        : ControllerPermissionsResponse.basicDefaults());
            result.add(ControllerListItemResponse.builder()
                    .id(auth.getId())
                    .email(auth.getEmail())
                    .firstName(auth.getFirstName())
                    .lastName(auth.getLastName())
                    .phone(auth.getPhone())
                    .status(resolveStatus(auth))
                    .isVerified(auth.getIsVerified())
                    .isBlocked(auth.getIsBlocked())
                    .permissions(perms)
                    .inviteExpiresAt(inviteExpiry.get(auth.getId()))
                    .build());
        }
        result.sort(Comparator
                .comparing((ControllerListItemResponse c) -> statusRank(c.getStatus()))
                .thenComparing(c -> nullToEmpty(c.getFirstName()) + " " + nullToEmpty(c.getLastName()),
                        String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    @Transactional
    public ControllerListItemResponse createController(CreateControllerRequest request, Auth actor) {
        requirePermission(actor, ControllerPermissionsResponse::getInviteControllers);

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Password and confirm password do not match");
        }

        String email = request.getEmail().trim().toLowerCase();
        if (authRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(ErrorMessages.EMAIL_ALREADY_EXISTS);
        }
        if (request.getPhone() == null || request.getPhone().isBlank()) {
            throw new IllegalArgumentException("Phone is required");
        }
        String phone = request.getPhone().trim();
        if (authRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException(ErrorMessages.PHONE_ALREADY_EXISTS);
        }

        Role controllerRole = roleRepository.findByNameIgnoreCaseAndClubIdIsNull("Controller")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setClubId(null);
                    r.setName("Controller");
                    r.setIsActive(true);
                    return roleRepository.save(r);
                });

        Auth auth = new Auth();
        auth.setEmail(email);
        auth.setFirstName(request.getFirstName().trim());
        auth.setLastName(request.getLastName().trim());
        auth.setPhone(phone);
        auth.setPassword(passwordEncoder.encode(request.getPassword()));
        auth.setClubId(null);
        auth.setSeasonId(null);
        auth.setRoleId(controllerRole.getId());
        auth.setRole("Controller");
        auth.setUserId(null);
        auth.setUserName(null);
        auth.setClubName(null);
        auth.setIsVerified(true);
        auth.setIsBlocked(false);
        auth.setIsInactive(false);
        auth.setMpin(null);
        seasonRepository.findByActive(true).ifPresent(season -> auth.setSeasonId(season.getId()));
        Auth saved = authRepository.save(auth);

        ControllerPermissions perms = newPermissionsShell(saved.getId(), actor.getId());
        applyBasicDefaults(perms);
        if (request.getPermissions() != null) {
            applyCappedUpdate(perms, request.getPermissions(), getOrBootstrapPermissions(actor.getId()));
        }
        controllerPermissionsRepository.save(perms);

        return toListItem(saved, perms, null);
    }

    @Transactional
    public ControllerInviteResponse inviteController(InviteControllerRequest request, Auth actor) {
        requirePermission(actor, ControllerPermissionsResponse::getInviteControllers);

        boolean isWhatsApp = "whatsapp".equalsIgnoreCase(request.getCommunicationMethod());
        if (!isWhatsApp && !"email".equalsIgnoreCase(request.getCommunicationMethod())) {
            throw new IllegalArgumentException("communicationMethod must be email or whatsapp");
        }
        if (isWhatsApp && (request.getPhone() == null || request.getPhone().isBlank())) {
            throw new IllegalArgumentException("Phone is required for WhatsApp invite");
        }

        String email = request.getEmail().trim().toLowerCase();
        if (authRepository.existsByEmail(email)) {
            Auth existing = authRepository.findByEmail(email).orElse(null);
            if (existing != null && "Controller".equalsIgnoreCase(existing.getRole()) && !isActiveController(existing)) {
                // Re-invite pending controller
                return resendInvite(existing, request, actor, isWhatsApp);
            }
            throw new IllegalArgumentException(ErrorMessages.EMAIL_ALREADY_EXISTS);
        }

        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            String phone = request.getPhone().trim();
            if (authRepository.existsByPhone(phone)) {
                throw new IllegalArgumentException(ErrorMessages.PHONE_ALREADY_EXISTS);
            }
        }

        Role controllerRole = roleRepository.findByNameIgnoreCaseAndClubIdIsNull("Controller")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setClubId(null);
                    r.setName("Controller");
                    r.setIsActive(true);
                    return roleRepository.save(r);
                });

        Auth pending = new Auth();
        pending.setEmail(email);
        pending.setFirstName(trimOrEmpty(request.getFirstName()));
        pending.setLastName(trimOrEmpty(request.getLastName()));
        pending.setPhone(request.getPhone() != null && !request.getPhone().isBlank()
                ? request.getPhone().trim() : null);
        pending.setPassword(null);
        pending.setClubId(null);
        pending.setSeasonId(null);
        pending.setRoleId(controllerRole.getId());
        pending.setRole("Controller");
        pending.setUserId(null);
        pending.setUserName(null);
        pending.setClubName(null);
        pending.setIsVerified(false);
        pending.setIsBlocked(false);
        pending.setIsInactive(false);
        pending.setMpin(null);
        seasonRepository.findByActive(true).ifPresent(season -> pending.setSeasonId(season.getId()));
        Auth savedPending = authRepository.save(pending);

        ControllerPermissions perms = newPermissionsShell(savedPending.getId(), actor.getId());
        applyBasicDefaults(perms);
        if (request.getPermissions() != null) {
            applyCappedUpdate(perms, request.getPermissions(), getOrBootstrapPermissions(actor.getId()));
        }
        controllerPermissionsRepository.save(perms);

        return sendInvite(savedPending, actor, isWhatsApp);
    }

    private ControllerInviteResponse resendInvite(Auth pending, InviteControllerRequest request, Auth actor, boolean isWhatsApp) {
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            pending.setPhone(request.getPhone().trim());
        }
        if (request.getFirstName() != null && !request.getFirstName().isBlank()) {
            pending.setFirstName(request.getFirstName().trim());
        }
        if (request.getLastName() != null && !request.getLastName().isBlank()) {
            pending.setLastName(request.getLastName().trim());
        }
        authRepository.save(pending);

        ControllerPermissions row = controllerPermissionsRepository.findByAuthId(pending.getId())
                .orElseGet(() -> createBasicPermissions(pending.getId(), actor.getId()));
        if (request.getPermissions() != null) {
            applyCappedUpdate(row, request.getPermissions(), getOrBootstrapPermissions(actor.getId()));
            row.setUpdatedBy(actor.getId());
            row.setUpdatedAt(Instant.now());
            controllerPermissionsRepository.save(row);
        }
        return sendInvite(pending, actor, isWhatsApp);
    }

    private ControllerInviteResponse sendInvite(Auth pending, Auth actor, boolean isWhatsApp) {
        InviteTokenService.InviteLinkResult link = inviteTokenService.createToken(
                InvitePurpose.CONTROLLER_PROFILE,
                pending.getId(),
                null,
                pending.getSeasonId(),
                isWhatsApp ? InviteChannel.WHATSAPP : InviteChannel.EMAIL,
                actor.getId());

        if (!isWhatsApp) {
            Map<String, String> templateData = new HashMap<>();
            templateData.put("emailTitle", "Squad STM – Master Panel Invitation");
            templateData.put("emailHeading", "Controller Invitation");
            String inviter = actor.getFirstName() != null ? actor.getFirstName() : "A Controller";
            templateData.put("emailMessage",
                    "<strong>" + inviter + "</strong> has invited you to join the Squad STM Master Panel as a <strong>Controller</strong>. "
                            + "Click the button below to set your password and complete your account.");
            templateData.put("buttonText", "Accept Invitation");
            templateData.put("buttonLink", link.inviteLink());
            templateData.put("buttonColor", "#007bff");
            templateData.put("additionalInfo", "This link will expire for security reasons. If you did not expect this invitation, ignore this email.");
            templateData.put("footerMessage", "Squad STM Master Panel");

            boolean mailSent = emailService.sendEmail(
                    pending.getEmail(),
                    "Squad STM – Controller Invitation",
                    inviter + " invited you to the Master Panel. Open: " + link.inviteLink(),
                    templateData);
            if (!mailSent) {
                throw new RuntimeException("Mail sending error.");
            }
        }

        return ControllerInviteResponse.builder()
                .controller(toListItem(pending, controllerPermissionsRepository.findByAuthId(pending.getId()).orElse(null), link.expiresAt()))
                .inviteLink(isWhatsApp ? link.inviteLink() : null)
                .inviteCode(isWhatsApp ? link.inviteCode() : null)
                .expiresAt(link.expiresAt())
                .build();
    }

    @Transactional
    public ControllerPermissionsResponse updatePermissions(String targetAuthId, UpdateControllerPermissionsRequest request, Auth actor) {
        requirePermission(actor, ControllerPermissionsResponse::getManageControllerPermissions);

        if (actor.getId().equals(targetAuthId)) {
            throw new IllegalArgumentException(ErrorMessages.CONTROLLER_CANNOT_EDIT_SELF);
        }

        Auth target = authRepository.findById(targetAuthId)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.CONTROLLER_NOT_FOUND));
        if (!"Controller".equalsIgnoreCase(target.getRole())) {
            throw new IllegalArgumentException(ErrorMessages.CONTROLLER_NOT_FOUND);
        }

        ControllerPermissionsResponse actorPerms = getOrBootstrapPermissions(actor.getId());
        ControllerPermissions row = controllerPermissionsRepository.findByAuthId(targetAuthId)
                .orElseGet(() -> {
                    ControllerPermissions created = newPermissionsShell(targetAuthId, actor.getId());
                    applyBasicDefaults(created);
                    return created;
                });

        boolean hadManage = Boolean.TRUE.equals(row.getManageControllerPermissions());
        applyCappedUpdate(row, request, actorPerms);

        // Prevent removing the last manager
        if (hadManage && !Boolean.TRUE.equals(row.getManageControllerPermissions())) {
            long otherManagers = controllerPermissionsRepository.findAll().stream()
                    .filter(p -> Boolean.TRUE.equals(p.getManageControllerPermissions()))
                    .filter(p -> !targetAuthId.equals(p.getAuthId()))
                    .count();
            if (otherManagers == 0) {
                throw new IllegalArgumentException(ErrorMessages.CONTROLLER_LAST_MANAGER);
            }
        }

        row.setUpdatedBy(actor.getId());
        row.setUpdatedAt(Instant.now());
        controllerPermissionsRepository.save(row);
        return toResponse(row);
    }

    @Transactional
    public ControllerListItemResponse updateDetails(
            String targetAuthId,
            UpdateControllerDetailsRequest request,
            Auth actor) {
        requireController(actor);
        ControllerPermissionsResponse actorPerms = getOrBootstrapPermissions(actor.getId());
        if (!Boolean.TRUE.equals(actorPerms.getManageControllerPermissions())
                && !Boolean.TRUE.equals(actorPerms.getInviteControllers())) {
            throw new SecurityException(ErrorMessages.CONTROLLER_PERMISSION_DENIED);
        }

        Auth target = authRepository.findById(targetAuthId)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.CONTROLLER_NOT_FOUND));
        if (!"Controller".equalsIgnoreCase(target.getRole())) {
            throw new IllegalArgumentException(ErrorMessages.CONTROLLER_NOT_FOUND);
        }

        String email = request.getEmail().trim().toLowerCase();
        String phone = request.getPhone().trim();

        Optional<Auth> emailOwner = authRepository.findByEmail(email);
        if (emailOwner.isPresent() && !emailOwner.get().getId().equals(targetAuthId)) {
            throw new IllegalArgumentException(ErrorMessages.EMAIL_ALREADY_EXISTS);
        }
        Optional<Auth> phoneOwner = authRepository.findByPhone(phone);
        if (phoneOwner.isPresent() && !phoneOwner.get().getId().equals(targetAuthId)) {
            throw new IllegalArgumentException(ErrorMessages.PHONE_ALREADY_EXISTS);
        }

        target.setFirstName(request.getFirstName().trim());
        target.setLastName(request.getLastName().trim());
        target.setEmail(email);
        target.setPhone(phone);
        Auth saved = authRepository.save(target);

        ControllerPermissions row = controllerPermissionsRepository.findByAuthId(targetAuthId).orElse(null);
        Instant inviteExpiry = null;
        List<InviteToken> active = inviteTokenRepository.findActiveByPurposeAndEntityId(
                InvitePurpose.CONTROLLER_PROFILE, targetAuthId);
        if (!active.isEmpty()) {
            inviteExpiry = active.stream()
                    .map(InviteToken::getExpiresAt)
                    .filter(e -> e != null)
                    .max(Instant::compareTo)
                    .orElse(null);
        }
        return toListItem(saved, row, inviteExpiry);
    }

    @Transactional
    public void revokePendingInvite(String targetAuthId, Auth actor) {
        requirePermission(actor, ControllerPermissionsResponse::getInviteControllers);

        Auth target = authRepository.findById(targetAuthId)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.CONTROLLER_NOT_FOUND));
        if (!"Controller".equalsIgnoreCase(target.getRole()) || isActiveController(target)) {
            throw new IllegalArgumentException(ErrorMessages.CONTROLLER_INVITE_PENDING_ONLY);
        }

        inviteTokenService.revokeActiveTokensForEntity(InvitePurpose.CONTROLLER_PROFILE, targetAuthId);
        controllerPermissionsRepository.deleteByAuthId(targetAuthId);
        authRepository.delete(target);
    }

    @Transactional
    public ControllerListItemResponse setBlocked(String targetAuthId, boolean blocked, Auth actor) {
        return setStatus(targetAuthId, blocked ? "BLOCKED" : "ACTIVE", actor);
    }

    /**
     * Change controller account status. Allowed: ACTIVE | INACTIVE | BLOCKED.
     * Pending invites cannot use this — revoke the invite instead.
     */
    @Transactional
    public ControllerListItemResponse setStatus(String targetAuthId, String status, Auth actor) {
        requirePermission(actor, ControllerPermissionsResponse::getManageControllerPermissions);
        if (actor.getId().equals(targetAuthId)) {
            throw new IllegalArgumentException("You cannot change your own status.");
        }
        Auth target = authRepository.findById(targetAuthId)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.CONTROLLER_NOT_FOUND));
        if (!"Controller".equalsIgnoreCase(target.getRole())) {
            throw new IllegalArgumentException(ErrorMessages.CONTROLLER_NOT_FOUND);
        }
        if (!isActiveController(target)) {
            throw new IllegalArgumentException("Pending invites cannot change status. Revoke the invite instead.");
        }

        String normalized = status == null ? "" : status.trim().toUpperCase();
        switch (normalized) {
            case "ACTIVE" -> {
                target.setIsBlocked(false);
                target.setIsInactive(false);
            }
            case "INACTIVE" -> {
                target.setIsBlocked(false);
                target.setIsInactive(true);
            }
            case "BLOCKED" -> {
                target.setIsBlocked(true);
                target.setIsInactive(false);
            }
            default -> throw new IllegalArgumentException("Status must be ACTIVE, INACTIVE, or BLOCKED.");
        }

        authRepository.save(target);
        ControllerPermissions row = controllerPermissionsRepository.findByAuthId(targetAuthId).orElse(null);
        return toListItem(target, row, null);
    }

    @Transactional
    public Auth completeControllerInvite(String authId, CompleteControllerInviteRequest request) {
        Auth auth = authRepository.findById(authId)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.INVITE_LINK_INVALID));
        if (!"Controller".equalsIgnoreCase(auth.getRole())) {
            throw new IllegalArgumentException(ErrorMessages.INVITE_LINK_INVALID);
        }
        if (isActiveController(auth)) {
            throw new IllegalArgumentException(ErrorMessages.CONTROLLER_INVITE_ALREADY_SUBMITTED);
        }
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Password and confirm password do not match");
        }

        auth.setFirstName(request.getFirstName().trim());
        auth.setLastName(request.getLastName().trim());
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            String phone = request.getPhone().trim();
            Optional<Auth> phoneOwner = authRepository.findByPhone(phone);
            if (phoneOwner.isPresent() && !phoneOwner.get().getId().equals(authId)) {
                throw new IllegalArgumentException(ErrorMessages.PHONE_ALREADY_EXISTS);
            }
            auth.setPhone(phone);
        }
        // Email is fixed from invite; ignore client override that differs
        auth.setPassword(passwordEncoder.encode(request.getPassword()));
        auth.setIsVerified(true);
        auth.setIsBlocked(false);
        auth.setIsInactive(false);
        seasonRepository.findByActive(true).ifPresent(season -> auth.setSeasonId(season.getId()));
        Auth saved = authRepository.save(auth);

        if (!controllerPermissionsRepository.existsByAuthId(authId)) {
            createBasicPermissions(authId, "invite-complete");
        }
        return saved;
    }

    public ControllerPermissionsResponse resolveInvitePrefill(String authId) {
        return getOrBootstrapPermissions(authId);
    }

    public boolean isPendingControllerInvite(Auth auth) {
        return auth != null
                && "Controller".equalsIgnoreCase(auth.getRole())
                && !isActiveController(auth);
    }

    private boolean isActiveController(Auth auth) {
        return Boolean.TRUE.equals(auth.getIsVerified())
                && auth.getPassword() != null
                && !auth.getPassword().isBlank();
    }

    private String resolveStatus(Auth auth) {
        if (!isActiveController(auth)) {
            return "PENDING";
        }
        if (Boolean.TRUE.equals(auth.getIsBlocked())) {
            return "BLOCKED";
        }
        if (Boolean.TRUE.equals(auth.getIsInactive())) {
            return "INACTIVE";
        }
        return "ACTIVE";
    }

    private int statusRank(String status) {
        if ("PENDING".equals(status)) return 0;
        if ("ACTIVE".equals(status)) return 1;
        if ("INACTIVE".equals(status)) return 2;
        return 3; // BLOCKED
    }

    private Map<String, Instant> loadActiveInviteExpiry(List<String> authIds) {
        Map<String, Instant> map = new HashMap<>();
        for (String authId : authIds) {
            List<InviteToken> active = inviteTokenRepository.findActiveByPurposeAndEntityId(
                    InvitePurpose.CONTROLLER_PROFILE, authId);
            if (!active.isEmpty()) {
                active.stream()
                        .map(InviteToken::getExpiresAt)
                        .filter(e -> e != null)
                        .max(Instant::compareTo)
                        .ifPresent(exp -> map.put(authId, exp));
            }
        }
        return map;
    }

    private ControllerPermissions newPermissionsShell(String authId, String createdBy) {
        ControllerPermissions row = new ControllerPermissions();
        row.setAuthId(authId);
        row.setCreatedBy(createdBy);
        row.setUpdatedBy(createdBy);
        Instant now = Instant.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return row;
    }

    private void applyAllTrue(ControllerPermissions row) {
        row.setViewDashboard(true);
        row.setViewRequests(true);
        row.setWorkRequests(true);
        row.setViewRequestPaymentDetails(true);
        row.setViewClubs(true);
        row.setViewClubMoney(true);
        row.setInviteControllers(true);
        row.setManageControllerPermissions(true);
    }

    private void applyBasicDefaults(ControllerPermissions row) {
        row.setViewDashboard(true);
        row.setViewRequests(true);
        row.setWorkRequests(false);
        row.setViewRequestPaymentDetails(false);
        row.setViewClubs(true);
        row.setViewClubMoney(false);
        row.setInviteControllers(false);
        row.setManageControllerPermissions(false);
    }

    /**
     * Apply requested flags, but never grant a permission the actor does not hold.
     * Null request fields leave the current value unchanged.
     */
    private void applyCappedUpdate(
            ControllerPermissions row,
            UpdateControllerPermissionsRequest request,
            ControllerPermissionsResponse actorPerms) {
        if (request.getViewDashboard() != null) {
            row.setViewDashboard(andCap(request.getViewDashboard(), actorPerms.getViewDashboard()));
        }
        if (request.getViewRequests() != null) {
            row.setViewRequests(andCap(request.getViewRequests(), actorPerms.getViewRequests()));
        }
        if (request.getWorkRequests() != null) {
            row.setWorkRequests(andCap(request.getWorkRequests(), actorPerms.getWorkRequests()));
        }
        if (request.getViewRequestPaymentDetails() != null) {
            row.setViewRequestPaymentDetails(andCap(request.getViewRequestPaymentDetails(), actorPerms.getViewRequestPaymentDetails()));
        }
        if (request.getViewClubs() != null) {
            row.setViewClubs(andCap(request.getViewClubs(), actorPerms.getViewClubs()));
        }
        if (request.getViewClubMoney() != null) {
            row.setViewClubMoney(andCap(request.getViewClubMoney(), actorPerms.getViewClubMoney()));
        }
        if (request.getInviteControllers() != null) {
            row.setInviteControllers(andCap(request.getInviteControllers(), actorPerms.getInviteControllers()));
        }
        if (request.getManageControllerPermissions() != null) {
            row.setManageControllerPermissions(andCap(request.getManageControllerPermissions(), actorPerms.getManageControllerPermissions()));
        }
        // Dependent caps: work/payment require viewRequests conceptually
        if (Boolean.TRUE.equals(row.getWorkRequests()) && !Boolean.TRUE.equals(row.getViewRequests())) {
            row.setViewRequests(Boolean.TRUE.equals(actorPerms.getViewRequests()));
            if (!Boolean.TRUE.equals(row.getViewRequests())) {
                row.setWorkRequests(false);
            }
        }
        if (Boolean.TRUE.equals(row.getViewRequestPaymentDetails()) && !Boolean.TRUE.equals(row.getViewRequests())) {
            row.setViewRequests(Boolean.TRUE.equals(actorPerms.getViewRequests()));
            if (!Boolean.TRUE.equals(row.getViewRequests())) {
                row.setViewRequestPaymentDetails(false);
            }
        }
        if (Boolean.TRUE.equals(row.getViewClubMoney()) && !Boolean.TRUE.equals(row.getViewClubs())) {
            row.setViewClubs(Boolean.TRUE.equals(actorPerms.getViewClubs()));
            if (!Boolean.TRUE.equals(row.getViewClubs())) {
                row.setViewClubMoney(false);
            }
        }
    }

    private boolean andCap(Boolean desired, Boolean actorHas) {
        return Boolean.TRUE.equals(desired) && Boolean.TRUE.equals(actorHas);
    }

    public ControllerPermissionsResponse toResponse(ControllerPermissions row) {
        return ControllerPermissionsResponse.builder()
                .viewDashboard(Boolean.TRUE.equals(row.getViewDashboard()))
                .viewRequests(Boolean.TRUE.equals(row.getViewRequests()))
                .workRequests(Boolean.TRUE.equals(row.getWorkRequests()))
                .viewRequestPaymentDetails(Boolean.TRUE.equals(row.getViewRequestPaymentDetails()))
                .viewClubs(Boolean.TRUE.equals(row.getViewClubs()))
                .viewClubMoney(Boolean.TRUE.equals(row.getViewClubMoney()))
                .inviteControllers(Boolean.TRUE.equals(row.getInviteControllers()))
                .manageControllerPermissions(Boolean.TRUE.equals(row.getManageControllerPermissions()))
                .build();
    }

    private ControllerListItemResponse toListItem(Auth auth, ControllerPermissions row, Instant inviteExpiresAt) {
        return ControllerListItemResponse.builder()
                .id(auth.getId())
                .email(auth.getEmail())
                .firstName(auth.getFirstName())
                .lastName(auth.getLastName())
                .phone(auth.getPhone())
                .status(resolveStatus(auth))
                .isVerified(auth.getIsVerified())
                .isBlocked(auth.getIsBlocked())
                .isInactive(auth.getIsInactive())
                .permissions(row != null ? toResponse(row) : ControllerPermissionsResponse.basicDefaults())
                .inviteExpiresAt(inviteExpiresAt)
                .build();
    }

    private static String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
