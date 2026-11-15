package io.github.mirco104.budget.importer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The result of an import (US33): replacedAuto counts the automatic entries the rows replaced (P6), missingAuto lists
 * the automatic entries of the accounts of the file within its dates that the file does not contain; the user deletes
 * or keeps them.
 */
public record ImportResultResponse(
        ImportBatchResponse batch, int created, int skippedDuplicates, int replacedAuto, List<MissingAuto> missingAuto) {

    public record MissingAuto(long id, LocalDate date, String description, BigDecimal amount, String accountName) {
    }
}
