package com.squad.backend.config;

import com.squad.backend.model.Season;
import com.squad.backend.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
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
        try {
            ensurePhoneIndexAllowsMissing();
        } catch (Exception e) {
            log.error("Error updating phone index so email invites can omit a phone number: ", e);
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

    /**
     * A normal unique index treats "no phone" as a value and allows it only once.
     * Email invites have no phone, so replace that index with one that ignores empty numbers.
     */
    private void ensurePhoneIndexAllowsMissing() {
        long cleared = mongoTemplate.updateMulti(
                Query.query(Criteria.where("phone").is(null)),
                new Update().unset("phone"),
                "auths").getModifiedCount();
        cleared += mongoTemplate.updateMulti(
                Query.query(Criteria.where("phone").is("")),
                new Update().unset("phone"),
                "auths").getModifiedCount();

        IndexOperations ops = mongoTemplate.indexOps("auths");
        boolean alreadyPartial = ops.getIndexInfo().stream()
                .filter(info -> "phone_1".equals(info.getName()))
                .anyMatch(info -> info.getPartialFilterExpression() != null
                        && info.getPartialFilterExpression().contains("$gt"));
        if (alreadyPartial) {
            log.info("Phone index already allows email invites without a number. Cleared {} empty phones.", cleared);
            return;
        }

        try {
            ops.dropIndex("phone_1");
        } catch (Exception e) {
            log.info("No existing phone index to replace.");
        }
        ops.ensureIndex(new Index()
                .on("phone", Sort.Direction.ASC)
                .named("phone_1")
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("phone").gt(""))));
        log.info("Phone index updated so email invitations can be sent without a phone number. Cleared {} empty phones.", cleared);
    }
}
