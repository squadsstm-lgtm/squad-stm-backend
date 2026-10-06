package com.squad.backend.service;

import com.squad.backend.dto.response.masterpanel.PlatformSettingsResponse;
import com.squad.backend.model.Auth;
import com.squad.backend.model.Club;
import com.squad.backend.model.PlatformSettings;
import com.squad.backend.repository.AuthRepository;
import com.squad.backend.repository.ClubRepository;
import com.squad.backend.repository.PlatformSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    public static final double INITIAL_DEFAULT_FEE = 0.99;
    private static final BigDecimal MAX_FEE = new BigDecimal("999.99");
    private static final String SYSTEM_ACTOR = "system";

    private final PlatformSettingsRepository platformSettingsRepository;
    private final ClubRepository clubRepository;
    private final AuthRepository authRepository;

    public PlatformSettingsResponse getSettings() {
        return toResponse(getOrCreate());
    }

    public PlatformSettingsResponse updateDefault(BigDecimal fee, String updatedBy) {
        double amount = requireFee(fee);
        PlatformSettings settings = getOrCreate();
        settings.setDefaultPlatformFee(amount);
        settings.setUpdatedAt(Instant.now());
        settings.setUpdatedBy(updatedBy);
        platformSettingsRepository.save(settings);
        int clubsUpdated = applyDefaultToFollowers(amount, updatedBy);
        PlatformSettingsResponse response = toResponse(settings);
        response.setClubsUpdated(clubsUpdated);
        return response;
    }

    /**
     * The number copied onto a new club, and onto clubs that follow the default.
     */
    public double currentDefaultFee() {
        Double fee = getOrCreate().getDefaultPlatformFee();
        if (fee == null) {
            return INITIAL_DEFAULT_FEE;
        }
        return fee;
    }

    public double requireFee(BigDecimal raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Platform fee is required.");
        }
        if (raw.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Platform fee cannot be negative.");
        }
        if (raw.compareTo(MAX_FEE) > 0) {
            throw new IllegalArgumentException("Platform fee cannot be more than 999.99.");
        }
        try {
            return raw.setScale(2, RoundingMode.UNNECESSARY).doubleValue();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Platform fee can have at most 2 decimal places.");
        }
    }

    public String displayName(String userId) {
        if (userId == null || userId.isBlank() || SYSTEM_ACTOR.equals(userId)) {
            return "System";
        }
        return authRepository.findById(userId)
                .map(this::nameOf)
                .orElse("Unknown user");
    }

    private PlatformSettings getOrCreate() {
        return platformSettingsRepository.findById(PlatformSettings.SINGLETON_ID)
                .orElseGet(this::createDefault);
    }

    private PlatformSettings createDefault() {
        PlatformSettings settings = new PlatformSettings();
        settings.setId(PlatformSettings.SINGLETON_ID);
        settings.setDefaultPlatformFee(INITIAL_DEFAULT_FEE);
        settings.setUpdatedAt(Instant.now());
        settings.setUpdatedBy(SYSTEM_ACTOR);
        try {
            return platformSettingsRepository.save(settings);
        } catch (DuplicateKeyException ex) {
            return platformSettingsRepository.findById(PlatformSettings.SINGLETON_ID)
                    .orElseThrow(() -> ex);
        }
    }

    /**
     * Clubs with their own fee, including a saved 0 for no fee, are left alone.
     */
    private int applyDefaultToFollowers(double amount, String updatedBy) {
        List<Club> clubs = clubRepository.findByPlatformFeeFollowsDefaultTrue();
        if (clubs.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        for (Club club : clubs) {
            club.setPlatformFee(amount);
            club.setPlatformFeeFollowsDefault(true);
            club.setPlatformFeeUpdatedAt(now);
            club.setPlatformFeeUpdatedBy(updatedBy);
        }
        clubRepository.saveAll(clubs);
        return clubs.size();
    }

    private PlatformSettingsResponse toResponse(PlatformSettings settings) {
        return PlatformSettingsResponse.builder()
                .defaultPlatformFee(settings.getDefaultPlatformFee())
                .updatedAt(settings.getUpdatedAt())
                .updatedByName(displayName(settings.getUpdatedBy()))
                .build();
    }

    private String nameOf(Auth auth) {
        String first = auth.getFirstName() == null ? "" : auth.getFirstName().trim();
        String last = auth.getLastName() == null ? "" : auth.getLastName().trim();
        String name = (first + " " + last).trim();
        if (!name.isEmpty()) {
            return name;
        }
        if (auth.getEmail() != null && !auth.getEmail().isBlank()) {
            return auth.getEmail();
        }
        return "Unknown user";
    }
}
