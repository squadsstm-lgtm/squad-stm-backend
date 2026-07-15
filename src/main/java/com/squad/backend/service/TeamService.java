package com.squad.backend.service;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.dto.request.team.CreateTeamRequest;
import com.squad.backend.dto.response.team.TeamResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.model.Player;
import com.squad.backend.model.Session;
import com.squad.backend.model.Team;
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
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamService {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final SessionRepository sessionRepository;
    private final MongoTemplate mongoTemplateClient;

    private static final Sort DEFAULT_TEAM_PAGE_SORT =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id"));

    public TeamResponse createTeam(CreateTeamRequest request, Auth auth) {
        Team team = new Team();
        
        // Use clubId from request if provided, otherwise use authenticated user's clubId (like players)
        String clubId = (request.getClubId() != null && !request.getClubId().isEmpty()) 
                ? request.getClubId() 
                : auth.getClubId();
        
        if (clubId == null || clubId.isEmpty()) {
            throw new IllegalArgumentException("Club ID is required");
        }
        if (TenantScope.denyClubOnly(auth, clubId)) {
            throw new IllegalArgumentException(ErrorMessages.FORBIDDEN);
        }

        mapToModel(request, team);
        team.setClubId(clubId); // Set the resolved clubId
        team.setSeasonId(auth.getSeasonId());
        team.setIsActive(true);
        team.setCreatedBy(auth.getId());
        team.setUpdatedBy("");
        Team savedTeam = teamRepository.save(team);
        
        // Update player teams Map (add teamId → teamName)
        if (request.getPlayersList() != null && !request.getPlayersList().isEmpty()) {
            String[] playerIds = request.getPlayersList().split(",");
            for (String playerId : playerIds) {
                if (playerId != null && !playerId.trim().isEmpty()) {
                    playerRepository.findById(playerId.trim()).ifPresent(player -> {
                        // Initialize teams map if null
                        if (player.getTeams() == null) {
                            player.setTeams(new java.util.HashMap<>());
                        }
                        
                        // Add team to map if not already present
                        if (!player.getTeams().containsKey(savedTeam.getId())) {
                            player.getTeams().put(savedTeam.getId(), savedTeam.getTeamName());
                            playerRepository.save(player);
                        }
                    });
                }
            }
        }
        
        return mapToResponse(savedTeam, clubId, auth.getSeasonId());
    }

    public PagedTeamsResult getAllTeamsPaged(
            String clubId,
            String seasonId,
            String search,
            int pageNumber,
            int pageSize,
            Auth auth) {
        if (TenantScope.denyClubScopedEntity(auth, clubId, seasonId)) {
            throw new IllegalArgumentException(ErrorMessages.FORBIDDEN);
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("clubId").is(clubId));
        query.addCriteria(Criteria.where("seasonId").is(seasonId));
        query.addCriteria(Criteria.where("isActive").is(true));

        String normalizedSearch = normalizeSearch(search);
        if (normalizedSearch != null) {
            query.addCriteria(Criteria.where("teamName").regex(normalizedSearch, "i"));
        }

        long totalItems = mongoTemplateClient.count(query, Team.class);
        Pageable pageable = PageRequest.of(Math.max(pageNumber - 1, 0), pageSize, DEFAULT_TEAM_PAGE_SORT);
        query.with(pageable);

        List<Team> pageTeams = mongoTemplateClient.find(query, Team.class);
        List<TeamResponse> teams = mapTeamsToResponses(pageTeams, clubId, seasonId);

        int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalItems / pageSize) : 0;
        return new PagedTeamsResult(teams, totalItems, totalPages, pageNumber, pageSize);
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

    public TeamResponse getTeamById(String id, String clubId, String seasonId, Auth auth) {
        if (TenantScope.denyClubScopedEntity(auth, clubId, seasonId)) {
            throw new IllegalArgumentException(ErrorMessages.FORBIDDEN);
        }
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.TEAM_NOT_FOUND));
        if (!Objects.equals(team.getClubId(), clubId) || !Objects.equals(team.getSeasonId(), seasonId)) {
            throw new IllegalArgumentException(ErrorMessages.TEAM_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(team.getIsActive())) {
            throw new IllegalArgumentException(ErrorMessages.TEAM_NOT_FOUND);
        }
        return mapToResponse(team, clubId, seasonId);
    }

    public TeamResponse updateTeam(String id, CreateTeamRequest request, Auth auth) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.TEAM_NOT_FOUND));
        if (!Boolean.TRUE.equals(team.getIsActive())) {
            throw new IllegalArgumentException(ErrorMessages.TEAM_NOT_FOUND);
        }
        if (TenantScope.denyClubScopedEntity(auth, team.getClubId(), team.getSeasonId())) {
            throw new IllegalArgumentException(ErrorMessages.FORBIDDEN);
        }

        String existingClubId = team.getClubId();
        String existingSeasonId = team.getSeasonId();
        String oldTeamName = team.getTeamName();

        mapToModel(request, team);
        
        team.setClubId(existingClubId);
        team.setSeasonId(existingSeasonId);
        team.setUpdatedBy(auth.getId());

        Team savedTeam = teamRepository.save(team);
        
        // Sync players' teams Map with new playersList (like Node.js lines 167-190)
        String newPlayersList = savedTeam.getPlayersList();
        List<String> newPlayersArray = (newPlayersList != null && !newPlayersList.isEmpty()) 
                ? java.util.Arrays.asList(newPlayersList.split(","))
                : new java.util.ArrayList<>();
        
        // Find all players that currently have this team in their teams Map
        List<Player> playersWithTeam = playerRepository.findByTeamsContainingKey(id);
        
        // Remove team from players NOT in the new list (Node.js lines 170-179)
        for (Player player : playersWithTeam) {
            if (!newPlayersArray.contains(player.getId())) {
                // Player was removed from team - remove team from their teams Map
                if (player.getTeams() != null) {
                    player.getTeams().remove(id);
                    playerRepository.save(player);
                }
            }
        }
        
        // Add team to players IN the new list but don't have it yet (Node.js lines 180-189)
        for (String playerId : newPlayersArray) {
            if (playerId != null && !playerId.trim().isEmpty()) {
                playerRepository.findById(playerId).ifPresent(player -> {
                    if (player.getTeams() == null) {
                        player.setTeams(new java.util.HashMap<>());
                    }
                    if (!player.getTeams().containsKey(id)) {
                        // Player added to team - add team to their teams Map
                        player.getTeams().put(id, savedTeam.getTeamName());
                        playerRepository.save(player);
                    }
                });
            }
        }
        
        // If team name changed, update all players' teams Map with new name
        if (!Objects.equals(oldTeamName, savedTeam.getTeamName())) {
            List<Player> players = playerRepository.findByTeamsContainingKey(id);
            for (Player player : players) {
                if (player.getTeams() != null && player.getTeams().containsKey(id)) {
                    player.getTeams().put(id, savedTeam.getTeamName());
                    playerRepository.save(player);
                }
            }
        }
        
        return mapToResponse(savedTeam, existingClubId, existingSeasonId);
    }

    public void deleteTeam(String id, Auth auth) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.TEAM_NOT_FOUND));
        if (!Boolean.TRUE.equals(team.getIsActive())) {
            throw new IllegalArgumentException(ErrorMessages.TEAM_NOT_FOUND);
        }
        if (TenantScope.denyClubScopedEntity(auth, team.getClubId(), team.getSeasonId())) {
            throw new IllegalArgumentException(ErrorMessages.FORBIDDEN);
        }

        String clubId = team.getClubId();
        String seasonId = team.getSeasonId();
        if (clubId != null && seasonId != null) {
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
                return session.getTeamList() != null && session.getTeamList().contains(id);
            });

            if (hasActiveSession) {
                throw new IllegalArgumentException(ErrorMessages.TEAM_HAS_ACTIVE_SESSION);
            }
        }

        team.setIsActive(false);
        teamRepository.save(team);
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

    public Integer getPlayerCount(String playersId) {
        if (playersId == null || playersId.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid or missing playersId");
        }
        String[] playerIds = playersId.split(",");
        List<Player> activePlayers = playerRepository.findAllById(java.util.Arrays.asList(playerIds))
                .stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .collect(java.util.stream.Collectors.toList());
        return activePlayers.size();
    }

    public void removePlayerFromTeam(String teamId, String playerId, Auth auth) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found"));
        if (!Boolean.TRUE.equals(team.getIsActive())) {
            throw new IllegalArgumentException(ErrorMessages.TEAM_NOT_FOUND);
        }
        if (TenantScope.denyClubScopedEntity(auth, team.getClubId(), team.getSeasonId())) {
            throw new IllegalArgumentException(ErrorMessages.FORBIDDEN);
        }

        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));
        if (!Boolean.TRUE.equals(player.getIsActive())) {
            throw new IllegalArgumentException(ErrorMessages.PLAYER_NOT_FOUND);
        }
        if (TenantScope.denyClubOnly(auth, player.getClubId())) {
            throw new IllegalArgumentException(ErrorMessages.FORBIDDEN);
        }

        List<String> playersArray = team.getPlayersList() != null && !team.getPlayersList().isEmpty() ?
                java.util.Arrays.asList(team.getPlayersList().split(",")) : new java.util.ArrayList<>();
        List<String> updatedPlayersArray = playersArray.stream()
                .filter(p -> !p.equals(playerId))
                .collect(java.util.stream.Collectors.toList());
        team.setPlayersList(String.join(",", updatedPlayersArray));
        teamRepository.save(team);

        // Remove team from player's teams Map
        if (player.getTeams() != null) {
            player.getTeams().remove(teamId);
            playerRepository.save(player);
        }
    }

    private void mapToModel(CreateTeamRequest request, Team team) {
        team.setClubName(request.getClubName());
        team.setTeamName(request.getTeamName());
        team.setLeague(request.getLeague());
        team.setPlayersList(request.getPlayersList());
        team.setNotes(request.getNotes());
    }

    private TeamResponse mapToResponse(Team team, String clubId, String seasonId) {
        return mapTeamsToResponses(List.of(team), clubId, seasonId).get(0);
    }

    /**
     * Batch-enrich teams for a page: one player lookup + one lightweight session scan
     * instead of 3 DB queries per team.
     */
    private List<TeamResponse> mapTeamsToResponses(List<Team> teams, String clubId, String seasonId) {
        if (teams == null || teams.isEmpty()) {
            return List.of();
        }

        Set<String> allPlayerIds = new HashSet<>();
        for (Team team : teams) {
            if (team.getPlayersList() == null || team.getPlayersList().isEmpty()) {
                continue;
            }
            for (String playerId : team.getPlayersList().split(",")) {
                if (playerId != null && !playerId.trim().isEmpty()) {
                    allPlayerIds.add(playerId.trim());
                }
            }
        }

        Map<String, Player> playersById = allPlayerIds.isEmpty()
                ? Map.of()
                : playerRepository.findAllById(allPlayerIds).stream()
                    .collect(Collectors.toMap(Player::getId, p -> p, (a, b) -> a));

        Query sessionQuery = new Query();
        sessionQuery.addCriteria(Criteria.where("clubId").is(clubId));
        sessionQuery.addCriteria(Criteria.where("seasonId").is(seasonId));
        sessionQuery.addCriteria(Criteria.where("isActive").is(true));
        sessionQuery.addCriteria(Criteria.where("sessionType").in("Game", "Training"));
        sessionQuery.fields().include("teamList").include("sessionType");
        List<Session> clubSessions = mongoTemplateClient.find(sessionQuery, Session.class);

        List<TeamResponse> responses = new ArrayList<>(teams.size());
        for (Team team : teams) {
            int playerCount = 0;
            if (team.getPlayersList() != null && !team.getPlayersList().isEmpty()) {
                for (String playerId : team.getPlayersList().split(",")) {
                    if (playerId == null || playerId.trim().isEmpty()) {
                        continue;
                    }
                    Player player = playersById.get(playerId.trim());
                    if (player != null && Boolean.TRUE.equals(player.getIsActive())) {
                        playerCount++;
                    }
                }
            }

            int gameCount = 0;
            int trainingCount = 0;
            String teamId = team.getId();
            for (Session session : clubSessions) {
                if (!teamListContains(session.getTeamList(), teamId)) {
                    continue;
                }
                if ("Game".equalsIgnoreCase(session.getSessionType())) {
                    gameCount++;
                } else if ("Training".equalsIgnoreCase(session.getSessionType())) {
                    trainingCount++;
                }
            }

            responses.add(TeamResponse.builder()
                    .id(team.getId())
                    .seasonId(team.getSeasonId())
                    .clubId(team.getClubId())
                    .clubName(team.getClubName())
                    .teamName(team.getTeamName())
                    .league(team.getLeague())
                    .playersList(team.getPlayersList())
                    .notes(team.getNotes())
                    .isActive(team.getIsActive())
                    .playerCount(playerCount)
                    .gameCount(gameCount)
                    .trainingCount(trainingCount)
                    .build());
        }
        return responses;
    }

    private boolean teamListContains(String teamList, String teamId) {
        if (teamList == null || teamList.isEmpty() || teamId == null || teamId.isEmpty()) {
            return false;
        }
        // Match token boundaries to preserve previous regex semantics without false substring hits.
        for (String token : teamList.split(",")) {
            if (teamId.equals(token.trim())) {
                return true;
            }
        }
        return false;
    }

    public record PagedTeamsResult(
            List<TeamResponse> teams,
            long totalItems,
            int totalPages,
            int currentPage,
            int pageSize
    ) {}
}
