package io.github.mirco104.budget.importer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The rows of a preview the user chose, sent back to be stored (decision 44). The backend checks them like any
 * booking (R11, R15, R27, R30).
 */
public record ImportRequest(
        @NotNull(message = "Die Bank ist Pflicht.")
        BankProfile profile,

        @NotNull(message = "Die Zuordnung der Konten ist Pflicht.")
        List<@Valid SourceMapping> sources,

        @NotNull(message = "Die Zeilen sind Pflicht.")
        @Size(max = 5000, message = "Höchstens 5000 Zeilen.")
        List<@Valid Row> rows) {

    /** A source of the file goes to an own account (accountId) or to a new one (newAccountName). */
    public record SourceMapping(
            @NotBlank(message = "Die Quelle ist Pflicht.")
            @Size(max = 120)
            String key,

            Long accountId,

            @Size(max = 50, message = "Der Name darf höchstens 50 Zeichen haben.")
            String newAccountName) {
    }

    /** One row: categoryId is required unless transfer is true (R27); transferPeerId only with a transfer. */
    public record Row(
            @NotBlank(message = "Der Fingerabdruck ist Pflicht.")
            @Size(min = 64, max = 64, message = "Der Fingerabdruck ist ungültig.")
            String fingerprint,

            @NotBlank(message = "Die Quelle ist Pflicht.")
            String source,

            @NotNull(message = "Das Datum ist Pflicht.")
            LocalDate date,

            @NotNull(message = "Der Betrag ist Pflicht.")
            @Digits(integer = 10, fraction = 2, message = "Der Betrag darf höchstens 2 Nachkommastellen haben.")
            BigDecimal amount,

            @NotBlank(message = "Die Beschreibung ist Pflicht.")
            @Size(max = 100, message = "Die Beschreibung darf höchstens 100 Zeichen lang sein.")
            String description,

            @Size(max = 100)
            String counterparty,

            @Size(max = 100)
            String bankCategory,

            Long categoryId,

            Boolean transfer,

            Long transferPeerId) {
    }
}
