package io.github.mirco104.budget.plan;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Create or change a planned item (section 19.4). The amount is the real amount per payment with its sign (R11); for a
 * saving item the sign is ignored. rhythmUnit null means "ohne festen Termin", then rhythmCount and anchorDate are
 * ignored. entryMode null takes the default of the group (decision 51). validFrom is the month from which the item or
 * the change applies.
 */
public record PlannedItemRequest(
        @NotBlank(message = "Der Name ist Pflicht.")
        @Size(max = 100, message = "Der Name darf höchstens 100 Zeichen lang sein.")
        String name,
        @NotNull(message = "Der Betrag ist Pflicht.")
        @Digits(integer = 10, fraction = 2, message = "Der Betrag darf höchstens 2 Nachkommastellen haben.")
        BigDecimal amount,
        Boolean saving,
        Long categoryId,
        Long accountId,
        RhythmUnit rhythmUnit,
        @Min(value = 1, message = "Die Anzahl muss mindestens 1 sein.")
        @Max(value = 52, message = "Die Anzahl darf höchstens 52 sein.")
        Integer rhythmCount,
        LocalDate anchorDate,
        EntryMode entryMode,
        Boolean workdaysOnly,
        @NotNull(message = "„Gilt ab“ ist Pflicht.")
        YearMonth validFrom,
        // Concept 3.6: the saving goal of a saving item, optional
        Long savingGoalId) {
}
