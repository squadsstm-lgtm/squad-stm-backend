package com.squad.backend.event;

import com.squad.backend.service.ClubWalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClubWalletUnlockListener {

    private final ClubWalletService clubWalletService;

    @Async
    @EventListener
    public void onUnlockRequested(ClubWalletUnlockRequestedEvent event) {
        try {
            clubWalletService.unlockEligibleForClub(event.clubId());
        } catch (Exception e) {
            log.error("Async wallet unlock failed for club {}: {}", event.clubId(), e.getMessage(), e);
        }
    }
}
