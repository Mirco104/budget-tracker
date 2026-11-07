package io.github.mirco104.budget.importer;

import java.time.Instant;
import java.time.LocalDate;

/** A stored import (US34): its bank, when it was made, the period and the number of its transactions. */
public record ImportBatchResponse(
        Long id, BankProfile profile, String profileLabel, Instant createdAt, LocalDate firstDate, LocalDate lastDate,
        int count) {

    static ImportBatchResponse from(ImportBatch batch) {
        return new ImportBatchResponse(batch.getId(), batch.getProfile(), batch.getProfile().label(),
                batch.getCreatedAt(), batch.getFirstDate(), batch.getLastDate(), batch.getCount());
    }
}
