package com.squad.backend.service;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.dto.request.session.CreateSessionRequest;
import com.squad.backend.dto.request.session.MarkAttendanceRequest;
import com.squad.backend.dto.response.session.SessionAttendanceSummaryResponse;
import com.squad.backend.dto.response.session.SessionPlayerStatusResponse;
import com.squad.backend.dto.response.session.SessionResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.model.ConfirmationRequest;
import com.squad.backend.model.Player;
import com.squad.backend.model.Session;
import com.squad.backend.model.Team;
import com.squad.backend.repository.ConfirmationRequestRepository;
import com.squad.backend.repository.PlayerRepository;
import com.squad.backend.repository.SessionRepository;
import com.squad.backend.repository.TeamRepository;
import com.squad.backend.security.TenantScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final SessionRepository sessionRepository;
    private final ConfirmationRequestRepository confirmationRequestRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final ConfirmationRequestService confirmationRequestService;
    private final MongoTemplate mongoTemplateClient;

    private static final Sort DEFAULT_SESSION_PAGE_SORT =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id"));

    public SessionResponse createSession(CreateSessionRequest request, Auth auth) {
        Session session = new Session();
        mapToModel(request, session);
        session.setClubId(auth.getClubId());
        session.setSeasonId(auth.getSeasonId());
        session.setActive(request.getActive());
        session.setIsActive(true);
        session.setCreatedBy(auth.getId());
        session.setUpdatedBy("");
        Session saved = sessionRepository.save(session);
        
        if (Boolean.TRUE.equals(request.getActive())) {
            confirmationRequestService.sendConfirmationRequestsForSession(saved, auth);
        }
        
        return mapToResponse(saved);
    }

    public PagedSessionsResult getAllSessionsPaged(
            String clubId,
            String seasonId,
            String search,
            String sessionType,
            String status,
            int pageNumber,
            int pageSize) {
        Query query = new Query();
        query.addCriteria(Criteria.where("clubId").is(clubId));
        query.addCriteria(Criteria.where("seasonId").is(seasonId));
        query.addCriteria(Criteria.where("isActive").is(true));

        String normalizedSearch = normalizeSearch(search);
        if (normalizedSearch != null) {
            query.addCriteria(Criteria.where("sessionName").regex(normalizedSearch, "i"));
        }

        String normalizedSessionType = normalizeSessionType(sessionType);
        if (normalizedSessionType != null) {
            query.addCriteria(Criteria.where("sessionType")
                    .regex("^" + Pattern.quote(normalizedSessionType) + "$", "i"));
        }

        String normalizedStatus = normalizeStatus(status);
        if (normalizedStatus != null) {
            Set<String> completedSessionIds = confirmationRequestRepository
                    .findByClubIdAndSeasonIdAndAttendanceMarkedAtNotNull(clubId, seasonId)
                    .stream()
                    .map(ConfirmationRequest::getSessionId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            if ("completed".equals(normalizedStatus)) {
                if (completedSessionIds.isEmpty()) {
                    return new PagedSessionsResult(Collections.emptyList(), 0, 0, pageNumber, pageSize);
                }
                query.addCriteria(Criteria.where("_id").in(completedSessionIds));
            } else {
                if (!completedSessionIds.isEmpty()) {
                    query.addCriteria(Criteria.where("_id").nin(completedSessionIds));
                }
                String todayIsoDate = LocalDate.now(ZoneId.systemDefault()).toString();
                if ("upcoming".equals(normalizedStatus)) {
                    query.addCriteria(Criteria.where("date").gte(todayIsoDate));
                } else if ("pending".equals(normalizedStatus)) {
                    query.addCriteria(Criteria.where("date").lt(todayIsoDate));
                }
            }
        }

        long totalItems = mongoTemplateClient.count(query, Session.class);
        Pageable pageable = PageRequest.of(Math.max(pageNumber - 1, 0), pageSize, DEFAULT_SESSION_PAGE_SORT);
        query.with(pageable);

        List<SessionResponse> sessions = mongoTemplateClient.find(query, Session.class).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalItems / pageSize) : 0;
        return new PagedSessionsResult(sessions, totalItems, totalPages, pageNumber, pageSize);
    }

    private String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }
        String normalized = search.trim();
        if (normalized.isEmpty() || "undefined".equalsIgnoreCase(normalized) || normalized.length() < 2) {
            return null;
        }
        return normalized;
    }

    private String normalizeSessionType(String sessionType) {
        if (sessionType == null) {
            return null;
        }
        String normalized = sessionType.trim();
        if (normalized.isEmpty() || "all".equalsIgnoreCase(normalized) || "undefined".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            return null;
        }
        String normalized = status.trim().toLowerCase();
        if (normalized.isEmpty() || "all".equals(normalized) || "undefined".equals(normalized)) {
            return null;
        }
        if ("upcoming".equals(normalized) || "pending".equals(normalized) || "completed".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    public SessionResponse getSessionById(String id, Auth auth) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.SESSION_NOT_FOUND));
        requireActiveSession(session);
        if (TenantScope.denyClubScopedEntity(auth, session.getClubId(), session.getSeasonId())) {
            throw new IllegalArgumentException(ErrorMessages.SESSION_NOT_FOUND);
        }
        return mapToResponse(session);
    }

    public SessionResponse updateSession(String id, CreateSessionRequest request, Auth auth) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.SESSION_NOT_FOUND));
        requireActiveSession(session);
        if (TenantScope.denyClubScopedEntity(auth, session.getClubId(), session.getSeasonId())) {
            throw new IllegalArgumentException(ErrorMessages.SESSION_NOT_FOUND);
        }

        String existingClubId = session.getClubId();
        String existingSeasonId = session.getSeasonId();
        Boolean existingActive = session.getActive();
        
        mapToModel(request, session);
        
        session.setClubId(existingClubId);
        session.setSeasonId(existingSeasonId);
        session.setActive(existingActive);
        session.setUpdatedBy(auth.getId());

        Session saved = sessionRepository.save(session);
        return mapToResponse(saved);
    }

    public void deleteSession(String id, Auth auth) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.SESSION_NOT_FOUND));
        requireActiveSession(session);
        if (TenantScope.denyClubScopedEntity(auth, session.getClubId(), session.getSeasonId())) {
            throw new IllegalArgumentException(ErrorMessages.SESSION_NOT_FOUND);
        }

        if (Boolean.TRUE.equals(session.getActive()) && session.getDate() != null && !session.getDate().isEmpty()) {
            try {
                Instant sessionDate = parseSessionDate(session.getDate());
                if (sessionDate != null && sessionDate.isAfter(Instant.now())) {
                    throw new IllegalArgumentException(ErrorMessages.SESSION_ACTIVE_CANNOT_DELETE);
                }
            } catch (DateTimeParseException ignored) {
                // Invalid date format: allow delete
            }
        }

        session.setIsActive(false);
        sessionRepository.save(session);
        
        List<ConfirmationRequest> requests = confirmationRequestRepository.findBySessionId(id);
        requests.forEach(r -> {
            r.setIsActive(false);
            confirmationRequestRepository.save(r);
        });
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
                    throw e3;
                }
            }
        }
    }

    public List<SessionResponse> getAllSessionsWithoutPagination(String clubId, Auth auth) {
        if (TenantScope.denyClubOnly(auth, clubId)) {
            throw new IllegalArgumentException(ErrorMessages.FORBIDDEN);
        }
        String seasonId = auth.getSeasonId();
        List<Session> sessions = sessionRepository.findByClubIdAndSeasonIdAndIsActive(clubId, seasonId, true);
        return sessions.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<SessionPlayerStatusResponse> getSessionPlayers(
            String sessionId, String teamList, String additionalPlayers, String clubId, Auth auth) {
        Session sessionForAccess = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.SESSION_NOT_FOUND));
        requireActiveSession(sessionForAccess);
        if (clubId != null && !clubId.isEmpty() && !clubId.equals(sessionForAccess.getClubId())) {
            throw new IllegalArgumentException(ErrorMessages.SESSION_NOT_FOUND);
        }
        if (TenantScope.denyClubScopedEntity(auth, sessionForAccess.getClubId(), sessionForAccess.getSeasonId())) {
            throw new IllegalArgumentException(ErrorMessages.FORBIDDEN);
        }

        List<String> teamIds = teamList != null && !teamList.isEmpty() ? 
                Arrays.asList(teamList.split(",")) : new ArrayList<>();
        List<String> additionalPlayerIds = additionalPlayers != null && !additionalPlayers.isEmpty() ? 
                Arrays.asList(additionalPlayers.split(",")) : new ArrayList<>();

        Set<String> allPlayerIds = new HashSet<>();

        for (String teamId : teamIds) {
            teamRepository.findById(teamId).ifPresent(team -> {
                if (team.getPlayersList() != null && !team.getPlayersList().isEmpty()) {
                    allPlayerIds.addAll(Arrays.asList(team.getPlayersList().split(",")));
                }
            });
        }

        allPlayerIds.addAll(additionalPlayerIds);

        List<Player> players = playerRepository.findAllById(allPlayerIds).stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .collect(Collectors.toList());

        List<SessionPlayerStatusResponse> playersWithStatus = players.stream()
                .map(player -> {
                    Optional<ConfirmationRequest> request = confirmationRequestRepository
                            .findBySessionIdAndPlayerId(sessionId, player.getId());

                    return SessionPlayerStatusResponse.builder()
                            .id(player.getId())
                            .firstName(player.getFirstName())
                            .lastName(player.getLastName())
                            .email(player.getEmail())
                            .phone(player.getPhone())
                            .paymentStatus(request.map(ConfirmationRequest::getPayment).orElse("Not available"))
                            .playerAttendanceResponse(request.map(ConfirmationRequest::getPlayerAttendanceResponse).orElse("Not available"))
                            .sessionAttendance(request.map(ConfirmationRequest::getSessionAttendance).orElse("Not available"))
                            .build();
                })
                .collect(Collectors.toList());

        return playersWithStatus;
    }

    public List<SessionResponse> getSessionsByPlayer(String playerId, String clubId, String seasonId, Auth auth) {
        if (TenantScope.denyClubScopedEntity(auth, clubId, seasonId)) {
            throw new IllegalArgumentException(ErrorMessages.FORBIDDEN);
        }
        Player playerEntity = playerRepository.findById(playerId).orElse(null);
        if (playerEntity == null || !Boolean.TRUE.equals(playerEntity.getIsActive())) {
            throw new IllegalArgumentException(ErrorMessages.PLAYER_NOT_FOUND);
        }
        if (TenantScope.denyClubOnly(auth, playerEntity.getClubId())) {
            throw new IllegalArgumentException(ErrorMessages.FORBIDDEN);
        }
        List<Team> teams = teamRepository.findByClubIdAndSeasonId(clubId, seasonId)
                .stream()
                .filter(t -> Boolean.TRUE.equals(t.getIsActive())
                        && t.getPlayersList() != null && t.getPlayersList().contains(playerId))
                .collect(Collectors.toList());

        List<String> teamIds = teams.stream().map(Team::getId).collect(Collectors.toList());
        String commaSeparatedTeamIds = String.join(",", teamIds);

        List<Session> sessions = new ArrayList<>();
        if (!commaSeparatedTeamIds.isEmpty()) {
            for (String teamId : teamIds) {
                sessions.addAll(sessionRepository.findByClubIdAndSeasonIdAndIsActiveAndTeamListRegex(
                        clubId, seasonId, true, teamId));
            }
        }

        sessions.addAll(sessionRepository.findByClubIdAndSeasonIdAndIsActiveAndAdditionalPlayersRegex(
                clubId, seasonId, true, playerId));

        return sessions.stream().distinct().map(this::mapToResponse).collect(Collectors.toList());
    }

    public List<SessionResponse> getSessionsByTeam(String teamId, String clubId, String seasonId, Auth auth) {
        if (TenantScope.denyClubScopedEntity(auth, clubId, seasonId)) {
            throw new IllegalArgumentException(ErrorMessages.FORBIDDEN);
        }
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.TEAM_NOT_FOUND));
        if (!Boolean.TRUE.equals(team.getIsActive())) {
            throw new IllegalArgumentException(ErrorMessages.TEAM_NOT_FOUND);
        }
        if (TenantScope.denyClubScopedEntity(auth, team.getClubId(), team.getSeasonId())) {
            throw new IllegalArgumentException(ErrorMessages.FORBIDDEN);
        }
        return sessionRepository.findByClubIdAndSeasonIdAndIsActiveAndTeamListRegex(
                clubId, seasonId, true, teamId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public Map<String, Long> getSessionTypeCount(String teamId, String clubId, String seasonId, Auth auth) {
        if (TenantScope.denyClubScopedEntity(auth, clubId, seasonId)) {
            throw new IllegalArgumentException(ErrorMessages.FORBIDDEN);
        }
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.TEAM_NOT_FOUND));
        if (!Boolean.TRUE.equals(team.getIsActive())) {
            throw new IllegalArgumentException(ErrorMessages.TEAM_NOT_FOUND);
        }
        if (TenantScope.denyClubScopedEntity(auth, team.getClubId(), team.getSeasonId())) {
            throw new IllegalArgumentException(ErrorMessages.FORBIDDEN);
        }
        Long gameCountLong = sessionRepository.countByClubIdAndSeasonIdAndIsActiveAndTeamListRegexAndSessionType(
                clubId, seasonId, true, teamId, "Game");
        Long trainingCountLong = sessionRepository.countByClubIdAndSeasonIdAndIsActiveAndTeamListRegexAndSessionType(
                clubId, seasonId, true, teamId, "Training");
        
        long gameCount = gameCountLong != null ? gameCountLong : 0L;
        long trainingCount = trainingCountLong != null ? trainingCountLong : 0L;

        Map<String, Long> counts = new HashMap<>();
        counts.put("gameCount", gameCount);
        counts.put("trainingCount", trainingCount);
        return counts;
    }

    public SessionResponse toggleSessionActive(String id, Boolean active, Auth auth) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.SESSION_NOT_FOUND));
        requireActiveSession(session);
        if (TenantScope.denyClubScopedEntity(auth, session.getClubId(), session.getSeasonId())) {
            throw new IllegalArgumentException(ErrorMessages.SESSION_NOT_FOUND);
        }

        session.setActive(active);
        Session saved = sessionRepository.save(session);
        
        if (Boolean.TRUE.equals(active)) {
            confirmationRequestService.sendConfirmationRequestsForSession(saved, auth);
        }
        
        return mapToResponse(saved);
    }

    public void removePlayerFromSession(String sessionId, String playerId, Auth auth) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.SESSION_NOT_FOUND));
        requireActiveSession(session);
        if (TenantScope.denyClubScopedEntity(auth, session.getClubId(), session.getSeasonId())) {
            throw new IllegalArgumentException(ErrorMessages.SESSION_NOT_FOUND);
        }

        List<String> teamListArray = session.getTeamList() != null ? 
                Arrays.asList(session.getTeamList().split(",")) : new ArrayList<>();
        List<String> additionalPlayersArray = session.getAdditionalPlayers() != null ? 
                Arrays.asList(session.getAdditionalPlayers().split(",")) : new ArrayList<>();

        for (String teamId : teamListArray) {
            teamRepository.findById(teamId).ifPresent(team -> {
                if (team.getPlayersList() != null && team.getPlayersList().contains(playerId)) {
                    throw new IllegalArgumentException("Player is in a team and cannot be deleted");
                }
            });
        }

        if (additionalPlayersArray.contains(playerId)) {
            additionalPlayersArray.remove(playerId);
            session.setAdditionalPlayers(String.join(",", additionalPlayersArray));
            sessionRepository.save(session);
        } else {
            throw new IllegalArgumentException("Player not found in the session");
        }
    }

    public SessionAttendanceSummaryResponse markAttendance(String sessionId, MarkAttendanceRequest request, Auth auth) {
        Session sessionForAccess = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.SESSION_NOT_FOUND));
        requireActiveSession(sessionForAccess);
        if (TenantScope.denyClubScopedEntity(auth, sessionForAccess.getClubId(), sessionForAccess.getSeasonId())) {
            throw new IllegalArgumentException(ErrorMessages.SESSION_NOT_FOUND);
        }

        if (request.getAttendance() == null || request.getAttendance().isEmpty()) {
            throw new IllegalArgumentException("Missing required field: attendance array");
        }

        String markedBy = auth.getId();
        String markedByName = (auth.getFirstName() != null ? auth.getFirstName() : "") + " " + 
                (auth.getLastName() != null ? auth.getLastName() : "");
        markedByName = markedByName.trim().isEmpty() ? "Unknown" : markedByName.trim();
        LocalDateTime markedAt = LocalDateTime.now();

        for (MarkAttendanceRequest.AttendanceItem item : request.getAttendance()) {
            Optional<ConfirmationRequest> confirmationRequest = confirmationRequestRepository
                    .findBySessionIdAndPlayerId(sessionId, item.getPlayerId());

            if (confirmationRequest.isPresent()) {
                ConfirmationRequest req = confirmationRequest.get();
                req.setSessionAttendance(item.getStatus());
                req.setAttendanceMarkedBy(markedBy);
                req.setAttendanceMarkedByName(markedByName);
                req.setAttendanceMarkedAt(markedAt);
                confirmationRequestRepository.save(req);
            }
        }

        long attendedCount = request.getAttendance().stream()
                .filter(a -> "attended".equals(a.getStatus()))
                .count();
        long absentCount = request.getAttendance().stream()
                .filter(a -> "absent".equals(a.getStatus()))
                .count();

        return SessionAttendanceSummaryResponse.builder()
                .sessionId(sessionId)
                .markedBy(markedBy)
                .markedByName(markedByName)
                .markedAt(markedAt)
                .totalPlayers(request.getAttendance().size())
                .attended(attendedCount)
                .absent(absentCount)
                .build();
    }

    private void requireActiveSession(Session session) {
        if (!Boolean.TRUE.equals(session.getIsActive())) {
            throw new IllegalArgumentException(ErrorMessages.SESSION_NOT_FOUND);
        }
    }

    private void mapToModel(CreateSessionRequest request, Session session) {
        session.setSessionName(request.getSessionName());
        session.setSessionType(request.getSessionType());
        session.setLocation(request.getLocation());
        session.setTeamList(request.getTeamList());
        session.setDate(request.getDate());
        session.setPrice(request.getPrice());
        session.setAdditionalPlayers(request.getAdditionalPlayers());
        session.setNotes(request.getNotes());
    }

    private SessionResponse mapToResponse(Session session) {
        int confirmedCount = confirmationRequestRepository.findBySessionId(session.getId()).size();
        int pendingCount = 0;

        List<ConfirmationRequest> requests = confirmationRequestRepository.findBySessionId(session.getId());
        Optional<ConfirmationRequest> anyMarked = requests.stream()
                .filter(r -> r.getAttendanceMarkedAt() != null)
                .findFirst();
        Boolean attendanceMarked = anyMarked.isPresent();
        LocalDateTime attendanceMarkedAt = anyMarked.map(ConfirmationRequest::getAttendanceMarkedAt).orElse(null);
        String attendanceMarkedBy = anyMarked.map(ConfirmationRequest::getAttendanceMarkedBy).orElse(null);
        String attendanceMarkedByName = anyMarked.map(ConfirmationRequest::getAttendanceMarkedByName).orElse(null);

        return SessionResponse.builder()
                .id(session.getId())
                .seasonId(session.getSeasonId())
                .clubId(session.getClubId())
                .active(session.getActive())
                .sessionName(session.getSessionName())
                .sessionType(session.getSessionType())
                .location(session.getLocation())
                .teamList(session.getTeamList())
                .date(session.getDate())
                .price(session.getPrice())
                .additionalPlayers(session.getAdditionalPlayers())
                .notes(session.getNotes())
                .isActive(session.getIsActive())
                .confirmedCount(confirmedCount)
                .pendingCount(pendingCount)
                .attendanceMarked(attendanceMarked)
                .attendanceMarkedAt(attendanceMarkedAt)
                .attendanceMarkedBy(attendanceMarkedBy)
                .attendanceMarkedByName(attendanceMarkedByName)
                .build();
    }

    public record PagedSessionsResult(
            List<SessionResponse> sessions,
            long totalItems,
            int totalPages,
            int currentPage,
            int pageSize
    ) {}
}
