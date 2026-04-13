package com.squad.backend.service;

import com.squad.backend.constants.ErrorMessages;
import com.squad.backend.dto.response.SeasonResponse;
import com.squad.backend.model.Season;
import com.squad.backend.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeasonService {

    private final SeasonRepository seasonRepository;

    public List<SeasonResponse> getAllSeasons() {
        List<Season> seasons = seasonRepository.findAll();
        return seasons.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public SeasonResponse getCurrentSeason() {
        Season season = seasonRepository.findByActive(true)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.NO_ACTIVE_SEASON));
        return mapToResponse(season);
    }

    private SeasonResponse mapToResponse(Season season) {
        return SeasonResponse.builder()
                .id(season.getId())
                .startDate(season.getStartDate())
                .endDate(season.getEndDate())
                .year(season.getYear())
                .active(season.getActive())
                .build();
    }
}
