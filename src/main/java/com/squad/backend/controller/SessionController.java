package com.squad.backend.controller;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.dto.request.session.CreateSessionRequest;
import com.squad.backend.dto.request.session.MarkAttendanceRequest;
import com.squad.backend.dto.response.ApiResponse;
import com.squad.backend.dto.response.PageMetaResponse;
import com.squad.backend.dto.response.session.SessionAttendanceSummaryResponse;
import com.squad.backend.dto.response.session.SessionPlayerStatusResponse;
import com.squad.backend.dto.response.session.SessionResponse;
import com.squad.backend.dto.response.session.PagedSessionsResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@Slf4j
public class SessionController {

    private final SessionService sessionService;

    @PostMapping
    public ResponseEntity<ApiResponse<SessionResponse>> createSession(
            @Valid @RequestBody CreateSessionRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            SessionResponse response = sessionService.createSession(request, auth);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response));
        } catch (Exception e) {
            log.error("Create session error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedSessionsResponse>> getAllSessions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sessionType,
            @RequestParam(required = false) String status,
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

            SessionService.PagedSessionsResult result = sessionService.getAllSessionsPaged(
                    auth.getClubId(), auth.getSeasonId(), search, sessionType, status, safePage, safeSize);

            PagedSessionsResponse body = PagedSessionsResponse.builder()
                    .sessions(result.sessions())
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
            log.error("Get sessions error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SessionResponse>> getSessionById(
            @PathVariable String id,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            SessionResponse response = sessionService.getSessionById(id, auth);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get session error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SessionResponse>> updateSession(
            @PathVariable String id,
            @Valid @RequestBody CreateSessionRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            SessionResponse response = sessionService.updateSession(id, request, auth);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Update session error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @PathVariable String id,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            sessionService.deleteSession(id, auth);
            return ResponseEntity.ok(ApiResponse.success(null, "Session deleted successfully"));
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.SESSION_ACTIVE_CANNOT_DELETE.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(e.getMessage()));
            }
            if (ErrorMessages.FORBIDDEN.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Delete session error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/all-sessions")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> getAllSessionsWithoutPagination(
            @RequestParam String clubId,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            List<SessionResponse> sessions = sessionService.getAllSessionsWithoutPagination(clubId, auth);
            return ResponseEntity.ok(ApiResponse.success(sessions));
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.FORBIDDEN.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get all sessions error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/session-players/{id}")
    public ResponseEntity<ApiResponse<List<SessionPlayerStatusResponse>>> getSessionPlayers(
            @PathVariable String id,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String teamList,
            @RequestParam(required = false) String additionalPlayer,
            @RequestParam String clubId,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            List<SessionPlayerStatusResponse> players = sessionService.getSessionPlayers(
                    sessionId != null ? sessionId : id, teamList, additionalPlayer, clubId, auth);
            return ResponseEntity.ok(ApiResponse.success(players, "Players and payment status retrieved successfully"));
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.FORBIDDEN.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get session players error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/player/{id}")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> getSessionsByPlayer(
            @PathVariable String id,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            List<SessionResponse> sessions = sessionService.getSessionsByPlayer(
                    id, auth.getClubId(), auth.getSeasonId(), auth);
            return ResponseEntity.ok(ApiResponse.success(sessions));
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.FORBIDDEN.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get sessions by player error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/team/{id}")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> getSessionsByTeam(
            @PathVariable String id,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            List<SessionResponse> sessions = sessionService.getSessionsByTeam(
                    id, auth.getClubId(), auth.getSeasonId(), auth);
            return ResponseEntity.ok(ApiResponse.success(sessions));
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.FORBIDDEN.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get sessions by team error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/sessionType/count/{id}")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getSessionTypeCount(
            @PathVariable String id,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            Map<String, Long> counts = sessionService.getSessionTypeCount(
                    id, auth.getClubId(), auth.getSeasonId(), auth);
            return ResponseEntity.ok(ApiResponse.success(counts));
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.FORBIDDEN.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get session type count error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/toggle/{id}")
    public ResponseEntity<ApiResponse<SessionResponse>> toggleSessionActive(
            @PathVariable String id,
            @RequestBody Map<String, Boolean> request,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            Boolean active = request.get("active");
            if (active == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("active field is required"));
            }
            SessionResponse response = sessionService.toggleSessionActive(id, active, auth);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Toggle session active error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @DeleteMapping("/removePlayer")
    public ResponseEntity<ApiResponse<Void>> removePlayerFromSession(
            @RequestParam String sessionId,
            @RequestParam String playerId,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            sessionService.removePlayerFromSession(sessionId, playerId, auth);
            return ResponseEntity.ok(ApiResponse.success(null, "Player removed successfully"));
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.FORBIDDEN.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(e.getMessage()));
            }
            if ("Player is in a team and cannot be deleted".equals(e.getMessage())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Remove player from session error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/{sessionId}/mark-attendance")
    public ResponseEntity<ApiResponse<SessionAttendanceSummaryResponse>> markAttendance(
            @PathVariable String sessionId,
            @Valid @RequestBody MarkAttendanceRequest request,
            @AuthenticationPrincipal Auth auth) {
        try {
            if (auth == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(ErrorMessages.UNAUTHORIZED));
            }
            SessionAttendanceSummaryResponse response = sessionService.markAttendance(sessionId, request, auth);
            String message = "Attendance marked successfully for " + request.getAttendance().size() + " players";
            return ResponseEntity.ok(ApiResponse.success(response, message));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Mark attendance error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }
}
