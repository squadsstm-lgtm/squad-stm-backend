package com.squad.backend.service;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.constants.InviteChannel;
import com.squad.backend.constants.InvitePurpose;
import com.squad.backend.dto.request.player.CreatePlayerRequest;
import com.squad.backend.dto.response.invite.PlayerInviteResponse;
import com.squad.backend.dto.response.player.PlayerResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.model.Club;
import com.squad.backend.model.Player;
import com.squad.backend.model.Session;
import com.squad.backend.model.Team;
import com.squad.backend.repository.ClubRepository;
import com.squad.backend.repository.PlayerRepository;
import com.squad.backend.repository.SessionRepository;
import com.squad.backend.repository.TeamRepository;
import com.squad.backend.utils.FrontendLinkUtils;
import com.squad.backend.utils.PhoneUtils;
import com.squad.backend.security.JwtTokenProvider;
import com.squad.backend.security.TenantScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final ClubRepository clubRepository;
    private final TeamRepository teamRepository;
    private final SessionRepository sessionRepository;
    private final EmailService emailService;
    private final JwtTokenProvider jwtTokenProvider;
    private final MongoTemplate mongoTemplateClient;
    private static final Sort DEFAULT_PLAYER_PAGE_SORT = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("_id"));

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${jwt.invite-expiration}")
    private long jwtInviteExpiration;

    @Autowired
    @Lazy
    private InviteTokenService inviteTokenService;

    public PlayerResponse createPlayer(CreatePlayerRequest request, Auth auth) {
        // Use clubId from request if provided, otherwise use authenticated user's clubId
        String clubId = (request.getClubId() != null && !request.getClubId().isEmpty()) 
                ? request.getClubId() 
                : auth.getClubId();
        
        if (clubId == null || clubId.isEmpty()) {
            throw new IllegalArgumentException("Club ID is required");
        }
        if (TenantScope.denyClubOnly(auth, clubId)) {
            throw new IllegalArgumentException(ErrorMessages.FORBIDDEN);
        }

        Optional<Player> existingPlayer = playerRepository.findByEmail(request.getEmail())
                .filter(p -> clubId.equals(p.getClubId()) && Boolean.TRUE.equals(p.getIsActive()));

        if (existingPlayer.isPresent()) {
            throw new IllegalArgumentException(ErrorMessages.PLAYER_PROFILE_COMPLETED);
        }

        Player player = new Player();
        mapToModel(request, player);
        player.setClubId(clubId); // Set the resolved clubId
        player.setSeasonId(auth.getSeasonId());
        player.setIsActive(true);
        player.setCreatedBy(auth.getId());
        player.setUpdatedBy(auth.getId());
        player = playerRepository.save(player);
        return mapToResponse(player);
    }

    public PagedPlayersResult getCategorizedPlayersPaged(String clubId, String seasonId, String search, int pageNumber, int pageSize) {
        int safePage = Math.max(1, pageNumber);
        int safeSize = Math.max(1, pageSize);
        Query query = new Query();
        query.addCriteria(Criteria.where("clubId").is(clubId));
        query.addCriteria(Criteria.where("seasonId").is(seasonId));
        query.addCriteria(Criteria.where("firstName").ne(""));
        query.addCriteria(Criteria.where("lastName").ne(""));
        query.addCriteria(Criteria.where("teams").exists(true).ne(Collections.emptyMap()));
        query.addCriteria(Criteria.where("isActive").is(true));
        String searchTerm = normalizeSearch(search);
        if (searchTerm != null) {
            String pattern = ".*" + java.util.regex.Pattern.quote(searchTerm) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("firstName").regex(pattern, "i"),
                    Criteria.where("lastName").regex(pattern, "i"),
                    Criteria.where("phone").regex(pattern, "i"),
                    Criteria.where("email").regex(pattern, "i")
            ));
        }
        long totalItems = mongoTemplateClient.count(query, Player.class);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, DEFAULT_PLAYER_PAGE_SORT);
        query.with(pageable);
        List<PlayerResponse> rows = mongoTemplateClient.find(query, Player.class).stream().map(this::mapToResponse).collect(Collectors.toList());
        int totalPages = (int) Math.ceil((double) totalItems / safeSize);
        return new PagedPlayersResult(rows, totalItems, totalPages, safePage, safeSize);
    }

    public PlayerResponse getPlayerById(String id, Auth auth) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.PLAYER_NOT_FOUND));
        if (!Boolean.TRUE.equals(player.getIsActive())) {
            throw new IllegalArgumentException(ErrorMessages.PLAYER_NOT_FOUND);
        }
        if (TenantScope.denyClubScopedEntity(auth, player.getClubId(), player.getSeasonId())) {
            throw new IllegalArgumentException(ErrorMessages.PLAYER_NOT_FOUND);
        }
        PlayerResponse response = mapToResponse(player);
        enrichWithClubAndTeamNames(player, response);
        return response;
    }

    public PlayerResponse updatePlayer(String id, String teamId, CreatePlayerRequest request, Auth auth) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.PLAYER_NOT_FOUND));
        if (!Boolean.TRUE.equals(player.getIsActive())) {
            throw new IllegalArgumentException(ErrorMessages.PLAYER_NOT_FOUND);
        }
        if (TenantScope.denyClubScopedEntity(auth, player.getClubId(), player.getSeasonId())) {
            throw new IllegalArgumentException(ErrorMessages.PLAYER_NOT_FOUND);
        }

        // If teamId is provided as query parameter (quick team assignment)
        if (teamId != null && !teamId.isEmpty() && !"undefined".equals(teamId)) {
            addTeamToPlayer(player, teamId, id);
            player.setUpdatedBy(auth.getId());
            player = playerRepository.save(player);
            return mapToResponse(player);
        }

        // Normal update (from request body)
        if (request != null) {
            String existingClubId = player.getClubId();
            String existingSeasonId = player.getSeasonId();
            String existingProfileImage = player.getProfileImage();
            
            mapToModel(request, player);
            
            player.setClubId(existingClubId);
            player.setSeasonId(existingSeasonId != null ? existingSeasonId : auth.getSeasonId());
            player.setIsActive(Boolean.TRUE.equals(player.getIsActive()));
            
            if (request.getProfileImage() == null || request.getProfileImage().isEmpty()) {
                player.setProfileImage(existingProfileImage);
            }
            player.setUpdatedBy(auth.getId());
            player = playerRepository.save(player);
        }
        
        return mapToResponse(player);
    }

    public PlayerResponse updatePlayerWithToken(String id, String token, CreatePlayerRequest request) {
        jwtTokenProvider.validateTokenForPlayerInvite(token, id);
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.PLAYER_NOT_FOUND));
        if (isPendingPlayerInvite(player)) {
            return completePlayerProfile(id, request);
        }
        return updateExistingPlayerViaToken(player, request);
    }

    private PlayerResponse updateExistingPlayerViaToken(Player player, CreatePlayerRequest request) {
        if (request == null) {
            PlayerResponse response = mapToResponse(player);
            enrichWithClubAndTeamNames(player, response);
            return response;
        }
        String existingClubId = player.getClubId();
        String existingSeasonId = player.getSeasonId();
        String existingProfileImage = player.getProfileImage();
        String existingCreatedBy = player.getCreatedBy();

        mapToModel(request, player);
        player.setClubId(existingClubId);
        player.setSeasonId(existingSeasonId);
        player.setCreatedBy(existingCreatedBy);
        player.setIsActive(true);
        if (request.getProfileImage() == null || request.getProfileImage().isEmpty()) {
            player.setProfileImage(existingProfileImage);
        }
        player.setUpdatedBy(player.getId());
        player = playerRepository.save(player);

        PlayerResponse response = mapToResponse(player);
        enrichWithClubAndTeamNames(player, response);
        return response;
    }

    public PlayerResponse completePlayerProfile(String id, CreatePlayerRequest request) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.PLAYER_NOT_FOUND));
        if (isInviteProfileSubmitted(player)) {
            throw new IllegalArgumentException(ErrorMessages.PLAYER_INVITE_ALREADY_SUBMITTED);
        }

        if (request != null) {
            String existingClubId = player.getClubId();
            String existingSeasonId = player.getSeasonId();
            String existingProfileImage = player.getProfileImage();
            String existingCreatedBy = player.getCreatedBy();

            mapToModel(request, player);
            player.setClubId(existingClubId);
            player.setSeasonId(existingSeasonId);
            player.setCreatedBy(existingCreatedBy);
            player.setIsActive(true);
            if (request.getProfileImage() == null || request.getProfileImage().isEmpty()) {
                player.setProfileImage(existingProfileImage);
            }
            player.setUpdatedBy(id);
            player = playerRepository.save(player);
        }

        PlayerResponse response = mapToResponse(player);
        enrichWithClubAndTeamNames(player, response);
        return response;
    }

    public boolean isInviteProfileSubmitted(Player player) {
        return Boolean.TRUE.equals(player.getIsActive())
                && player.getFirstName() != null && !player.getFirstName().isBlank()
                && player.getLastName() != null && !player.getLastName().isBlank();
    }

    /** Open invite stub — not yet an active completed player profile. */
    public boolean isPendingPlayerInvite(Player player) {
        return player != null && !isInviteProfileSubmitted(player);
    }

    /** Channel-specific prefill for pending player invites (public /i/{code} flow only). */
    public PlayerInvitePrefillFields buildPlayerInvitePrefill(Player player, String channel) {
        String email = null;
        String phone = null;
        if (InviteChannel.EMAIL.equalsIgnoreCase(channel)) {
            if (player.getEmail() != null && !player.getEmail().isBlank()) {
                email = player.getEmail().trim().toLowerCase();
            }
        } else if (InviteChannel.WHATSAPP.equalsIgnoreCase(channel)) {
            if (player.getPhone() != null && !player.getPhone().isBlank()) {
                phone = player.getPhone().trim();
            }
        }
        return new PlayerInvitePrefillFields(email, phone);
    }

    public record PlayerInvitePrefillFields(String email, String phone) {
    }

    private boolean isPhoneBlockedForPlayerInvite(String clubId, String phone) {
        if (clubId == null || phone == null || phone.isBlank()) {
            return false;
        }
        return playerRepository.findByClubId(clubId).stream()
                .anyMatch(p -> PhoneUtils.matches(p.getPhone(), phone) && isInviteProfileSubmitted(p));
    }

    private void assertEligibleForPlayerInvite(String clubId, String email, String phone) {
        if (email != null && !email.isBlank()) {
            Optional<Player> existing = playerRepository.findByEmail(email.trim().toLowerCase())
                    .filter(p -> clubId.equals(p.getClubId()) && isInviteProfileSubmitted(p));
            if (existing.isPresent()) {
                throw new IllegalArgumentException(ErrorMessages.PLAYER_PROFILE_COMPLETED);
            }
        }
        if (phone != null && !phone.isBlank() && isPhoneBlockedForPlayerInvite(clubId, phone)) {
            throw new IllegalArgumentException(ErrorMessages.PHONE_ALREADY_EXISTS);
        }
    }

    public void deletePlayer(String id, Auth auth) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.PLAYER_NOT_FOUND));
        if (!Boolean.TRUE.equals(player.getIsActive())) {
            throw new IllegalArgumentException(ErrorMessages.PLAYER_NOT_FOUND);
        }
        if (TenantScope.denyClubScopedEntity(auth, player.getClubId(), player.getSeasonId())) {
            throw new IllegalArgumentException(ErrorMessages.PLAYER_NOT_FOUND);
        }

        String clubId = player.getClubId();
        String seasonId = player.getSeasonId();
        if (clubId == null || seasonId == null) {
            player.setIsActive(false);
            playerRepository.save(player);
            return;
        }

        // Teams that include this player (by playersList containing player id)
        List<Team> teamsWithPlayer = teamRepository.findByPlayersListContainingPlayerId(id);
        List<String> teamIds = teamsWithPlayer.stream()
                .map(Team::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<Session> sessions = sessionRepository.findByClubIdAndSeasonId(clubId, seasonId);
        Instant now = Instant.now();
        boolean hasActiveSession = sessions.stream().anyMatch(session -> {
            if (!Boolean.TRUE.equals(session.getActive())) {
                return false;
            }
            if (session.getDate() == null || session.getDate().isEmpty()) {
                return false;
            }
            Instant sessionDate = parseSessionDate(session.getDate());
            if (sessionDate == null || !sessionDate.isAfter(now)) {
                return false;
            }
            boolean inAdditionalPlayers = session.getAdditionalPlayers() != null
                    && session.getAdditionalPlayers().contains(id);
            boolean inTeamList = session.getTeamList() != null
                    && teamIds.stream().anyMatch(tid -> session.getTeamList().contains(tid));
            return inAdditionalPlayers || inTeamList;
        });

        if (hasActiveSession) {
            throw new IllegalArgumentException(ErrorMessages.PLAYER_HAS_ACTIVE_SESSION);
        }

        player.setIsActive(false);
        playerRepository.save(player);
    }

    /** Parse session date string (ISO or date-only) to Instant; returns null if unparseable. */
    private Instant parseSessionDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(dateStr);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(dateStr).atStartOfDay(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    public List<PlayerResponse> createBulkPlayers(List<CreatePlayerRequest> requests, Auth auth) {
        if (!TenantScope.isControllerWithoutClub(auth)) {
            for (CreatePlayerRequest r : requests) {
                if (r.getClubId() == null || TenantScope.denyClubOnly(auth, r.getClubId())) {
                    throw new IllegalArgumentException(ErrorMessages.FORBIDDEN);
                }
            }
        }
        List<Player> existingPlayers = playerRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()) &&
                        requests.stream().anyMatch(r ->
                    r.getEmail().equals(p.getEmail()) &&
                    r.getClubId().equals(p.getClubId()) &&
                    auth.getSeasonId().equals(p.getSeasonId())))
                .collect(Collectors.toList());

        List<CreatePlayerRequest> newPlayers = requests.stream()
                .filter(newPlayer -> existingPlayers.stream().noneMatch(existing ->
                    existing.getEmail().equals(newPlayer.getEmail()) &&
                    existing.getClubId().equals(newPlayer.getClubId())))
                .collect(Collectors.toList());

        if (newPlayers.isEmpty()) {
            throw new IllegalArgumentException("No new records to upload");
        }

        List<Player> playersToSave = newPlayers.stream().map(request -> {
            Player player = new Player();
            mapToModel(request, player);
            player.setClubId(request.getClubId());
            player.setSeasonId(auth.getSeasonId());
            if (player.getTeams() == null) {
                player.setTeams(new HashMap<>()); // Initialize empty teams map
            }
            player.setIsActive(true);
            player.setCreatedBy(auth.getId());
            player.setUpdatedBy("");
            player.setCreatedAt(Instant.now());
            player.setUpdatedAt(Instant.now());
            return player;
        }).collect(Collectors.toList());

        List<Player> savedPlayers = playerRepository.saveAll(playersToSave);
        return savedPlayers.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public PlayerInviteResponse invitePlayer(String communicationMethod, String email, String phone, String clubId, Auth auth) {
        boolean isWhatsApp = "whatsapp".equalsIgnoreCase(communicationMethod);
        if (isWhatsApp && (phone == null || phone.isBlank())) {
            throw new IllegalArgumentException("Phone is required for WhatsApp invite");
        }
        if (!isWhatsApp && (email == null || email.isBlank())) {
            throw new IllegalArgumentException("Email is required for email invite");
        }

        String targetClubId = clubId != null ? clubId : auth.getClubId();
        if (TenantScope.denyClubOnly(auth, targetClubId)) {
            throw new IllegalArgumentException(ErrorMessages.FORBIDDEN);
        }

        String normalizedEmail = email != null ? email.trim().toLowerCase() : "";
        String normalizedPhone = phone != null ? phone.trim() : "";
        assertEligibleForPlayerInvite(targetClubId, normalizedEmail, normalizedPhone);

        Player player = findOrCreateInvitePlayer(normalizedEmail, normalizedPhone, targetClubId, auth);

        PlayerResponse playerResponse = mapToResponse(player);
        enrichWithClubAndTeamNames(player, playerResponse);

        if (isWhatsApp) {
            InviteTokenService.InviteLinkResult link = inviteTokenService.createToken(
                    InvitePurpose.PLAYER_PROFILE,
                    player.getId(),
                    targetClubId,
                    auth.getSeasonId(),
                    InviteChannel.WHATSAPP,
                    auth.getId());
            return PlayerInviteResponse.builder()
                    .player(playerResponse)
                    .inviteLink(link.inviteLink())
                    .inviteCode(link.inviteCode())
                    .expiresAt(link.expiresAt())
                    .build();
        }

        String token = jwtTokenProvider.generateToken(player.getId(), jwtInviteExpiration);
        String inviteLink = FrontendLinkUtils.hashLink(frontendUrl,
                "add/player-details/" + player.getId() + "/" + targetClubId + "/" + token);

        Map<String, String> templateData = new HashMap<>();
        templateData.put("emailTitle", "Squad STM Player Request");
        templateData.put("emailHeading", "Player Information Request");
        templateData.put("emailMessage", "<strong>" + (auth.getFirstName() != null ? auth.getFirstName() : "Squad STM") + "</strong> has requested you to complete your player information. Please click the button below to provide your details.");
        templateData.put("buttonText", "Player Information Request");
        templateData.put("buttonLink", inviteLink);
        templateData.put("buttonColor", "#28a745");
        templateData.put("additionalInfo", "Important: Please ensure to provide accurate information as it will be used for team registration and communication purposes.");
        templateData.put("footerMessage", "If you believe this request was sent in error, please ignore this email.");

        boolean mailSent = emailService.sendEmail(
                normalizedEmail,
                "Squad STM Player Request",
                (auth.getFirstName() != null ? auth.getFirstName() : "Squad STM") + " has requested you to complete your player information for Squad STM.",
                templateData
        );
        if (!mailSent) {
            throw new RuntimeException("Mail sending error.");
        }

        return PlayerInviteResponse.builder()
                .player(playerResponse)
                .build();
    }

    private Player findOrCreateInvitePlayer(String email, String phone, String targetClubId, Auth auth) {
        Optional<Player> existingPlayer = Optional.empty();
        if (email != null && !email.isBlank()) {
            existingPlayer = playerRepository.findByEmail(email)
                    .filter(p -> targetClubId.equals(p.getClubId()));
        }
        if (existingPlayer.isEmpty() && phone != null && !phone.isBlank()) {
            existingPlayer = playerRepository.findByClubId(targetClubId).stream()
                    .filter(p -> PhoneUtils.matches(p.getPhone(), phone))
                    .findFirst();
        }

        if (existingPlayer.isPresent()) {
            Player player = existingPlayer.get();
            if (isInviteProfileSubmitted(player)) {
                throw new IllegalArgumentException(ErrorMessages.PLAYER_PROFILE_COMPLETED);
            }
            if (phone != null && !phone.isEmpty()) {
                player.setPhone(phone);
            }
            if (email != null && !email.isBlank()) {
                player.setEmail(email);
            }
            return playerRepository.save(player);
        }

        if (email != null && !email.isBlank()) {
            Optional<Player> uncategorizedPlayer = playerRepository.findUncategorizedByEmail(email);
            if (uncategorizedPlayer.isPresent()) {
                Player player = uncategorizedPlayer.get();
                player.setClubId(targetClubId);
                if (phone != null && !phone.isEmpty()) {
                    player.setPhone(phone);
                }
                return playerRepository.save(player);
            }
        }

        Player player = new Player();
        player.setClubId(targetClubId);
        player.setSeasonId(auth.getSeasonId());
        player.setTeams(new HashMap<>());
        player.setFirstName("");
        player.setLastName("");
        player.setEmail(email != null ? email : "");
        player.setPhone(phone != null ? phone : "");
        player.setParentName("");
        player.setParentEmail("");
        player.setEmContact("");
        player.setEmPhone("");
        player.setClubs("");
        player.setConsentGiven("");
        player.setStatus("");
        player.setPhotoUploaded("");
        player.setIsActive(false);
        player.setCreatedBy(auth.getId());
        player.setUpdatedBy("");
        player.setCreatedAt(Instant.now());
        player.setUpdatedAt(Instant.now());
        return playerRepository.save(player);
    }

    public boolean checkEmailAvailability(String email, String clubId) {
        Optional<Player> existing = playerRepository.findByEmail(email)
                .filter(p -> clubId.equals(p.getClubId()) && Boolean.TRUE.equals(p.getIsActive()));
        return existing.isEmpty();
    }

    public boolean checkPhoneAvailability(String phone, String clubId) {
        if (phone == null || phone.isBlank()) {
            return true;
        }
        return playerRepository.findByClubId(clubId).stream()
                .noneMatch(p -> PhoneUtils.matches(p.getPhone(), phone) && isInviteProfileSubmitted(p));
    }

    public PagedPlayersResult getUncategorizedPlayersPaged(String clubId, String seasonId, String search, int pageNumber, int pageSize) {
        int safePage = Math.max(1, pageNumber);
        int safeSize = Math.max(1, pageSize);
        Query query = new Query();
        query.addCriteria(Criteria.where("clubId").is(clubId));
        query.addCriteria(Criteria.where("seasonId").is(seasonId));
        query.addCriteria(Criteria.where("isActive").is(true));
        query.addCriteria(new Criteria().orOperator(
                Criteria.where("teams").is(null),
                Criteria.where("teams").is(Collections.emptyMap())
        ));
        String searchTerm = normalizeSearch(search);
        if (searchTerm != null) {
            String pattern = ".*" + java.util.regex.Pattern.quote(searchTerm) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("firstName").regex(pattern, "i"),
                    Criteria.where("lastName").regex(pattern, "i"),
                    Criteria.where("phone").regex(pattern, "i"),
                    Criteria.where("email").regex(pattern, "i")
            ));
        }
        long totalItems = mongoTemplateClient.count(query, Player.class);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, DEFAULT_PLAYER_PAGE_SORT);
        query.with(pageable);
        List<PlayerResponse> rows = mongoTemplateClient.find(query, Player.class).stream().map(this::mapToResponse).collect(Collectors.toList());
        int totalPages = (int) Math.ceil((double) totalItems / safeSize);
        return new PagedPlayersResult(rows, totalItems, totalPages, safePage, safeSize);
    }

    public List<PlayerResponse> getClubsAndPlayers(String seasonId, String search, String clubId, Auth auth) {
        List<Club> clubs = clubRepository.findAll();
        Map<String, String> clubIdToNameMap = clubs.stream()
                .collect(Collectors.toMap(Club::getId, Club::getClubName));

        List<Player> players;
        if (!TenantScope.isControllerWithoutClub(auth)) {
            String scopedClub = auth.getClubId();
            if (clubId != null && !clubId.isEmpty() && TenantScope.denyClubOnly(auth, clubId)) {
                throw new IllegalArgumentException(ErrorMessages.FORBIDDEN);
            }
            players = playerRepository.findByClubIdAndSeasonId(scopedClub, seasonId)
                    .stream()
                    .filter(p -> (p.getFirstName() != null && !p.getFirstName().isEmpty()) &&
                            (p.getLastName() != null && !p.getLastName().isEmpty()) &&
                            Boolean.TRUE.equals(p.getIsActive()))
                    .collect(Collectors.toList());
        } else if (clubId != null && !clubId.isEmpty()) {
            players = playerRepository.findByClubIdAndSeasonId(clubId, seasonId)
                    .stream()
                    .filter(p -> (p.getFirstName() != null && !p.getFirstName().isEmpty()) &&
                            (p.getLastName() != null && !p.getLastName().isEmpty()) &&
                            Boolean.TRUE.equals(p.getIsActive()))
                    .collect(Collectors.toList());
        } else {
            players = playerRepository.findAll()
                    .stream()
                    .filter(p -> seasonId.equals(p.getSeasonId()) &&
                            (p.getFirstName() != null && !p.getFirstName().isEmpty()) &&
                            (p.getLastName() != null && !p.getLastName().isEmpty()) &&
                            Boolean.TRUE.equals(p.getIsActive()))
                    .collect(Collectors.toList());
        }

        if (search != null && !search.isEmpty() && !"undefined".equals(search)) {
            String searchLower = search.toLowerCase();
            players = players.stream()
                    .filter(p -> ((p.getFirstName() != null ? p.getFirstName() : "") + " " + (p.getLastName() != null ? p.getLastName() : "")).toLowerCase().contains(searchLower) ||
                            (p.getEmail() != null && p.getEmail().toLowerCase().contains(searchLower)) ||
                            (p.getFirstName() != null && p.getFirstName().toLowerCase().contains(searchLower)) ||
                            (p.getLastName() != null && p.getLastName().toLowerCase().contains(searchLower)))
                    .collect(Collectors.toList());
        }

        return players.stream()
                .map(p -> {
                    PlayerResponse response = mapToResponse(p);
                    response.setClubName(clubIdToNameMap.get(p.getClubId()));
                    return response;
                })
                .collect(Collectors.toList());
    }

    public PlayerResponse getPlayerByIdWithToken(String id, String token) {
        jwtTokenProvider.validateTokenForPlayerInvite(token, id);

        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.PLAYER_NOT_FOUND));

        PlayerResponse response = mapToResponse(player);
        enrichWithClubAndTeamNames(player, response);
        return response;
    }

    /** Sets clubName (from club lookup) and teamName (comma-separated from teams map) on the response. */
    private void enrichWithClubAndTeamNames(Player player, PlayerResponse response) {
        if (player.getClubId() != null) {
            clubRepository.findById(player.getClubId())
                    .ifPresent(club -> response.setClubName(club.getClubName()));
        }
        if (player.getTeams() != null && !player.getTeams().isEmpty()) {
            response.setTeamName(String.join(", ", player.getTeams().values()));
        }
    }

    public List<PlayerResponse> getAllPlayersNoAuth() {
        return playerRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private void mapToModel(CreatePlayerRequest request, Player player) {
        // Convert teamId string to teams Map
        Map<String, String> teams = new HashMap<>();
        if (request.getTeamId() != null && !request.getTeamId().isEmpty()) {
            String[] teamIds = request.getTeamId().split(",");
            for (String teamId : teamIds) {
                final String trimmedTeamId = teamId.trim(); // Create final variable for lambda
                if (!trimmedTeamId.isEmpty()) {
                    // Fetch team name from repository
                    teamRepository.findById(trimmedTeamId).ifPresent(team -> {
                        teams.put(trimmedTeamId, team.getTeamName());
                    });
                }
            }
        }
        player.setTeams(teams);
        player.setFirstName(request.getFirstName());
        player.setLastName(request.getSurName());
        player.setEmail(request.getEmail());
        player.setPhone(request.getPhone() != null ? request.getPhone() : "");
        player.setParentName(request.getParentName());
        player.setParentEmail(request.getParentEmail());
        player.setEmContact(request.getEmergencyContact());
        player.setEmPhone(request.getEmergencyPhone());
        player.setClubs(request.getOtherClubs());
        player.setConsentGiven(request.getConsentGiven());
        player.setStatus(request.getContractStatus());
        player.setPhotoUploaded(request.getPhotoUploadedDate());
        player.setProfileImage(request.getProfileImage() != null ? request.getProfileImage() : "");
        player.setNotes(request.getNotes());
    }

    /**
     * Adds a team to a player's teams Map and syncs player to team's playersList
     * (Bidirectional sync like Node.js)
     */
    private void addTeamToPlayer(Player player, String teamId, String playerId) {
        // Initialize teams Map if null
        if (player.getTeams() == null) {
            player.setTeams(new HashMap<>());
        }
        
        // Fetch team and add to player's teams Map
        teamRepository.findById(teamId).ifPresent(team -> {
            if (!Boolean.TRUE.equals(team.getIsActive())) {
                throw new IllegalArgumentException(ErrorMessages.TEAM_NOT_FOUND);
            }
            // Add team to player's teams Map (teamId → teamName)
            player.getTeams().put(teamId, team.getTeamName());
            
            // Also add player to team's playersList (bidirectional sync)
            String playersList = team.getPlayersList();
            List<String> playersArray = (playersList != null && !playersList.isEmpty()) 
                    ? new ArrayList<>(Arrays.asList(playersList.split(",")))
                    : new ArrayList<>();
            
            if (!playersArray.contains(playerId)) {
                playersArray.add(playerId);
                team.setPlayersList(String.join(",", playersArray));
                teamRepository.save(team);
            }
        });
    }

    private PlayerResponse mapToResponse(Player player) {
        return PlayerResponse.builder()
                .id(player.getId())
                .clubId(player.getClubId())
                .seasonId(player.getSeasonId())
                .teams(player.getTeams() != null ? player.getTeams() : new HashMap<>())
                .firstName(player.getFirstName())
                .lastName(player.getLastName())
                .email(player.getEmail())
                .phone(player.getPhone())
                .parentName(player.getParentName())
                .parentEmail(player.getParentEmail())
                .emContact(player.getEmContact())
                .emPhone(player.getEmPhone())
                .clubs(player.getClubs())
                .consentGiven(player.getConsentGiven())
                .status(player.getStatus())
                .photoUploaded(player.getPhotoUploaded())
                .profileImage(player.getProfileImage())
                .notes(player.getNotes())
                .isActive(player.getIsActive())
                .build();
    }

    private String normalizeSearch(String search) {
        if (search == null) return null;
        String normalized = search.trim();
        if (normalized.isEmpty() || "undefined".equalsIgnoreCase(normalized) || normalized.length() < 2) return null;
        return normalized;
    }

    public record PagedPlayersResult(
            List<PlayerResponse> players,
            long totalItems,
            int totalPages,
            int currentPage,
            int pageSize) {
    }
}
