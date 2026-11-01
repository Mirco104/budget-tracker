package io.github.mirco104.budget.account;

import io.github.mirco104.budget.recurring.RecurringInterval;
import io.github.mirco104.budget.user.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** US16: all data of the signed-in user as JSON. No ids of other users, no password hash. */
public record AccountExport(
        Instant exportedAt,
        Account account,
        List<CategoryExport> categories,
        List<TransactionExport> transactions,
        List<RecurringExport> recurringTransactions) {

    public record Account(String email, String displayName, Role role, Instant createdAt) {
    }

    public record CategoryExport(String name, int sortOrder, boolean archived, List<TargetExport> targets) {
    }

    public record TargetExport(YearMonth validFrom, BigDecimal targetAmount, BigDecimal targetPercent) {
    }

    public record TransactionExport(LocalDate date, BigDecimal amount, String description, String category) {
    }

    public record RecurringExport(
            UUID seriesId, BigDecimal amount, String description, String category, RecurringInterval interval,
            LocalDate startDate, LocalDate endDate, List<LocalDate> exceptions) {
    }
}
