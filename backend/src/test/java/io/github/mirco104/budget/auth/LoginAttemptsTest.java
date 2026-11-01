package io.github.mirco104.budget.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** US14: 5 failures within 15 minutes lock the e-mail for 15 minutes. */
class LoginAttemptsTest {

    private static final String EMAIL = "person-a@example.test";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-01T10:00:00Z"));
    private final LoginAttempts attempts = new LoginAttempts(clock);

    @Test
    void locksAfterFiveFailuresWithinTheWindow() {
        failTimes(4);
        assertThat(attempts.isLocked(EMAIL)).isFalse();

        attempts.recordFailure(EMAIL);

        assertThat(attempts.isLocked(EMAIL)).isTrue();
        assertThat(attempts.isLocked("person-b@example.test")).isFalse();
    }

    @Test
    void unlocksAfterFifteenMinutes() {
        failTimes(4);
        attempts.recordFailure(EMAIL);

        clock.advance(Duration.ofMinutes(15).minusSeconds(1));
        assertThat(attempts.isLocked(EMAIL)).isTrue();

        clock.advance(Duration.ofSeconds(1));
        assertThat(attempts.isLocked(EMAIL)).isFalse();
    }

    @Test
    void forgetsFailuresOlderThanTheWindow() {
        failTimes(4);
        clock.advance(Duration.ofMinutes(15));

        attempts.recordFailure(EMAIL);

        assertThat(attempts.isLocked(EMAIL)).isFalse();
    }

    @Test
    void successfulSignInClearsTheFailures() {
        failTimes(4);
        attempts.reset(EMAIL);

        attempts.recordFailure(EMAIL);

        assertThat(attempts.isLocked(EMAIL)).isFalse();
    }

    private void failTimes(int times) {
        for (int i = 0; i < times; i++) {
            attempts.recordFailure(EMAIL);
            clock.advance(Duration.ofSeconds(10));
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
