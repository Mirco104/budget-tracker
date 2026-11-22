package io.github.mirco104.budget.importer;

import io.github.mirco104.budget.plan.TermRef;
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
        List<@Valid Row> rows,

        // R31: monthly series created with the import; optional
        @Size(max = 500)
        List<@Valid NewSeries> series) {

    /** The series of the request, empty without one. */
    public List<NewSeries> seriesOrEmpty() {
        return series == null ? List.of() : series;
    }

    /**
     * R31: a monthly series that the import creates; its rows replace its occurrences (source: its account). A saving
     * series (decision 67) has no category, every other one needs one.
     */
    public record NewSeries(
            @NotNull(message = "Das Startdatum ist Pflicht.")
            LocalDate startDate,

            @NotNull(message = "Der Betrag ist Pflicht.")
            @Digits(integer = 10, fraction = 2, message = "Der Betrag darf höchstens 2 Nachkommastellen haben.")
            BigDecimal amount,

            @NotBlank(message = "Die Beschreibung ist Pflicht.")
            @Size(max = 100, message = "Die Beschreibung darf höchstens 100 Zeichen lang sein.")
            String description,

            Long categoryId,

            @NotBlank(message = "Die Quelle ist Pflicht.")
            String source,

            Boolean saving) {

        public boolean isSaving() {
            return Boolean.TRUE.equals(saving);
        }
    }

    /** A source of the file goes to an own account (accountId) or to a new one (newAccountName). */
    public record SourceMapping(
            @NotBlank(message = "Die Quelle ist Pflicht.")
            @Size(max = 120)
            String key,

            Long accountId,

            @Size(max = 50, message = "Der Name darf höchstens 50 Zeichen haben.")
            String newAccountName,

            // Decision 92: the balance of the file taken over for the account; both or none
            @jakarta.validation.constraints.PastOrPresent(message = "Der Stichtag darf nicht in der Zukunft liegen.")
            LocalDate balanceDate,

            @Digits(integer = 10, fraction = 2, message = "Der Betrag darf höchstens 2 Nachkommastellen haben.")
            BigDecimal balanceAmount) {

        public SourceMapping(String key, Long accountId, String newAccountName) {
            this(key, accountId, newAccountName, null, null);
        }

        /** Whether the balance of the file is taken over. */
        public boolean takesBalance() {
            return balanceDate != null && balanceAmount != null;
        }
    }

    /**
     * One row: categoryId is required unless transfer or saving is true (R27); transferPeerId only with a transfer or a
     * saving row. saving is the kind Sparen (P1, decision 67), a transfer that counts as saving.
     */
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

            Long transferPeerId,

            // P5: the term of a planned item the row fulfils
            @Valid
            TermRef replaces,

            // R31: the index of the new series in "series" whose occurrence the row replaces
            Integer series,

            Boolean saving) {

        public boolean isSaving() {
            return Boolean.TRUE.equals(saving);
        }

        /** A transfer that is no saving: it fulfils no term and belongs to no series (R27). */
        boolean isPlainTransfer() {
            return Boolean.TRUE.equals(transfer) && !isSaving();
        }
    }
}
