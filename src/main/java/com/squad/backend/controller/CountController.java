package com.squad.backend.controller;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.dto.response.ApiResponse;
import com.squad.backend.dto.response.ClubCountResponse;
import com.squad.backend.dto.response.CountResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.model.Season;
import com.squad.backend.repository.SeasonRepository;
import com.squad.backend.security.TenantScope;
import com.squad.backend.service.CountService;
import com.squad.backend.utils.PaginationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.Map;

@RestController
@RequestMapping("/api/counts")
@RequiredArgsConstructor
@Slf4j
public class CountController {

    private final CountService countService;
    private final SeasonRepository seasonRepository;

    @GetMapping("/count/{clubId}")
    public ResponseEntity<ApiResponse<CountResponse>> getCounts(
            @PathVariable String clubId,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                log.error("Auth is null in getCounts");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("User not authenticated"));
            }
            if (TenantScope.denyClubOnly(auth, clubId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(ErrorMessages.FORBIDDEN));
            }

            // Get seasonId from auth, or fallback to current active season
            String seasonId = auth.getSeasonId();
            if (seasonId == null || seasonId.isEmpty()) {
                log.warn("SeasonId is null for user: {}, using current active season", auth.getId());
                Optional<Season> currentSeason = seasonRepository.findByActive(true);
                if (currentSeason.isPresent()) {
                    seasonId = currentSeason.get().getId();
                } else {
                    log.error("No active season found");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(ApiResponse.error("No active season found"));
                }
            }
            
            CountResponse response = countService.getCounts(clubId, seasonId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            log.error("Get counts error for clubId: {}, seasonId: {}", clubId, auth != null ? auth.getSeasonId() : "null", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/clubs")
    public ResponseEntity<ApiResponse<Object>> getClubsWithCounts(
            @RequestParam(required = false) String clubId,
            @RequestParam(required = false) String pageNumber,
            @RequestParam(required = false) String pageSize,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                log.error("Auth is null in getClubsWithCounts");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("User not authenticated"));
            }

            final String clubIdFilter;
            if (TenantScope.isControllerWithoutClub(auth)) {
                clubIdFilter = (clubId != null && !clubId.isBlank()) ? clubId.trim() : null;
            } else {
                if (auth.getClubId() == null || auth.getClubId().isBlank()) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(ApiResponse.error(ErrorMessages.FORBIDDEN));
                }
                if (clubId != null && !clubId.isBlank() && !auth.getClubId().equals(clubId.trim())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(ApiResponse.error(ErrorMessages.FORBIDDEN));
                }
                clubIdFilter = auth.getClubId();
            }

            PaginationUtils.PageParams pageParams = PaginationUtils.parse(pageNumber, pageSize);

            if (pageParams.hasPagination()) {
                CountService.ClubsWithCountsPagedResult pagedResult = countService.getClubsWithCountsPaged(
                        auth.getSeasonId(),
                        clubIdFilter,
                        pageParams.page(),
                        pageParams.size()
                );
                return ResponseEntity.ok(ApiResponse.success(PaginationUtils.buildPagedBody(
                        "clubs",
                        pagedResult.clubs(),
                        pagedResult.totalItems(),
                        pagedResult.totalPages(),
                        pagedResult.currentPage(),
                        pagedResult.pageSize()
                )));
            }

            List<ClubCountResponse> clubData = countService.getClubsWithCounts(
                    auth.getSeasonId(), clubIdFilter);
            return ResponseEntity.ok(ApiResponse.success(Map.of("clubs", clubData)));
        } catch (Exception e) {
            log.error("Get clubs with counts error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }
}
