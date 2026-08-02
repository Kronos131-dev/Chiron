package com.kronos.chiron.fitbit;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FitbitAuthSessionStore {

    private static final Duration TTL = Duration.ofMinutes(10);

    public record PendingAuth(Long chironUserId, String codeVerifier, Instant createdAt) {}

    private final ConcurrentHashMap<String, PendingAuth> pending = new ConcurrentHashMap<>();

    public String register(Long chironUserId, String codeVerifier) {
        purgeExpired();
        String state = UUID.randomUUID().toString();
        pending.put(state, new PendingAuth(chironUserId, codeVerifier, Instant.now()));
        return state;
    }

    public PendingAuth consume(String state) {
        if (state == null) return null;
        PendingAuth p = pending.remove(state);
        if (p == null) return null;
        if (p.createdAt().plus(TTL).isBefore(Instant.now())) return null;
        return p;
    }

    private void purgeExpired() {
        Instant cutoff = Instant.now().minus(TTL);
        pending.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(cutoff));
    }
}
