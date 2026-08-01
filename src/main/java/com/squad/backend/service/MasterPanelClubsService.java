package com.squad.backend.service;

import com.squad.backend.constants.WithdrawalStatus;
import com.squad.backend.dto.response.CountResponse;
import com.squad.backend.dto.response.PageMetaResponse;
import com.squad.backend.dto.response.clubwallet.ClubWalletResponse;
import com.squad.backend.dto.response.masterpanel.MasterClubAdminContactResponse;
import com.squad.backend.dto.response.masterpanel.MasterClubDetailResponse;
import com.squad.backend.dto.response.masterpanel.MasterClubListItemResponse;
import com.squad.backend.dto.response.masterpanel.MasterClubRecentItemResponse;
import com.squad.backend.dto.response.masterpanel.MasterClubsListResponse;
import com.squad.backend.dto.response.masterpanel.MasterClubsPlatformSummaryResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.model.Club;
import com.squad.backend.model.ClubWallet;
import com.squad.backend.model.ConfirmationRequest;
import com.squad.backend.model.Season;
import com.squad.backend.model.Transaction;
import com.squad.backend.model.WithdrawalRequest;
import com.squad.backend.repository.AuthRepository;
import com.squad.backend.repository.ClubRepository;
import com.squad.backend.repository.ClubWalletRepository;
import com.squad.backend.repository.SeasonRepository;
import com.squad.backend.utils.AmountParseUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Platform-scope club overview for Master Panel Controllers.
 * Uses batch Mongo queries (no N+1) so list + summary stay fast as club count grows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterPanelClubsService {

    private static final List<String> OPEN_WITHDRAWAL_STATUSES = List.of(
            WithdrawalStatus.WAITING_FOR_APPROVAL,
            WithdrawalStatus.VERIFIED,
            WithdrawalStatus.PROCESSING
    );

    private final MongoTemplate mongoTemplate;
    private final ClubRepository clubRepository;
    private final ClubWalletRepository clubWalletRepository;
    private final AuthRepository authRepository;
    private final SeasonRepository seasonRepository;
    private final CountService countService;
    private final ClubWalletService clubWalletService;

    public MasterClubsPlatformSummaryResponse getPlatformSummary(String seasonId) {
        List<Club> clubs = findClubs(seasonId, null);
        if (clubs.isEmpty()) {
            return emptySummary();
        }

        Set<String> clubIds = clubs.stream().map(Club::getId).collect(Collectors.toSet());
        Map<String, Integer> players = countActivePlayersByClub(clubIds, seasonId);
        Map<String, Integer> teams = countByClub("teams", clubIds, seasonId, null);
        // Match CountService: only active sessions
        Map<String, Integer> sessions = countByClub(
                "sessions", clubIds, seasonId, List.of(Criteria.where("isActive").is(true)));
        Map<String, ClubWallet> wallets = loadWalletsByClubId(clubIds);
        Map<String, Double> outstanding = sumOutstandingByClub(clubIds, seasonId);
        Map<String, Integer> openWithdrawals = countOpenWithdrawalsByClub(clubIds);

        long activeClubs = 0;
        long emptyClubs = 0;
        long totalPlayers = 0;
        long totalTeams = 0;
        long totalSessions = 0;
        double totalEarnings = 0;
        double totalOutstanding = 0;
        double totalAvailable = 0;
        double totalPendingWithdrawals = 0;
        long openWithdrawalCount = 0;

        for (Club club : clubs) {
            String id = club.getId();
            int playerCount = players.getOrDefault(id, 0);
            int teamCount = teams.getOrDefault(id, 0);
            int sessionCount = sessions.getOrDefault(id, 0);
            totalPlayers += playerCount;
            totalTeams += teamCount;
            totalSessions += sessionCount;
            if (playerCount > 0) {
                activeClubs++;
            } else {
                emptyClubs++;
            }

            ClubWallet wallet = wallets.get(id);
            if (wallet != null) {
                totalEarnings += nz(wallet.getTotalEarnings());
                totalAvailable += nz(wallet.getAvailableForWithdrawal());
                totalPendingWithdrawals += nz(wallet.getPendingWithdrawals());
            }
            totalOutstanding += outstanding.getOrDefault(id, 0.0);
            openWithdrawalCount += openWithdrawals.getOrDefault(id, 0);
        }

        return MasterClubsPlatformSummaryResponse.builder()
                .totalClubs(clubs.size())
                .activeClubs(activeClubs)
                .emptyClubs(emptyClubs)
                .totalPlayers(totalPlayers)
                .totalTeams(totalTeams)
                .totalSessions(totalSessions)
                .totalEarnings(round2(totalEarnings))
                .totalOutstanding(round2(totalOutstanding))
                .totalAvailableForWithdrawal(round2(totalAvailable))
                .totalPendingWithdrawals(round2(totalPendingWithdrawals))
                .openWithdrawalCount(openWithdrawalCount)
                .build();
    }

    public MasterClubsListResponse getClubs(
            String seasonId,
            String search,
            String statusFilter,
            int page,
            int limit) {
        int safePage = Math.max(1, page);
        int safeLimit = Math.max(1, Math.min(limit, 100));

        List<Club> clubs = findClubs(seasonId, search);
        if (clubs.isEmpty()) {
            return MasterClubsListResponse.builder()
                    .clubs(List.of())
                    .pagination(pageMeta(safePage, safeLimit, 0))
                    .build();
        }

        Set<String> clubIds = clubs.stream().map(Club::getId).collect(Collectors.toSet());
        Map<String, Integer> players = countActivePlayersByClub(clubIds, seasonId);
        Map<String, Integer> teams = countByClub("teams", clubIds, seasonId, null);
        // Match CountService: only active sessions
        Map<String, Integer> sessions = countByClub(
                "sessions", clubIds, seasonId, List.of(Criteria.where("isActive").is(true)));
        Map<String, ClubWallet> wallets = loadWalletsByClubId(clubIds);
        Map<String, Double> outstanding = sumOutstandingByClub(clubIds, seasonId);
        Map<String, Integer> openWithdrawals = countOpenWithdrawalsByClub(clubIds);
        Map<String, Auth> primaryAdmins = loadPrimaryAdmins(clubIds);

        List<MasterClubListItemResponse> rows = clubs.stream()
                .map(club -> {
                    String id = club.getId();
                    int playerCount = players.getOrDefault(id, 0);
                    int teamCount = teams.getOrDefault(id, 0);
                    int sessionCount = sessions.getOrDefault(id, 0);
                    ClubWallet wallet = wallets.get(id);
                    Auth admin = primaryAdmins.get(id);
                    return MasterClubListItemResponse.builder()
                            .clubId(id)
                            .clubName(club.getClubName())
                            .seasonId(club.getSeasonId())
                            .status(deriveStatus(playerCount, teamCount, sessionCount))
                            .playerCount(playerCount)
                            .teamCount(teamCount)
                            .sessionCount(sessionCount)
                            .totalEarnings(round2(wallet != null ? nz(wallet.getTotalEarnings()) : 0))
                            .outstandingAmount(round2(outstanding.getOrDefault(id, 0.0)))
                            .availableForWithdrawal(round2(wallet != null ? nz(wallet.getAvailableForWithdrawal()) : 0))
                            .pendingWithdrawals(round2(wallet != null ? nz(wallet.getPendingWithdrawals()) : 0))
                            .openWithdrawalCount(openWithdrawals.getOrDefault(id, 0))
                            .primaryAdminName(admin != null ? displayName(admin) : null)
                            .primaryAdminEmail(admin != null ? admin.getEmail() : null)
                            .build();
                })
                .filter(row -> matchesStatusFilter(row, statusFilter))
                .sorted(Comparator.comparing(
                        (MasterClubListItemResponse r) -> r.getClubName() == null ? "" : r.getClubName(),
                        String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        long total = rows.size();
        int from = Math.min((safePage - 1) * safeLimit, rows.size());
        int to = Math.min(from + safeLimit, rows.size());
        List<MasterClubListItemResponse> pageRows = rows.subList(from, to);

        return MasterClubsListResponse.builder()
                .clubs(pageRows)
                .pagination(pageMeta(safePage, safeLimit, total))
                .build();
    }

    public MasterClubDetailResponse getClubDetail(String clubId, String seasonId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new IllegalArgumentException("Club not found"));

        String effectiveSeasonId = (seasonId != null && !seasonId.isBlank())
                ? seasonId
                : club.getSeasonId();

        CountResponse counts = countService.getCounts(clubId, effectiveSeasonId);
        ClubWalletResponse wallet = clubWalletRepository.findByClubId(clubId)
                .map(clubWalletService::mapWalletToResponse)
                .orElse(ClubWalletResponse.builder()
                        .clubId(clubId)
                        .totalEarnings(0.0)
                        .lockedEarnings(0.0)
                        .availableForWithdrawal(0.0)
                        .totalWithdrawn(0.0)
                        .pendingWithdrawals(0.0)
                        .processingFees(0.0)
                        .monthlyRevenue(0.0)
                        .yearlyRevenue(0.0)
                        .totalRefunds(0.0)
                        .build());

        OutstandingTotals outstanding = sumOutstandingForClub(clubId, effectiveSeasonId);
        List<MasterClubAdminContactResponse> admins = loadClubAdmins(clubId);
        List<MasterClubRecentItemResponse> recentPayments = loadRecentPayments(clubId, 5);
        List<MasterClubRecentItemResponse> recentWithdrawals = loadRecentWithdrawals(clubId, 5);

        int playerCount = counts.getActivePlayerCount() != null ? counts.getActivePlayerCount() : 0;
        int teamCount = counts.getTeamCount() != null ? counts.getTeamCount() : 0;
        int sessionCount = counts.getSessionCount() != null ? counts.getSessionCount() : 0;
        String status = deriveStatus(playerCount, teamCount, sessionCount);

        String seasonLabel = null;
        if (effectiveSeasonId != null) {
            seasonLabel = seasonRepository.findById(effectiveSeasonId)
                    .map(Season::getYear)
                    .orElse(null);
        }

        return MasterClubDetailResponse.builder()
                .clubId(club.getId())
                .clubName(club.getClubName())
                .seasonId(effectiveSeasonId)
                .seasonLabel(seasonLabel)
                .status(status)
                .playerCount(playerCount)
                .activePlayerCount(counts.getActivePlayerCount())
                .inactivePlayerCount(counts.getInactivePlayerCount())
                .playersWithoutTeam(counts.getPlayersWithoutTeam())
                .teamCount(teamCount)
                .sessionCount(sessionCount)
                .newSessionsThisMonth(counts.getNewSessionsThisMonth())
                .outstandingAmount(round2(outstanding.amount()))
                .outstandingCount(outstanding.count())
                .wallet(wallet)
                .admins(admins)
                .healthFlags(buildHealthFlags(playerCount, sessionCount, outstanding.amount(), wallet))
                .recentPayments(recentPayments)
                .recentWithdrawals(recentWithdrawals)
                .build();
    }

    private MasterClubsPlatformSummaryResponse emptySummary() {
        return MasterClubsPlatformSummaryResponse.builder()
                .totalClubs(0)
                .activeClubs(0)
                .emptyClubs(0)
                .totalPlayers(0)
                .totalTeams(0)
                .totalSessions(0)
                .totalEarnings(0)
                .totalOutstanding(0)
                .totalAvailableForWithdrawal(0)
                .totalPendingWithdrawals(0)
                .openWithdrawalCount(0)
                .build();
    }

    private List<Club> findClubs(String seasonId, String search) {
        List<Criteria> and = new ArrayList<>();
        if (seasonId != null && !seasonId.isBlank()) {
            and.add(Criteria.where("seasonId").is(seasonId));
        }
        if (search != null && !search.isBlank()) {
            and.add(Criteria.where("clubName").regex(Pattern.quote(search.trim()), "i"));
        }
        Criteria criteria = and.isEmpty()
                ? new Criteria()
                : new Criteria().andOperator(and.toArray(new Criteria[0]));
        return mongoTemplate.find(
                new Query(criteria).with(Sort.by(Sort.Order.asc("clubName"))),
                Club.class);
    }

    private Map<String, ClubWallet> loadWalletsByClubId(Set<String> clubIds) {
        if (clubIds.isEmpty()) {
            return Map.of();
        }
        Query query = new Query(Criteria.where("clubId").in(clubIds));
        List<ClubWallet> wallets = mongoTemplate.find(query, ClubWallet.class);
        Map<String, ClubWallet> map = new HashMap<>();
        for (ClubWallet wallet : wallets) {
            if (wallet.getClubId() != null) {
                map.put(wallet.getClubId(), wallet);
            }
        }
        return map;
    }

    private Map<String, Integer> countActivePlayersByClub(Set<String> clubIds, String seasonId) {
        List<Criteria> extra = new ArrayList<>();
        extra.add(Criteria.where("isActive").is(true));
        extra.add(Criteria.where("firstName").ne(""));
        extra.add(Criteria.where("lastName").ne(""));
        return countByClub("players", clubIds, seasonId, extra);
    }

    private Map<String, Integer> countByClub(
            String collection,
            Set<String> clubIds,
            String seasonId,
            List<Criteria> extraCriteria) {
        if (clubIds.isEmpty()) {
            return Map.of();
        }
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

    /**
     * Outstanding = attended + unpaid confirmation requests (same definition as Finance pending).
     * Loads only clubId + amount fields, then sums in Java (amount is stored as string).
     */
    private Map<String, Double> sumOutstandingByClub(Set<String> clubIds, String seasonId) {
        if (clubIds.isEmpty()) {
            return Map.of();
        }
        List<Criteria> and = new ArrayList<>();
        and.add(Criteria.where("clubId").in(clubIds));
        and.add(Criteria.where("playerAttendanceResponse").is("Yes"));
        and.add(Criteria.where("isActive").is(true));
        and.add(Criteria.where("payment").is("No"));
        if (seasonId != null && !seasonId.isBlank()) {
            and.add(Criteria.where("seasonId").is(seasonId));
        }
        Query query = new Query(new Criteria().andOperator(and.toArray(new Criteria[0])));
        query.fields().include("clubId").include("amount");
        List<ConfirmationRequest> rows = mongoTemplate.find(query, ConfirmationRequest.class);

        Map<String, Double> map = new HashMap<>();
        for (ConfirmationRequest row : rows) {
            if (row.getClubId() == null) {
                continue;
            }
            map.merge(row.getClubId(), AmountParseUtils.parseToDoubleSafe(row.getAmount()), Double::sum);
        }
        return map;
    }

    private OutstandingTotals sumOutstandingForClub(String clubId, String seasonId) {
        List<Criteria> and = new ArrayList<>();
        and.add(Criteria.where("clubId").is(clubId));
        and.add(Criteria.where("playerAttendanceResponse").is("Yes"));
        and.add(Criteria.where("isActive").is(true));
        and.add(Criteria.where("payment").is("No"));
        if (seasonId != null && !seasonId.isBlank()) {
            and.add(Criteria.where("seasonId").is(seasonId));
        }
        Query query = new Query(new Criteria().andOperator(and.toArray(new Criteria[0])));
        query.fields().include("amount");
        List<ConfirmationRequest> rows = mongoTemplate.find(query, ConfirmationRequest.class);
        double amount = rows.stream()
                .mapToDouble(r -> AmountParseUtils.parseToDoubleSafe(r.getAmount()))
                .sum();
        return new OutstandingTotals(round2(amount), rows.size());
    }

    private Map<String, Integer> countOpenWithdrawalsByClub(Set<String> clubIds) {
        if (clubIds.isEmpty()) {
            return Map.of();
        }
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("clubId").in(clubIds)
                        .and("status").in(OPEN_WITHDRAWAL_STATUSES)),
                Aggregation.group("clubId").count().as("count")
        );
        AggregationResults<Document> results =
                mongoTemplate.aggregate(aggregation, "withdrawalrequests", Document.class);
        Map<String, Integer> map = new HashMap<>();
        for (Document doc : results.getMappedResults()) {
            String clubId = doc.getString("_id");
            Object countObj = doc.get("count");
            int count = countObj instanceof Number n ? n.intValue() : 0;
            map.put(clubId, count);
        }
        return map;
    }

    private Map<String, Auth> loadPrimaryAdmins(Set<String> clubIds) {
        if (clubIds.isEmpty()) {
            return Map.of();
        }
        Query query = new Query(Criteria.where("clubId").in(clubIds)
                .and("role").regex("^Admin$", "i")
                .and("isBlocked").ne(true));
        query.fields().include("clubId").include("firstName").include("lastName")
                .include("userName").include("email").include("role");
        List<Auth> admins = mongoTemplate.find(query, Auth.class);

        Map<String, Auth> map = new HashMap<>();
        for (Auth admin : admins) {
            if (admin.getClubId() == null) {
                continue;
            }
            map.putIfAbsent(admin.getClubId(), admin);
        }

        // Fallback: any non-blocked user on the club if no Admin role found
        Set<String> missing = new HashSet<>(clubIds);
        missing.removeAll(map.keySet());
        if (!missing.isEmpty()) {
            Query fallback = new Query(Criteria.where("clubId").in(missing).and("isBlocked").ne(true));
            fallback.fields().include("clubId").include("firstName").include("lastName")
                    .include("userName").include("email").include("role");
            for (Auth user : mongoTemplate.find(fallback, Auth.class)) {
                if (user.getClubId() != null) {
                    map.putIfAbsent(user.getClubId(), user);
                }
            }
        }
        return map;
    }

    private List<MasterClubAdminContactResponse> loadClubAdmins(String clubId) {
        List<Auth> users = authRepository.findByClubId(clubId).stream()
                .filter(a -> !Boolean.TRUE.equals(a.getIsBlocked()))
                .filter(a -> a.getRole() != null && (
                        a.getRole().equalsIgnoreCase("Admin")
                                || a.getRole().equalsIgnoreCase("Treasurer")))
                .sorted(Comparator.comparing(a -> a.getRole() == null ? "" : a.getRole()))
                .collect(Collectors.toList());

        if (users.isEmpty()) {
            users = authRepository.findByClubId(clubId).stream()
                    .filter(a -> !Boolean.TRUE.equals(a.getIsBlocked()))
                    .limit(5)
                    .collect(Collectors.toList());
        }

        return users.stream()
                .map(a -> MasterClubAdminContactResponse.builder()
                        .name(displayName(a))
                        .email(a.getEmail())
                        .phone(a.getPhone())
                        .role(a.getRole())
                        .build())
                .collect(Collectors.toList());
    }

    private List<MasterClubRecentItemResponse> loadRecentPayments(String clubId, int limit) {
        Query query = new Query(Criteria.where("clubId").is(clubId)
                .and("type").is("payment")
                .and("status").is("completed"));
        query.with(Sort.by(Sort.Order.desc("createdAt"))).limit(limit);
        query.fields().include("amount").include("description").include("status").include("createdAt");
        return mongoTemplate.find(query, Transaction.class).stream()
                .map(t -> MasterClubRecentItemResponse.builder()
                        .id(t.getId())
                        .label(t.getDescription() != null && !t.getDescription().isBlank()
                                ? t.getDescription()
                                : "Payment")
                        .amount(t.getAmount())
                        .status(t.getStatus())
                        .at(t.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private List<MasterClubRecentItemResponse> loadRecentWithdrawals(String clubId, int limit) {
        Query query = new Query(Criteria.where("clubId").is(clubId));
        query.with(Sort.by(Sort.Order.desc("requestedAt"))).limit(limit);
        query.fields().include("amount").include("status").include("requestedAt").include("description");
        return mongoTemplate.find(query, WithdrawalRequest.class).stream()
                .map(w -> MasterClubRecentItemResponse.builder()
                        .id(w.getId())
                        .label(w.getDescription() != null && !w.getDescription().isBlank()
                                ? w.getDescription()
                                : "Withdrawal")
                        .amount(w.getAmount())
                        .status(w.getStatus())
                        .at(w.getRequestedAt() != null ? w.getRequestedAt() : w.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private List<String> buildHealthFlags(
            int playerCount,
            int sessionCount,
            double outstanding,
            ClubWalletResponse wallet) {
        List<String> flags = new ArrayList<>();
        if (playerCount == 0) {
            flags.add("NO_PLAYERS");
        }
        if (sessionCount == 0) {
            flags.add("NO_SESSIONS");
        }
        if (outstanding > 0) {
            flags.add("HAS_OUTSTANDING");
        }
        if (wallet != null && nz(wallet.getPendingWithdrawals()) > 0) {
            flags.add("PENDING_WITHDRAWAL");
        }
        return flags;
    }

    private static String deriveStatus(int players, int teams, int sessions) {
        if (players > 0) {
            return "ACTIVE";
        }
        if (teams == 0 && sessions == 0) {
            return "INCOMPLETE";
        }
        return "EMPTY";
    }

    private static boolean matchesStatusFilter(MasterClubListItemResponse row, String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank() || "ALL".equalsIgnoreCase(statusFilter)) {
            return true;
        }
        String filter = statusFilter.trim().toUpperCase(Locale.ROOT);
        return switch (filter) {
            case "ACTIVE" -> "ACTIVE".equals(row.getStatus());
            case "EMPTY" -> "EMPTY".equals(row.getStatus()) || "INCOMPLETE".equals(row.getStatus());
            case "OUTSTANDING" -> row.getOutstandingAmount() != null && row.getOutstandingAmount() > 0;
            case "PENDING_WITHDRAWAL" ->
                    (row.getPendingWithdrawals() != null && row.getPendingWithdrawals() > 0)
                            || (row.getOpenWithdrawalCount() != null && row.getOpenWithdrawalCount() > 0);
            default -> true;
        };
    }

    private static String displayName(Auth auth) {
        String first = Optional.ofNullable(auth.getFirstName()).orElse("").trim();
        String last = Optional.ofNullable(auth.getLastName()).orElse("").trim();
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) {
            return full;
        }
        if (auth.getUserName() != null && !auth.getUserName().isBlank()) {
            return auth.getUserName();
        }
        return auth.getEmail();
    }

    private static PageMetaResponse pageMeta(int page, int limit, long total) {
        int pages = total == 0 ? 0 : (int) Math.ceil((double) total / limit);
        return PageMetaResponse.builder()
                .page(page)
                .limit(limit)
                .pageSize(limit)
                .total(total)
                .pages(pages)
                .build();
    }

    private static double nz(Double value) {
        return value != null ? value : 0.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record OutstandingTotals(double amount, int count) {}
}
