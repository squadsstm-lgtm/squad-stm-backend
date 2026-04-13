package com.squad.backend.service;

import com.squad.backend.dto.response.ClubCountResponse;
import com.squad.backend.dto.response.CountResponse;
import com.squad.backend.model.Player;
import com.squad.backend.model.Team;
import com.squad.backend.repository.SessionRepository;
import com.squad.backend.model.Club;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.FacetOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CountService {

    private final SessionRepository sessionRepository;
    private final MongoTemplate mongoTemplate;

    public CountResponse getCounts(String clubId, String seasonId) {
        PlayerCounts playerCounts = getPlayerCountsByAggregation(clubId, seasonId);
        TeamCounts teamCounts = getTeamCountsByAggregation(clubId, seasonId);
        Long sessionCountLong = sessionRepository.countByClubIdAndSeasonIdAndIsActive(clubId, seasonId, true);
        long sessionCount = sessionCountLong != null ? sessionCountLong : 0L;

        Instant startOfMonth = YearMonth.now().atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        long newSessionsThisMonth = sessionRepository.countByClubIdAndSeasonIdAndCreatedAtGreaterThanEqual(clubId, seasonId, startOfMonth);

        int playersInTeams = playerCounts.total - playerCounts.withoutTeam;
        int avgPlayersPerTeam = teamCounts.total == 0 ? 0
                : (int) Math.round((double) playersInTeams / teamCounts.total);

        return CountResponse.builder()
                .playerCount(playerCounts.total)
                .activePlayerCount(playerCounts.active)
                .inactivePlayerCount(playerCounts.inactive)
                .playersWithoutTeam(playerCounts.withoutTeam)
                .newPlayersThisMonth(playerCounts.newThisMonth)
                .teamCount(teamCounts.total)
                .activeTeamCount(teamCounts.active)
                .inactiveTeamCount(teamCounts.inactive)
                .avgPlayersPerTeam(avgPlayersPerTeam)
                .newTeamsThisMonth(teamCounts.newThisMonth)
                .sessionCount((int) sessionCount)
                .newSessionsThisMonth((int) newSessionsThisMonth)
                .build();
    }

    /**
     * Single aggregation for all player counts (total, active, inactive, without team, new this month).
     */
    private PlayerCounts getPlayerCountsByAggregation(String clubId, String seasonId) {
        Instant startOfMonth = YearMonth.now().atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Criteria withoutTeamCriteria = new Criteria().orOperator(
                Criteria.where("teams").is(null),
                Criteria.where("teams").exists(false),
                Criteria.where("teams").is(Collections.emptyMap()));

        FacetOperation facet = Aggregation.facet(
                        Aggregation.count().as("count"))
                .as("total")
                .and(
                        Aggregation.match(Criteria.where("isActive").is(true)),
                        Aggregation.count().as("count"))
                .as("active")
                .and(
                        Aggregation.match(Criteria.where("isActive").is(false)),
                        Aggregation.count().as("count"))
                .as("inactive")
                .and(
                        Aggregation.match(withoutTeamCriteria),
                        Aggregation.count().as("count"))
                .as("withoutTeam")
                .and(
                        Aggregation.match(Criteria.where("createdAt").gte(startOfMonth)),
                        Aggregation.count().as("count"))
                .as("newThisMonth");

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("clubId").is(clubId).and("seasonId").is(seasonId)),
                facet);

        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, Player.class, Document.class);
        Document doc = results.getUniqueMappedResult();

        if (doc == null) {
            return new PlayerCounts(0, 0, 0, 0, 0);
        }

        return new PlayerCounts(
                extractCount(doc, "total"),
                extractCount(doc, "active"),
                extractCount(doc, "inactive"),
                extractCount(doc, "withoutTeam"),
                extractCount(doc, "newThisMonth"));
    }

    private static int extractCount(Document doc, String facetName) {
        List<?> list = doc.getList(facetName, Document.class);
        if (list == null || list.isEmpty()) {
            return 0;
        }
        Object first = list.getFirst();
        if (first instanceof Document d) {
            Object count = d.get("count");
            if (count instanceof Number n) {
                return n.intValue();
            }
        }
        return 0;
    }

    private record PlayerCounts(int total, int active, int inactive, int withoutTeam, int newThisMonth) {}

    /**
     * Single aggregation for all team counts (total, active, inactive, new this month).
     */
    private TeamCounts getTeamCountsByAggregation(String clubId, String seasonId) {
        Instant startOfMonth = YearMonth.now().atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        FacetOperation facet = Aggregation.facet(
                        Aggregation.count().as("count"))
                .as("total")
                .and(
                        Aggregation.match(Criteria.where("isActive").is(true)),
                        Aggregation.count().as("count"))
                .as("active")
                .and(
                        Aggregation.match(Criteria.where("isActive").is(false)),
                        Aggregation.count().as("count"))
                .as("inactive")
                .and(
                        Aggregation.match(Criteria.where("createdAt").gte(startOfMonth)),
                        Aggregation.count().as("count"))
                .as("newThisMonth");

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("clubId").is(clubId).and("seasonId").is(seasonId)),
                facet);

        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, Team.class, Document.class);
        Document doc = results.getUniqueMappedResult();

        if (doc == null) {
            return new TeamCounts(0, 0, 0, 0);
        }

        return new TeamCounts(
                extractCount(doc, "total"),
                extractCount(doc, "active"),
                extractCount(doc, "inactive"),
                extractCount(doc, "newThisMonth"));
    }

    private record TeamCounts(int total, int active, int inactive, int newThisMonth) {}

    public List<ClubCountResponse> getClubsWithCounts(String seasonId, String clubIdFilter) {
        return queryClubsWithCounts(seasonId, clubIdFilter, null).clubs();
    }

    public ClubsWithCountsPagedResult getClubsWithCountsPaged(String seasonId, String clubIdFilter, int pageNumber, int pageSize) {
        int safePage = Math.max(1, pageNumber);
        int safeSize = Math.max(1, pageSize);
        Pageable pageable = PageRequest.of(
                safePage - 1,
                safeSize,
                Sort.by(Sort.Order.asc("_id")));
        ClubsWithCountsQueryResult result = queryClubsWithCounts(seasonId, clubIdFilter, pageable);
        int totalPages = (int) Math.ceil((double) result.totalItems() / safeSize);
        return new ClubsWithCountsPagedResult(
                result.clubs(),
                result.totalItems(),
                totalPages,
                safePage,
                safeSize
        );
    }

    private ClubsWithCountsQueryResult queryClubsWithCounts(String seasonId, String clubIdFilter, Pageable pageable) {
        Criteria criteria = buildClubCriteria(seasonId, clubIdFilter);
        Query baseQuery = new Query(criteria).with(Sort.by(Sort.Order.asc("_id")));
        long totalItems = mongoTemplate.count(new Query(criteria), Club.class);

        List<Club> clubs;
        if (pageable != null) {
            clubs = mongoTemplate.find(baseQuery.with(pageable), Club.class);
        } else {
            clubs = mongoTemplate.find(baseQuery, Club.class);
        }
        if (clubs.isEmpty()) {
            return new ClubsWithCountsQueryResult(List.of(), totalItems);
        }

        Set<String> clubIds = clubs.stream().map(Club::getId).collect(Collectors.toSet());
        Map<String, Integer> teamCounts = countByClub("teams", clubIds, seasonId, null);
        Map<String, Integer> sessionCounts = countByClub("sessions", clubIds, seasonId, null);

        List<Criteria> playerCriteriaList = new ArrayList<>();
        playerCriteriaList.add(Criteria.where("isActive").is(true));
        playerCriteriaList.add(Criteria.where("firstName").ne(""));
        playerCriteriaList.add(Criteria.where("lastName").ne(""));
        Map<String, Integer> playerCounts = countByClub("players", clubIds, seasonId, playerCriteriaList);

        List<ClubCountResponse> rows = clubs.stream()
                .map(club -> ClubCountResponse.builder()
                        .clubId(club.getId())
                        .clubname(club.getClubName())
                        .noofteams(teamCounts.getOrDefault(club.getId(), 0))
                        .noofsessions(sessionCounts.getOrDefault(club.getId(), 0))
                        .noofplayers(playerCounts.getOrDefault(club.getId(), 0))
                        .build())
                .collect(Collectors.toList());

        return new ClubsWithCountsQueryResult(rows, totalItems);
    }

    private Criteria buildClubCriteria(String seasonId, String clubIdFilter) {
        List<Criteria> andList = new ArrayList<>();
        if (seasonId != null && !seasonId.isBlank()) {
            andList.add(Criteria.where("seasonId").is(seasonId));
        }
        if (clubIdFilter != null && !clubIdFilter.isBlank()) {
            andList.add(Criteria.where("_id").is(clubIdFilter));
        }
        if (andList.isEmpty()) return new Criteria();
        return new Criteria().andOperator(andList.toArray(new Criteria[0]));
    }

    private Map<String, Integer> countByClub(
            String collection,
            Set<String> clubIds,
            String seasonId,
            List<Criteria> extraCriteria) {
        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(Criteria.where("clubId").in(clubIds));
        if (seasonId != null && !seasonId.isBlank()) {
            criteriaList.add(Criteria.where("seasonId").is(seasonId));
        }
        if (extraCriteria != null && !extraCriteria.isEmpty()) {
            criteriaList.addAll(extraCriteria);
        }
        Criteria matchCriteria = new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(matchCriteria),
                Aggregation.group("clubId").count().as("count")
        );
        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, collection, Document.class);
        Map<String, Integer> map = new HashMap<>();
        for (Document doc : results.getMappedResults()) {
            String clubId = doc.getString("_id");
            Object countObj = doc.get("count");
            int count = countObj instanceof Number n ? n.intValue() : 0;
            map.put(clubId, count);
        }
        return map;
    }

    private record ClubsWithCountsQueryResult(List<ClubCountResponse> clubs, long totalItems) {}

    public record ClubsWithCountsPagedResult(
            List<ClubCountResponse> clubs,
            long totalItems,
            int totalPages,
            int currentPage,
            int pageSize) {
    }
}
