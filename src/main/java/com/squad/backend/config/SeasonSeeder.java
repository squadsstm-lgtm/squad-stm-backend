package com.squad.backend.config;

import com.squad.backend.model.Season;
import com.squad.backend.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Year;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class SeasonSeeder implements ApplicationRunner {

    private final SeasonRepository seasonRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (seasonRepository.findByActive(true).isPresent()) {
            return;
        }
        int year = Year.now().getValue();
        Season season = new Season();
        season.setStartDate(LocalDate.of(year, 1, 1));
        season.setEndDate(LocalDate.of(year, 12, 31));
        season.setYear(String.valueOf(year));
        season.setActive(true);
        seasonRepository.save(season);
        log.info("No active season found. Created default active season for year {}.", year);
    }
}
