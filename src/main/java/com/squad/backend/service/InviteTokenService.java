package com.squad.backend.service;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.constants.InviteChannel;
import com.squad.backend.constants.InvitePurpose;
import com.squad.backend.dto.request.player.CreatePlayerRequest;
import com.squad.backend.dto.request.user.CreateUserRequest;
import com.squad.backend.dto.response.invite.InviteResolveResponse;
import com.squad.backend.dto.response.player.PlayerResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.model.Club;
import com.squad.backend.model.InviteToken;
import com.squad.backend.model.Player;
import com.squad.backend.model.User;
import com.squad.backend.repository.ClubRepository;
import com.squad.backend.repository.InviteTokenRepository;
import com.squad.backend.repository.PlayerRepository;
import com.squad.backend.repository.UserRepository;
import com.squad.backend.utils.FrontendLinkUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InviteTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-";

    private final InviteTokenRepository inviteTokenRepository;
    private final PlayerRepository playerRepository;
    private final UserRepository userRepository;
    private final ClubRepository clubRepository;

    @Autowired
    @Lazy
    private PlayerService playerService;

    @Autowired
    @Lazy
    private UserService userService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${jwt.invite-expiration:604800000}")
    private long inviteExpirationMs;

    @Value("${app.invite-code-length:22}")
    private int inviteCodeLength;

    public InviteLinkResult createToken(
            String purpose,
            String entityId,
            String clubId,
            String seasonId,
            String channel,
            String createdBy) {
        revokeActiveTokens(purpose, entityId);

        Instant expiresAt = Instant.now().plusMillis(inviteExpirationMs);
        String code = generateUniqueCode();

        InviteToken token = new InviteToken();
        token.setCode(code);
        token.setPurpose(purpose);
        token.setEntityId(entityId);
        token.setClubId(clubId);
        token.setSeasonId(seasonId);
        token.setChannel(channel);
        token.setExpiresAt(expiresAt);
        token.setCreatedBy(createdBy);
        token.setCreatedAt(Instant.now());
        token.setUpdatedAt(Instant.now());
        inviteTokenRepository.save(token);

        String inviteLink = FrontendLinkUtils.hashLink(frontendUrl, "i/" + code);
        return new InviteLinkResult(inviteLink, code, expiresAt);
    }

    public InviteResolveResponse resolve(String code) {
        InviteToken token = validateActiveToken(code);

        if (InvitePurpose.PLAYER_PROFILE.equals(token.getPurpose())) {
            return resolvePlayerProfile(token);
        }
        if (InvitePurpose.USER_PROFILE.equals(token.getPurpose())) {
            return resolveUserProfile(token);
        }
        throw new IllegalArgumentException(ErrorMessages.INVITE_LINK_INVALID);
    }

    public PlayerResponse completePlayerProfile(String code, CreatePlayerRequest request) {
        InviteToken token = validateActiveToken(code);
        if (!InvitePurpose.PLAYER_PROFILE.equals(token.getPurpose())) {
            throw new IllegalArgumentException(ErrorMessages.INVITE_LINK_INVALID);
        }
        Player player = playerRepository.findById(token.getEntityId())
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.INVITE_LINK_INVALID));
        if (!playerService.isPendingPlayerInvite(player)) {
            throw new IllegalArgumentException(ErrorMessages.PLAYER_INVITE_ALREADY_SUBMITTED);
        }
        PlayerResponse response = playerService.completePlayerProfile(token.getEntityId(), request);
        markUsed(token);
        return response;
    }

    public Auth completeUserProfile(String code, CreateUserRequest request) {
        InviteToken token = validateActiveToken(code);
        if (!InvitePurpose.USER_PROFILE.equals(token.getPurpose())) {
            throw new IllegalArgumentException(ErrorMessages.INVITE_LINK_INVALID);
        }
        Auth response = userService.completeUserProfile(token.getEntityId(), request);
        markUsed(token);
        return response;
    }

    private InviteResolveResponse resolvePlayerProfile(InviteToken token) {
        Player player = playerRepository.findById(token.getEntityId())
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.INVITE_LINK_INVALID));

        if (!playerService.isPendingPlayerInvite(player)) {
            throw new IllegalArgumentException(ErrorMessages.PLAYER_INVITE_ALREADY_SUBMITTED);
        }

        PlayerService.PlayerInvitePrefillFields prefill =
                playerService.buildPlayerInvitePrefill(player, token.getChannel());
        String clubName = resolveClubName(token.getClubId());
        return InviteResolveResponse.builder()
                .purpose(token.getPurpose())
                .entityId(player.getId())
                .clubId(token.getClubId())
                .seasonId(token.getSeasonId())
                .clubName(clubName)
                .email(prefill.email())
                .phone(prefill.phone())
                .canPrefill(true)
                .alreadySubmitted(false)
                .expiresAt(token.getExpiresAt())
                .build();
    }

    private InviteResolveResponse resolveUserProfile(InviteToken token) {
        User user = userRepository.findById(token.getEntityId())
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.INVITE_LINK_INVALID));

        if (!userService.isPendingUserInvite(user)) {
            throw new IllegalArgumentException(ErrorMessages.USER_INVITE_ALREADY_SUBMITTED);
        }

        UserService.UserInvitePrefillFields prefill =
                userService.buildUserInvitePrefill(user, token.getChannel());
        String clubName = resolveClubName(token.getClubId());
        return InviteResolveResponse.builder()
                .purpose(token.getPurpose())
                .entityId(user.getId())
                .clubId(token.getClubId())
                .seasonId(token.getSeasonId())
                .clubName(clubName)
                .email(prefill.email())
                .phone(prefill.phone())
                .role(prefill.role())
                .canPrefill(true)
                .alreadySubmitted(false)
                .expiresAt(token.getExpiresAt())
                .build();
    }

    private InviteToken validateActiveToken(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException(ErrorMessages.INVITE_LINK_INVALID);
        }

        InviteToken token = inviteTokenRepository.findByCode(code.trim())
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.INVITE_LINK_INVALID));

        if (token.getRevokedAt() != null) {
            throw new InviteTokenRevokedException(ErrorMessages.INVITE_LINK_REVOKED);
        }
        if (token.getUsedAt() != null) {
            throw new IllegalArgumentException(
                    InvitePurpose.USER_PROFILE.equals(token.getPurpose())
                            ? ErrorMessages.USER_INVITE_ALREADY_SUBMITTED
                            : ErrorMessages.PLAYER_INVITE_ALREADY_SUBMITTED);
        }
        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(Instant.now())) {
            throw new InviteTokenExpiredException(
                    InvitePurpose.USER_PROFILE.equals(token.getPurpose())
                            ? ErrorMessages.USER_INVITE_LINK_EXPIRED
                            : ErrorMessages.PLAYER_INVITE_LINK_EXPIRED);
        }
        return token;
    }

    private void revokeActiveTokens(String purpose, String entityId) {
        List<InviteToken> activeTokens = inviteTokenRepository.findActiveByPurposeAndEntityId(purpose, entityId);
        Instant now = Instant.now();
        for (InviteToken existing : activeTokens) {
            existing.setRevokedAt(now);
            existing.setUpdatedAt(now);
        }
        if (!activeTokens.isEmpty()) {
            inviteTokenRepository.saveAll(activeTokens);
        }
    }

    private void markUsed(InviteToken token) {
        token.setUsedAt(Instant.now());
        token.setUpdatedAt(Instant.now());
        inviteTokenRepository.save(token);
    }

    private String resolveClubName(String clubId) {
        if (clubId == null) {
            return null;
        }
        return clubRepository.findById(clubId)
                .map(Club::getClubName)
                .orElse(null);
    }

    private String generateUniqueCode() {
        int length = Math.max(16, inviteCodeLength);
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = randomCode(length);
            if (inviteTokenRepository.findByCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Failed to generate unique invite code");
    }

    private String randomCode(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(length);
        for (byte b : bytes) {
            sb.append(CODE_ALPHABET.charAt(Math.floorMod(b, CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    public record InviteLinkResult(String inviteLink, String inviteCode, Instant expiresAt) {
    }

    public static class InviteTokenExpiredException extends RuntimeException {
        public InviteTokenExpiredException(String message) {
            super(message);
        }
    }

    public static class InviteTokenRevokedException extends RuntimeException {
        public InviteTokenRevokedException(String message) {
            super(message);
        }
    }
}
