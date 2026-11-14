package io.github.mirco104.budget.importer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The preview of a bank file (R30, US33); nothing is stored. rows counts every line after the header, newRows those
 * that can be imported (NEW and POSSIBLE_DUPLICATE), duplicates the ones imported before, skipped the others; series are
 * the monthly series the new rows suggest (R31).
 */
public record ImportPreviewResponse(
        BankProfile profile, String profileLabel, int rows, int newRows, int duplicates, int skipped,
        List<Source> sources, List<Entry> entries, List<SeriesSuggestion> series) {

    public enum Status { NEW, POSSIBLE_DUPLICATE, DUPLICATE, SKIPPED }

    /** A source account of the file; accountId is the own account it is put on so far, else null. */
    public record Source(String key, String name, Long accountId, String suggestedName) {
    }

    /**
     * P5: the term of a planned item the row fulfils, with the values of the item; recurringId is the id of the item
     * version (name kept from R28 for the import page). autoEntryId is the automatic entry the row replaces (P6).
     */
    public record Replaces(
            long recurringId, LocalDate date, String description, BigDecimal amount, Long categoryId,
            Long autoEntryId) {
    }

    /**
     * R31: rows that look like a monthly series that does not exist yet: their lines, the first occurrence, the median
     * amount with the smallest and the largest amount by size (issue #119), the description of the latest row, its source
     * and the category most of the rows were suggested (null without one).
     */
    public record SeriesSuggestion(
            List<Integer> lines, LocalDate startDate, BigDecimal amount, BigDecimal lowest, BigDecimal highest,
            String description, String source, Long categoryId) {
    }

    /**
     * Issue #119: what the bank says about a row, for its details in the preview; a field the bank does not deliver is
     * null. purpose is the purpose as the bank sends it (only whitespace joined), kind the kind of booking.
     */
    public record Details(
            String counterpartyIban, String purpose, String kind, LocalDate valueDate, String mandateId,
            String creditorId) {
    }

    /**
     * R27: the counterpart of a suggested transfer with its date, description, amount and account (the source for a row
     * of the file); line is set only for a row of the file, so the page can jump to it.
     */
    public record TransferPeer(Integer line, LocalDate date, String description, BigDecimal amount, String accountName) {
    }

    /** A booking by hand that may be the same as the row (R30). */
    public record DuplicateOf(long id, LocalDate date, String description) {
    }

    /**
     * One line of the file. For SKIPPED only line, status and reason are set. categoryId is the suggestion (R29) and
     * certain its certainty; transfer is the suggestion of R27 with transferCertain, transferPeerId a stored
     * counterpart that becomes a transfer as well and transferPeer the counterpart for the hint; replaces is the occurrence the row replaces (R28), and then
     * categoryId is the category of its series. details is what else the bank says about the row (issue #119).
     */
    public record Entry(
            int line, Status status, String reason, String fingerprint, String source, LocalDate date, BigDecimal amount,
            String description, String counterparty, String bankCategory, String suggestionKey, Long categoryId,
            boolean certain, boolean transfer, boolean transferCertain, Long transferPeerId, TransferPeer transferPeer,
            Replaces replaces,
            DuplicateOf duplicateOf, Details details) {

        static Entry skipped(int line, String reason) {
            return new Entry(line, Status.SKIPPED, reason, null, null, null, null, null, null, null, null, null, false,
                    false, false, null, null, null, null, null);
        }
    }
}
