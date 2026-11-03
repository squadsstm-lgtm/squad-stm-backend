package com.squad.backend.config;

import com.squad.backend.model.BlogPost;
import com.squad.backend.model.Season;
import com.squad.backend.repository.BlogPostRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartupConfig implements CommandLineRunner {

    private final SeasonRepository seasonRepository;
    private final BlogPostRepository blogPostRepository;
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
        try {
            seedSampleBlogPostsIfEmpty();
        } catch (Exception e) {
            log.error("Error seeding sample blog posts: ", e);
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

    /** First-run sample posts so the public Blog page is not empty. Controllers can edit or delete them. */
    private void seedSampleBlogPostsIfEmpty() {
        if (blogPostRepository.count() > 0) {
            return;
        }
        Instant now = Instant.now();
        blogPostRepository.save(samplePost(
                "Welcome to the SQUAD journal",
                "welcome-to-the-squad-journal",
                "A short note on why we built a home for clubs, sessions, and match day admin.",
                "SQUAD is built for grassroots clubs that live on the pitch, not in spreadsheets.\n\n"
                        + "This journal is where we'll share product updates, club stories, and simple tips for running a smoother season - from invites to attendance to fees.\n\n"
                        + "Thanks for being here. More posts are on the way.",
                "https://images.unsplash.com/photo-1574629810360-7efbbe195018?auto=format&fit=crop&w=1200&q=80",
                now.minus(6, ChronoUnit.DAYS)));
        blogPostRepository.save(samplePost(
                "Player invites without the chase",
                "player-invites-without-the-chase",
                "How WhatsApp and email invites keep parents and players in the loop without extra accounts.",
                "Getting players registered should not mean chasing forms all week.\n\n"
                        + "With SQUAD, clubs send one invite link by WhatsApp or email. Players add their details once. Staff see who's in, who's pending, and who's ready for the session list.\n\n"
                        + "Less admin. More time on the training pitch.",
                "https://images.unsplash.com/photo-1431324155629-1a6deb1dec8d?auto=format&fit=crop&w=1200&q=80",
                now.minus(3, ChronoUnit.DAYS)));
        blogPostRepository.save(samplePost(
                "Match week, made clearer",
                "match-week-made-clearer",
                "Sessions, attendance, and fees in one place so game day stays about the whistle.",
                "Match week gets busy fast - who's training, who's available, and who still owes for the session.\n\n"
                        + "SQUAD keeps that picture in one club home: plan the session, mark who's in, and follow up on outstanding fees without juggling three apps.\n\n"
                        + "When Saturday comes, the focus stays on the pitch.",
                "https://images.unsplash.com/photo-1522778119026-d647f0596c20?auto=format&fit=crop&w=1200&q=80",
                now.minus(1, ChronoUnit.DAYS)));
        log.info("Seeded 3 sample blog posts for the public Blog page.");
    }

    private BlogPost samplePost(
            String title,
            String slug,
            String summary,
            String body,
            String coverImage,
            Instant publishedAt) {
        BlogPost post = new BlogPost();
        post.setTitle(title);
        post.setSlug(slug);
        post.setSummary(summary);
        post.setBody(body);
        post.setCoverImage(coverImage);
        post.setStatus(BlogPost.STATUS_PUBLISHED);
        post.setPublishedAt(publishedAt);
        post.setCreatedBy("system");
        post.setUpdatedBy("system");
        post.setCreatedAt(publishedAt);
        post.setUpdatedAt(publishedAt);
        return post;
    }
}
