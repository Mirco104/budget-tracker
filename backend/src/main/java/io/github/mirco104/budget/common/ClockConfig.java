package io.github.mirco104.budget.common;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One clock for timestamps, the login lock and the current month (R7, R23), so tests can control the time.
 * The zone keeps the current month from changing at UTC midnight.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Europe/Berlin"));
    }
}
