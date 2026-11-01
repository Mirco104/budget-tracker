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
 * US14: after 5 failed sign-ins within 15 minutes the e-mail is locked for 15 minutes. Kept in memory: one instance,
 * few users, and a restart only lifts the locks early.
 */
@Component
public class LoginAttempts {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final Duration LOCK = Duration.ofMinutes(15);

    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new HashMap<>();
    private final Map<String, Instant> lockedUntil = new HashMap<>();

    public LoginAttempts(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean isLocked(String email) {
        Instant until = lockedUntil.get(email);
        if (until == null) {
            return false;
        }
        if (clock.instant().isBefore(until)) {
            return true;
        }
        lockedUntil.remove(email);
        return false;
    }

    public synchronized void recordFailure(String email) {
        Instant now = clock.instant();
        forgetExpired(now);
        Deque<Instant> recent = failures.computeIfAbsent(email, key -> new ArrayDeque<>());
        recent.addLast(now);
        if (recent.size() >= MAX_FAILURES) {
            lockedUntil.put(email, now.plus(LOCK));
            failures.remove(email);
        }
    }

    public synchronized void reset(String email) {
        failures.remove(email);
        lockedUntil.remove(email);
    }

    // Keeps the maps small when many different e-mails are tried
    private void forgetExpired(Instant now) {
        Instant windowStart = now.minus(WINDOW);
        failures.values().forEach(recent -> {
            while (!recent.isEmpty() && !recent.peekFirst().isAfter(windowStart)) {
                recent.removeFirst();
            }
        });
        failures.values().removeIf(Deque::isEmpty);
        lockedUntil.values().removeIf(until -> !now.isBefore(until));
    }
}
