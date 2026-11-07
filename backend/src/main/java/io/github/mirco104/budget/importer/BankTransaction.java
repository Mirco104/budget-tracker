package io.github.mirco104.budget.importer;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One booking of a bank, normalized by its source (decision 46). The fields follow what ISO 20022 (CAMT.053) and the
 * PSD2 interfaces (Berlin Group) use for a booking, so a later CAMT.053 import or bank API is only another adapter. A
 * field the source does not deliver is null; amount is signed (R11).
 *
 * @param accountName           the account of the bank the booking belongs to (C24 Kontoname, N26 Account Name, Trade
 *                              Republic account_type)
 * @param accountIban           IBAN of that account
 * @param bookingDate           booking date; null for a pending booking without one
 * @param valueDate             value date
 * @param currency              ISO 4217 code of the amount
 * @param status                booked or pending
 * @param counterpartyName      the other side (payee or payer)
 * @param remittanceInformation the purpose
 * @param endToEndId            end-to-end reference of a transfer
 * @param mandateId             mandate reference of a direct debit
 * @param creditorId            creditor identifier of a direct debit
 * @param bankTransactionCode   the kind of booking as the bank names it (Transaktionstyp, Type, type)
 * @param entryReference        the bank's own id of the booking (Trade Republic transaction_id)
 * @param bankCategory          the bank's own category, outside ISO 20022 (C24 "Kategorie › Unterkategorie")
 */
public record BankTransaction(
        String accountName, String accountIban, LocalDate bookingDate, LocalDate valueDate, BigDecimal amount,
        String currency, Status status, String counterpartyName, String counterpartyIban, String counterpartyBic,
        String remittanceInformation, String endToEndId, String mandateId, String creditorId,
        String bankTransactionCode, String entryReference, String bankCategory) {

    /** ISO 20022 entry status: BOOK and PDNG. */
    public enum Status { BOOKED, PENDING }
}
