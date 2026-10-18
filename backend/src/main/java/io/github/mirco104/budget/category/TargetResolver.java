package io.github.mirco104.budget.category;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rule R3 from docs/spezifikation.md: a target entry applies from its month onward until a newer entry
 * replaces it. No Spring dependencies.
 */
public final class TargetResolver {

    /** targetPercent is a fraction of income (0..1). Both values null means "no target". */
    public record TargetEntry(long categoryId, YearMonth validFrom, BigDecimal targetAmount, BigDecimal targetPercent) {
    }

    private TargetResolver() {
    }

    /** Per category the entry with the latest validFrom ≤ month; categories without one are missing. */
    public static Map<Long, TargetEntry> inEffect(List<TargetEntry> entries, YearMonth month) {
        Map<Long, TargetEntry> result = new HashMap<>();
        for (TargetEntry entry : entries) {
            if (entry.validFrom().isAfter(month)) {
                continue;
            }
            result.merge(entry.categoryId(), entry,
                    (current, candidate) -> candidate.validFrom().isAfter(current.validFrom()) ? candidate : current);
        }
        return result;
    }
}
