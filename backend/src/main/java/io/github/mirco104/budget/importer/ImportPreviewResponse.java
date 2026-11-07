package io.github.mirco104.budget.importer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The preview of a bank file (R30, US33); nothing is stored. rows counts every line after the header, newRows those
 * that can be imported (NEW and POSSIBLE_DUPLICATE), duplicates the ones imported before, skipped the others.
 */
public record ImportPreviewResponse(
        BankProfile profile, String profileLabel, int rows, int newRows, int duplicates, int skipped,
        List<Source> sources, List<Entry> entries) {

    public enum Status { NEW, POSSIBLE_DUPLICATE, DUPLICATE, SKIPPED }

    /** A source account of the file; accountId is the own account it is put on so far, else null. */
    public record Source(String key, String name, Long accountId, String suggestedName) {
    }

    /** A booking by hand that may be the same as the row (R30). */
    public record DuplicateOf(long id, LocalDate date, String description) {
    }

    /**
     * One line of the file. For SKIPPED only line, status and reason are set. categoryId is the suggestion (R29) and
     * certain its certainty; transfer is the suggestion of R27 with transferCertain, transferPeerId a stored
     * counterpart that becomes a transfer as well.
     */
    public record Entry(
            int line, Status status, String reason, String fingerprint, String source, LocalDate date, BigDecimal amount,
            String description, String counterparty, String bankCategory, String suggestionKey, Long categoryId,
            boolean certain, boolean transfer, boolean transferCertain, Long transferPeerId, DuplicateOf duplicateOf) {

        static Entry skipped(int line, String reason) {
            return new Entry(line, Status.SKIPPED, reason, null, null, null, null, null, null, null, null, null, false,
                    false, false, null, null);
        }
    }
}
