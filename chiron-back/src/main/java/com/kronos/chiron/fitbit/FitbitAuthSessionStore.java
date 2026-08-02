package com.kronos.chiron.fitbit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class FitbitAuthSessionStore {

    private static final Duration TTL = Duration.ofMinutes(10);

    public record PendingAuth(Long chironUserId, String codeVerifier, Instant createdAt) {}

    private final ConcurrentHashMap<String, PendingAuth> pending = new ConcurrentHashMap<>();

    private final Clock clock;

    public String register(Long chironUserId, String codeVerifier) {
        purgeExpired();
        String state = UUID.randomUUID().toString();
        pending.put(state, new PendingAuth(chironUserId, codeVerifier, clock.instant()));
        return state;
    }

    public PendingAuth consume(String state) {
        if (state == null) return null;
        PendingAuth p = pending.remove(state);
        if (p == null) return null;
        if (p.createdAt().plus(TTL).isBefore(clock.instant())) return null;
        return p;
    }

    private void purgeExpired() {
        Instant cutoff = clock.instant().minus(TTL);
        pending.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(cutoff));
    }
}
