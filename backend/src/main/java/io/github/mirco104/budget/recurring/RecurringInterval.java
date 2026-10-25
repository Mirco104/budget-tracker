package io.github.mirco104.budget.recurring;

/** Interval of a recurring transaction in months (R12). */
public enum RecurringInterval {
    MONTHLY(1),
    QUARTERLY(3),
    HALF_YEARLY(6),
    YEARLY(12);

    private final int months;

    RecurringInterval(int months) {
        this.months = months;
    }

    public int months() {
        return months;
    }
}
