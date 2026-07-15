package com.squad.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.dto.request.invite.MatchSessionPlayerRequest;
import com.squad.backend.dto.request.invite.SelectSessionPlayerRequest;
import com.squad.backend.dto.request.player.CreatePlayerRequest;
import com.squad.backend.dto.request.user.CreateUserRequest;
import com.squad.backend.dto.response.ApiResponse;
import com.squad.backend.dto.response.invite.InviteResolveResponse;
import com.squad.backend.dto.response.invite.MatchSessionPlayerResponse;
import com.squad.backend.dto.response.invite.SelectSessionPlayerResponse;
import com.squad.backend.dto.response.player.PlayerResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.service.InviteRateLimitService;
import com.squad.backend.service.InviteTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invite")
@RequiredArgsConstructor
@Slf4j
public class InviteController {

    private final InviteTokenService inviteTokenService;
    private final InviteRateLimitService inviteRateLimitService;
    private final ObjectMapper objectMapper;

    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<InviteResolveResponse>> resolveInvite(
            @PathVariable String code,
            HttpServletRequest request) {
        try {
            inviteRateLimitService.checkRateLimit(resolveClientIp(request));
            InviteResolveResponse response = inviteTokenService.resolve(code);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (InviteTokenService.InviteTokenExpiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (InviteTokenService.InviteTokenRevokedException e) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.PLAYER_INVITE_ALREADY_SUBMITTED.equals(e.getMessage())
                    || ErrorMessages.USER_INVITE_ALREADY_SUBMITTED.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Resolve invite error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/{code}/select-player")
    public ResponseEntity<ApiResponse<SelectSessionPlayerResponse>> selectSessionPlayer(
            @PathVariable String code,
            @Valid @RequestBody SelectSessionPlayerRequest request,
            HttpServletRequest httpRequest) {
        try {
            inviteRateLimitService.checkRateLimit(resolveClientIp(httpRequest));
            SelectSessionPlayerResponse response =
                    inviteTokenService.selectSessionPlayer(code, request.getPlayerId());
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (InviteTokenService.InviteTokenExpiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (InviteTokenService.InviteTokenRevokedException e) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Select session player error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PostMapping("/{code}/match-player")
    public ResponseEntity<ApiResponse<MatchSessionPlayerResponse>> matchSessionPlayer(
            @PathVariable String code,
            @Valid @RequestBody MatchSessionPlayerRequest request,
            HttpServletRequest httpRequest) {
        try {
            inviteRateLimitService.checkRateLimit(resolveClientIp(httpRequest));
            MatchSessionPlayerResponse response = inviteTokenService.matchSessionPlayer(
                    code,
                    request.getFirstName(),
                    request.getLastName(),
                    request.getPlayerId());
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (InviteTokenService.InviteTokenExpiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (InviteTokenService.InviteTokenRevokedException e) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Match session player error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @PutMapping("/{code}/complete")
    public ResponseEntity<ApiResponse<Object>> completeInvite(
            @PathVariable String code,
            @RequestBody(required = false) Object body,
            HttpServletRequest request) {
        try {
            inviteRateLimitService.checkRateLimit(resolveClientIp(request));
            InviteResolveResponse resolved = inviteTokenService.resolve(code);

            if ("PLAYER_PROFILE".equals(resolved.getPurpose())) {
                CreatePlayerRequest playerRequest = mapToPlayerRequest(body);
                validatePlayerRequest(playerRequest);
                PlayerResponse playerResponse = inviteTokenService.completePlayerProfile(code, playerRequest);
                return ResponseEntity.ok(ApiResponse.success(playerResponse));
            }

            if ("USER_PROFILE".equals(resolved.getPurpose())) {
                CreateUserRequest userRequest = mapToUserRequest(body);
                validateUserRequest(userRequest);
                Auth authResponse = inviteTokenService.completeUserProfile(code, userRequest);
                return ResponseEntity.ok(ApiResponse.success(authResponse));
            }

            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ErrorMessages.INVITE_LINK_INVALID));
        } catch (InviteTokenService.InviteTokenExpiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (InviteTokenService.InviteTokenRevokedException e) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            if (ErrorMessages.PLAYER_INVITE_ALREADY_SUBMITTED.equals(e.getMessage())
                    || ErrorMessages.USER_INVITE_ALREADY_SUBMITTED.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Complete invite error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    private void validatePlayerRequest(CreatePlayerRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.getFirstName() == null || request.getFirstName().trim().isEmpty()) {
            throw new IllegalArgumentException("First name is required");
        }
        if (request.getSurName() == null || request.getSurName().trim().isEmpty()) {
            throw new IllegalArgumentException("Last name is required");
        }
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
    }

    private void validateUserRequest(CreateUserRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.getFirstName() == null || request.getFirstName().trim().isEmpty()) {
            throw new IllegalArgumentException("First name is required");
        }
        if (request.getLastName() == null || request.getLastName().trim().isEmpty()) {
            throw new IllegalArgumentException("Last name is required");
        }
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
    }

    private CreatePlayerRequest mapToPlayerRequest(Object body) {
        if (body instanceof CreatePlayerRequest request) {
            return request;
        }
        return objectMapper.convertValue(body, CreatePlayerRequest.class);
    }

    private CreateUserRequest mapToUserRequest(Object body) {
        if (body instanceof CreateUserRequest request) {
            return request;
        }
        return objectMapper.convertValue(body, CreateUserRequest.class);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
