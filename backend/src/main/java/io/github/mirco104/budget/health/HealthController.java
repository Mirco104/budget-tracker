package io.github.mirco104.budget.health;

import java.util.Arrays;
import java.util.List;

import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    /** The local profiles of decision 35; only these are named, production and tests stay without a mode. */
    static final List<String> LOCAL_MODES = List.of("dev", "sample");

    /** {@code mode} is the local profile (ticket #82), so the app shows which database it uses; null in production. */
    public record HealthResponse(String status, String mode) {
    }

    private final Environment environment;

    public HealthController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("UP", mode(environment));
    }

    /** The active local profile; without an active profile the default one applies (dev, decision 35). */
    static String mode(Environment environment) {
        String[] active = environment.getActiveProfiles();
        String[] profiles = active.length > 0 ? active : environment.getDefaultProfiles();
        return Arrays.stream(profiles).filter(LOCAL_MODES::contains).findFirst().orElse(null);
    }
}
