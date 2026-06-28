package com.squad.backend.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InviteRateLimitService {

    private static final int MAX_REQUESTS_PER_MINUTE = 30;
    private static final long WINDOW_MS = 60_000L;

    private final Map<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();

    public void checkRateLimit(String clientIp) {
        String key = clientIp != null ? clientIp : "unknown";
        long now = Instant.now().toEpochMilli();
        Deque<Long> timestamps = requestLog.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > WINDOW_MS) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
                throw new IllegalArgumentException("Too many requests. Please try again later.");
            }
            timestamps.addLast(now);
        }
    }
}
