package com.squad.backend.config;

import com.squad.backend.model.Season;
import com.squad.backend.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartupConfig implements CommandLineRunner {

    private final SeasonRepository seasonRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) {
        try {
            createOrActivateSeason();
        } catch (Exception e) {
            log.error("Error initializing season on startup: ", e);
        }
    }

    private void createOrActivateSeason() {
        try {
            LocalDate currentDate = LocalDate.now();
            int month = currentDate.getMonthValue();
            int year = currentDate.getYear();
            
            // If month is before May (month < 5), use previous year
            final int seasonYear = (month < 5) ? year - 1 : year;
            
            // Find existing season for this year
            final String yearString = String.valueOf(seasonYear);
            Optional<Season> existingSeason = seasonRepository.findAll().stream()
                    .filter(s -> s.getYear() != null && s.getYear().equals(yearString))
                    .findFirst();
            
            // Set all seasons to inactive first (using updateMany like Node.js)
            Query query = new Query();
            Update update = new Update().set("active", false);
            mongoTemplate.updateMulti(query, update, Season.class);
            
            if (existingSeason.isEmpty()) {
                // Create new season
                LocalDate startDate = LocalDate.of(seasonYear, 8, 1); // August 1st
                LocalDate endDate = startDate.plusMonths(9); // 9 months later (May)
                
                Season newSeason = new Season();
                newSeason.setStartDate(startDate);
                newSeason.setEndDate(endDate);
                newSeason.setYear(yearString);
                newSeason.setActive(true);
                
                seasonRepository.save(newSeason);
                log.info("Season for year {} created and activated.", seasonYear);
            } else {
                // Activate existing season using updateOne (like Node.js)
                Season season = existingSeason.get();
                Query seasonQuery = new Query(Criteria.where("_id").is(season.getId()));
                Update seasonUpdate = new Update().set("active", true);
                mongoTemplate.updateFirst(seasonQuery, seasonUpdate, Season.class);
                log.info("Season for year {} already exists and activated.", seasonYear);
            }
        } catch (Exception e) {
            log.error("Error in createOrActivateSeason: ", e);
            // Don't throw - let the app start even if season initialization fails
        }
    }
}
