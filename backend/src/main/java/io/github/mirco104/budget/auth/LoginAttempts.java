package io.github.mirco104.budget.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * US14: after 5 failed sign-ins within 15 minutes the e-mail is locked for 15 minutes. In addition (pentest F6),
 * a single client IP is locked after 20 failed sign-ins within the same window, so password spraying across many
 * accounts from one IP is throttled too; the higher threshold leaves room for several users behind one shared IP
 * (NAT). Kept in memory: one instance, few users, and a restart only lifts the locks early.
 */
@Component
public class LoginAttempts {

    static final int MAX_FAILURES = 5;
    static final int MAX_IP_FAILURES = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final Duration LOCK = Duration.ofMinutes(15);

    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new HashMap<>();
    private final Map<String, Instant> lockedUntil = new HashMap<>();
    private final Map<String, Deque<Instant>> failuresByIp = new HashMap<>();
    private final Map<String, Instant> lockedUntilByIp = new HashMap<>();

    public LoginAttempts(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean isLocked(String email) {
        return locked(lockedUntil, email);
    }

    public synchronized boolean isLockedByIp(String ip) {
        return locked(lockedUntilByIp, ip);
    }

    public synchronized void recordFailure(String email) {
        record(failures, lockedUntil, email, MAX_FAILURES);
    }

    public synchronized void recordIpFailure(String ip) {
        record(failuresByIp, lockedUntilByIp, ip, MAX_IP_FAILURES);
    }

    public synchronized void reset(String email) {
        failures.remove(email);
        lockedUntil.remove(email);
    }

    public synchronized void resetIp(String ip) {
        failuresByIp.remove(ip);
        lockedUntilByIp.remove(ip);
    }

    private boolean locked(Map<String, Instant> locks, String key) {
        Instant until = locks.get(key);
        if (until == null) {
            return false;
        }
        if (clock.instant().isBefore(until)) {
            return true;
        }
        locks.remove(key);
        return false;
    }

    private void record(Map<String, Deque<Instant>> counters, Map<String, Instant> locks, String key, int max) {
        Instant now = clock.instant();
        forgetExpired(now);
        Deque<Instant> recent = counters.computeIfAbsent(key, k -> new ArrayDeque<>());
        recent.addLast(now);
        if (recent.size() >= max) {
            locks.put(key, now.plus(LOCK));
            counters.remove(key);
        }
    }

    // Keeps the maps small when many different e-mails / IPs are tried
    private void forgetExpired(Instant now) {
        forgetExpired(failures, lockedUntil, now);
        forgetExpired(failuresByIp, lockedUntilByIp, now);
    }

    private void forgetExpired(Map<String, Deque<Instant>> counters, Map<String, Instant> locks, Instant now) {
        Instant windowStart = now.minus(WINDOW);
        counters.values().forEach(recent -> {
            while (!recent.isEmpty() && !recent.peekFirst().isAfter(windowStart)) {
                recent.removeFirst();
            }
        });
        counters.values().removeIf(Deque::isEmpty);
        locks.values().removeIf(until -> !now.isBefore(until));
    }
}
