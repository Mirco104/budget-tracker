package io.github.mirco104.budget.importer;

/**
 * The result of storing an import: the new batch (null when nothing was new), how many transactions it created and how
 * many rows were left out because they had been imported before (R30).
 */
public record ImportResultResponse(ImportBatchResponse batch, int created, int skippedDuplicates) {
}
