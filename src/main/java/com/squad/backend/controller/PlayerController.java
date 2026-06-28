package com.squad.backend.controller;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.dto.request.player.CreatePlayerRequest;
import com.squad.backend.dto.request.player.InvitePlayerRequest;
import com.squad.backend.dto.response.ApiResponse;
import com.squad.backend.dto.response.PageMetaResponse;
import com.squad.backend.dto.response.player.PagedPlayersResponse;
import com.squad.backend.dto.response.invite.PlayerInviteResponse;
import com.squad.backend.dto.response.player.PlayerResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.security.TenantScope;
import com.squad.backend.service.PlayerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
@Slf4j
public class PlayerController {

    private final PlayerService playerService;

    @PostMapping
    public ResponseEntity<ApiResponse<PlayerResponse>> createPlayer(
            @Valid @RequestBody CreatePlayerRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            PlayerResponse response = playerService.createPlayer(request, auth);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.FORBIDDEN.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Create player error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedPlayersResponse>> getAllPlayers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String pageNumber,
            @RequestParam(required = false) String pageSize,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            com.squad.backend.utils.PaginationUtils.PageParams pageParams =
                    com.squad.backend.utils.PaginationUtils.parse(pageNumber, pageSize);
            int safePage = pageParams.page() != null && pageParams.page() > 0 ? pageParams.page() : 1;
            int safeSize = pageParams.size() != null && pageParams.size() > 0 ? pageParams.size() : 10;
            PlayerService.PagedPlayersResult result = playerService.getCategorizedPlayersPaged(
                    auth.getClubId(), auth.getSeasonId(), search, safePage, safeSize);
            PagedPlayersResponse body = PagedPlayersResponse.builder()
                    .players(result.players())
                    .pagination(PageMetaResponse.builder()
                            .page(result.currentPage())
                            .limit(result.pageSize())
                            .pageSize(result.pageSize())
                            .total(result.totalItems())
                            .pages(result.totalPages())
                            .build())
                    .build();
            return ResponseEntity.ok(ApiResponse.success(body));
        } catch (Exception e) {
            log.error("Get players error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PlayerResponse>> getPlayerById(
            @PathVariable String id,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            PlayerResponse response = playerService.getPlayerById(id, auth);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get player error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PlayerResponse>> updatePlayer(
            @PathVariable String id,
            @RequestParam(required = false) String teamId, // Support teamId as query parameter (like Node.js)
            @RequestBody(required = false) CreatePlayerRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            // Node.js logic: if req.query.teamId == 'undefined' → full update, else → quick team assignment
            boolean isQuickTeamAssignment = (teamId != null && !teamId.isEmpty() && !"undefined".equals(teamId));
            
            // Only validate when doing full update (no teamId in query)
            if (!isQuickTeamAssignment && request != null) {
                // Validate required fields for full update
                if (request.getFirstName() == null || request.getFirstName().trim().isEmpty()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(ApiResponse.error("First name is required"));
                }
                if (request.getSurName() == null || request.getSurName().trim().isEmpty()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(ApiResponse.error("Last name is required"));
                }
                if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(ApiResponse.error("Email is required"));
                }
            }
            
            PlayerResponse response = playerService.updatePlayer(id, teamId, request, auth);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Update player error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/{id}/{token}")
    public ResponseEntity<ApiResponse<PlayerResponse>> updatePlayerWithToken(
            @PathVariable String id,
            @PathVariable String token,
            @RequestBody(required = false) CreatePlayerRequest request) {
        try {
            if (request != null) {
                if (request.getFirstName() == null || request.getFirstName().trim().isEmpty()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(ApiResponse.error("First name is required"));
                }
                if (request.getSurName() == null || request.getSurName().trim().isEmpty()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(ApiResponse.error("Last name is required"));
                }
                if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(ApiResponse.error("Email is required"));
                }
            }
            PlayerResponse response = playerService.updatePlayerWithToken(id, token, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.PLAYER_INVITE_ALREADY_SUBMITTED.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Update player via token error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> deletePlayer(
            @PathVariable String id,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            playerService.deletePlayer(id, auth);
            return ResponseEntity.ok(ApiResponse.success(java.util.Map.of("deleted", true), "Player deleted successfully"));
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.PLAYER_HAS_ACTIVE_SESSION.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Delete player error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<List<PlayerResponse>>> createBulkPlayers(
            @Valid @RequestBody List<CreatePlayerRequest> requests,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            List<PlayerResponse> responses = playerService.createBulkPlayers(requests, auth);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(responses));
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.FORBIDDEN.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Bulk create players error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/request-info")
    public ResponseEntity<ApiResponse<PlayerInviteResponse>> invitePlayer(
            @Valid @RequestBody InvitePlayerRequest request,
            @RequestParam(required = false) String clubId,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            String targetClubId = request.getClubId() != null ? request.getClubId() :
                    (clubId != null ? clubId : auth.getClubId());
            String communicationMethod = request.getCommunicationMethod() != null
                    ? request.getCommunicationMethod() : "email";
            PlayerInviteResponse response = playerService.invitePlayer(
                    communicationMethod,
                    request.getEmail(),
                    request.getPhone(),
                    targetClubId,
                    auth);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.FORBIDDEN.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (RuntimeException e) {
            if ("Mail sending error.".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        } catch (Exception e) {
            log.error("Invite player error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/check-email")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkEmail(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            String email = request.get("email");
            String clubId = request.get("clubId");
            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("email is required."));
            }
            String effectiveClubId = TenantScope.resolveClubForAvailabilityCheck(clubId, auth);
            boolean available = playerService.checkEmailAvailability(email, effectiveClubId);
            Map<String, Boolean> response = new HashMap<>();
            response.put("available", available);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.FORBIDDEN.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Check email error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/check-phone")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkPhone(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            String phone = request.get("phone");
            String clubId = request.get("clubId");
            if (phone == null || phone.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("phone is required."));
            }
            String effectiveClubId = TenantScope.resolveClubForAvailabilityCheck(clubId, auth);
            boolean available = playerService.checkPhoneAvailability(phone, effectiveClubId);
            Map<String, Boolean> response = new HashMap<>();
            response.put("available", available);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.FORBIDDEN.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Check phone error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/uncategorised")
    public ResponseEntity<ApiResponse<PagedPlayersResponse>> getUncategorizedPlayers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String pageNumber,
            @RequestParam(required = false) String pageSize,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            com.squad.backend.utils.PaginationUtils.PageParams pageParams =
                    com.squad.backend.utils.PaginationUtils.parse(pageNumber, pageSize);
            int safePage = pageParams.page() != null && pageParams.page() > 0 ? pageParams.page() : 1;
            int safeSize = pageParams.size() != null && pageParams.size() > 0 ? pageParams.size() : 10;
            PlayerService.PagedPlayersResult result = playerService.getUncategorizedPlayersPaged(
                    auth.getClubId(), auth.getSeasonId(), search, safePage, safeSize);
            PagedPlayersResponse body = PagedPlayersResponse.builder()
                    .players(result.players())
                    .pagination(PageMetaResponse.builder()
                            .page(result.currentPage())
                            .limit(result.pageSize())
                            .pageSize(result.pageSize())
                            .total(result.totalItems())
                            .pages(result.totalPages())
                            .build())
                    .build();
            return ResponseEntity.ok(ApiResponse.success(body));
        } catch (Exception e) {
            log.error("Get uncategorized players error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/clubs-and-players")
    public ResponseEntity<ApiResponse<PagedPlayersResponse>> getClubsAndPlayers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String clubId,
            @RequestParam(required = false) String pageNumber,
            @RequestParam(required = false) String pageSize,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            List<PlayerResponse> playerResponses = playerService.getClubsAndPlayers(
                    auth.getSeasonId(), search, clubId, auth);

            com.squad.backend.utils.PaginationUtils.PageParams pageParams =
                    com.squad.backend.utils.PaginationUtils.parse(pageNumber, pageSize);
            int safePage = pageParams.page() != null && pageParams.page() > 0 ? pageParams.page() : 1;
            int safeSize = pageParams.size() != null && pageParams.size() > 0 ? pageParams.size() : 10;
            int skip = (safePage - 1) * safeSize;
            List<PlayerResponse> pagedResponses = playerResponses.stream().skip(skip).limit(safeSize).toList();
            long totalItemsCount = playerResponses.size();
            int totalPagesCount = (int) Math.ceil((double) totalItemsCount / safeSize);
            PagedPlayersResponse body = PagedPlayersResponse.builder()
                    .players(pagedResponses)
                    .pagination(PageMetaResponse.builder()
                            .page(safePage)
                            .limit(safeSize)
                            .pageSize(safeSize)
                            .total(totalItemsCount)
                            .pages(totalPagesCount)
                            .build())
                    .build();
            return ResponseEntity.ok(ApiResponse.success(body));
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.FORBIDDEN.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get clubs and players error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/{id}/{token}")
    public ResponseEntity<ApiResponse<PlayerResponse>> getPlayerByIdWithToken(
            @PathVariable String id,
            @PathVariable String token) {
        try {
            PlayerResponse response = playerService.getPlayerByIdWithToken(id, token);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.PLAYER_INVITE_ALREADY_SUBMITTED.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiResponse.error(e.getMessage()));
            }
            if ("Token is invalid".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get player by id with token error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/get-players")
    public ResponseEntity<ApiResponse<List<PlayerResponse>>> getAllPlayersNoAuth() {
        try {
            List<PlayerResponse> players = playerService.getAllPlayersNoAuth();
            return ResponseEntity.ok(ApiResponse.success(players));
        } catch (Exception e) {
            log.error("Get all players error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }
}
