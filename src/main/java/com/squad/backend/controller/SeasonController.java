package com.squad.backend.controller;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.dto.response.ApiResponse;
import com.squad.backend.dto.response.SeasonResponse;
import com.squad.backend.service.SeasonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/seasons")
@RequiredArgsConstructor
@Slf4j
public class SeasonController {

    private final SeasonService seasonService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SeasonResponse>>> getAllSeasons() {
        try {
            List<SeasonResponse> seasonResponses = seasonService.getAllSeasons();
            return ResponseEntity.ok(ApiResponse.success(seasonResponses));
        } catch (Exception e) {
            log.error("Get seasons error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<SeasonResponse>> getCurrentSeason() {
        try {
            SeasonResponse response = seasonService.getCurrentSeason();
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get current season error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ErrorMessages.AN_ERROR_OCCURRED));
        }
    }
}
