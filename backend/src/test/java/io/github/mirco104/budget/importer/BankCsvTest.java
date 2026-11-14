package io.github.mirco104.budget.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mirco104.budget.importer.BankCsv.BankFile;
import io.github.mirco104.budget.importer.BankTransaction.Status;
import io.github.mirco104.budget.importer.BankCsv.Line;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Rule R30: reading the CSV exports of C24, Trade Republic, N26, the Sparkasse and the VR-Bank. The files have the format of the real exports
 * (header, delimiter, number and date format) with fictional values only: no real account numbers, IBANs or names.
 */
class BankCsvTest {

    // Fictional; the IBANs are the ISO 13616 documentation examples, not real accounts of anyone
    static final String C24 = """
            Transaktionstyp,Buchungsdatum,Karteneinsatz,Betrag,Zahlungsempfänger,IBAN,BIC,Verwendungszweck,Beschreibung,Kontonummer,Kontoname,Kategorie,Unterkategorie,Bargeldabhebung
            Online-Kartenzahlung,04.11.2026,03.11.2026 14:25,"-2,99 €",SPIELE SHOP,,,,SPIELESHOP.EXAMPLE/BILL,1000000001,Ausgabenkonto,Freizeit & Unterhaltung,Digitale Produkte & Dienste,
            Überweisung,03.11.2026,,"-1.234,50 €",Person B,DE89370400440532013000,COBADEFFXXX,Miete November,,1000000001,Ausgabenkonto,Wohnen,,
            Gutschrift,01.11.2026,,"2.500,00 €",Firma Beispiel GmbH,GB82WEST12345698765432,,Lohn 10/2026 Personalnummer 12345678,,1000000001,Ausgabenkonto,Einkommen,Gehalt,
            Kartenzahlung,,05.11.2026 09:10,"-4,20 €",BAECKEREI,,,,,1000000001,Ausgabenkonto,Lebensmittel,,
            Umbuchung,02.11.2026,,"-100,00 €",,,,Auf Pocket Urlaub,,1000000002,Pocket Urlaub,,,
            """;

    static final String TRADE_REPUBLIC = """
            "datetime","date","account_type","category","type","asset_class","name","symbol","shares","price","amount","fee","tax","currency","original_amount","original_currency","fx_rate","description","transaction_id","counterparty_name","counterparty_iban","payment_reference","mcc_code"
            "2026-01-01T02:05:08.289926Z","2026-01-01","DEFAULT","CASH","BENEFITS_SAVEBACK","","","","","","8.170000","","","EUR","","",""," Saveback cash reward f60809b4-17fa-40c5-bc2a-61c35543cba7 for reservation: fe2ed055-dff7-43ac-b61d-7e66758f9d16","00000000-0000-4000-8000-000000000001","","","",""
            "2026-01-02T10:00:00.000000Z","2026-01-02","DEFAULT","TRADING","SAVINGS_PLAN","FUND","Welt ETF","XX0000000001","1.5","100.0","-150.000000","","","EUR","","","","","00000000-0000-4000-8000-000000000002","","","",""
            "2026-01-03T10:00:00.000000Z","2026-01-03","DEFAULT","CASH","TRANSFER_INCOMING","","","","","","500.00","","","EUR","","","","","00000000-0000-4000-8000-000000000003","Person A","DE89370400440532013000","Sparen",""
            "2026-01-04T10:00:00.000000Z","2026-01-04","DEFAULT","CASH","CARD_TRANSACTION","","","","","","-12.345","","","USD","","","","Online Shop","00000000-0000-4000-8000-000000000004","","","","5999"
            "2026-01-05T10:00:00.000000Z","2026-01-05","DEFAULT","TRADING","SECURITY_TRANSFER","STOCK","Beispiel AG","XX0000000002","2","","","","","EUR","","","","","00000000-0000-4000-8000-000000000005","","","",""
            """;

    static final String N26 = """
            "Booking Date","Value Date","Partner Name","Partner Iban",Type,"Payment Reference","Account Name","Amount (EUR)","Original Amount","Original Currency","Exchange Rate"
            2026-01-01,2026-01-01,N26,,Reward,"N26 Cashback",Hauptkonto,0.01,,,
            2026-01-12,2026-01-13,"Versand Händler S.a.r.l.",LU280019400644750000,"Credit Transfer",Penny-Test,Hauptkonto,0.01,,,
            2026-01-14,2026-01-14,"Kino ""Am Markt""\",,Presentment,"Kino, 2 Karten",Hauptkonto,-24.00,,,
            """;

    // Sparkasse (decision 47): the same fictional bookings in three of the four exports (CSV-CAMT V2 and V8 are equal).
    // Every field quoted, ";", dd.MM.yy, a credit without sign; the IBANs are ISO 13616 documentation examples.
    static final String SPARKASSE_CAMT = """
            "Auftragskonto";"Buchungstag";"Valutadatum";"Buchungstext";"Verwendungszweck";"Glaeubiger ID";"Mandatsreferenz";"Kundenreferenz (End-to-End)";"Sammlerreferenz";"Lastschrift Ursprungsbetrag";"Auslagenersatz Ruecklastschrift";"Beguenstigter/Zahlungspflichtiger";"Kontonummer/IBAN";"BIC (SWIFT-Code)";"Betrag";"Waehrung";"Info"
            "DE89370400440532013000";"05.11.26";"05.11.26";"FOLGELASTSCHRIFT";"Beitrag 11/2026 : Mitglied 4711 ";"DE00ZZZ00000000001";"M-0001";"E2E-0001";"";"";"";"Beispiel Verein e.V.";"GB82WEST12345698765432";"WESTGB2L   ";"-12,00";"EUR";"Umsatz gebucht"
            "DE89370400440532013000";"31.10.26";"01.11.26";"GUTSCHR. UEBERWEISUNG";"Lohn 10/2026 ";"";"";"";"";"";"";"Firma Beispiel GmbH";"GB29NWBK60161331926819";"NWBKGB2L";"2.500,00";"EUR";"Umsatz gebucht"
            "DE89370400440532013000";"30.10.26";"01.11.26";"ABSCHLUSS";"Entgeltabrechnung siehe Anlage ";"";"";"";"";"";"";"";"";"";"0,00";"EUR";"Umsatz gebucht"
            "DE89370400440532013000";"06.11.26";"06.11.26";"KARTENZAHLUNG";"Baeckerei Beispiel ";"";"";"";"";"";"";"Baeckerei Beispiel";"";"";"-4,20";"EUR";"Umsatz vorgemerkt"
            "DE89370400440532013000";"";"06.11.26";"KARTENZAHLUNG";"Kiosk ";"";"";"";"";"";"";"Kiosk Beispiel";"";"";"-2,00";"EUR";""
            "DE89370400440532013000";"04.11.26";"04.11.26";"KARTENZAHLUNG";"Shop ";"";"";"";"";"";"";"Shop Beispiel";"";"";"-9,99";"EUR";"Umsatz storniert"
            """;

    static final String SPARKASSE_MT940 = """
            "Auftragskonto";"Buchungstag";"Valutadatum";"Buchungstext";"Verwendungszweck";"Beguenstigter/Zahlungspflichtiger";"Kontonummer";"BLZ";"Betrag";"Waehrung";"Info"
            "DE89370400440532013000";"05.11.26";"05.11.26";"FOLGELASTSCHRIFT";"EREF+E2E-0001MREF+M-0001CRED+DE00ZZZ00000000001SVWZ+Beitrag 11/2026 . Mitglied4711ABWA+Beispiel Dachverband";"Beispiel Verein e.V.";"GB82WEST12345698765432";"WESTGB2L";"-12,00";"EUR";"Umsatz gebucht"
            "DE89370400440532013000";"31.10.26";"01.11.26";"GUTSCHR. UEBERWEISUNG";"SVWZ+Lohn 10/2026";"Firma Beispiel GmbH";"GB29NWBK60161331926819";"NWBKGB2L";"2.500,00";"EUR";"Umsatz gebucht"
            "DE89370400440532013000";"03.11.26";"03.11.26";"FOLGELASTSCHRIFT";"EREF+E2E-0002MREF+M-0002CRED+DE00ZZZ00000000002SVWZ+Abo+ Monat 11";"Beispiel Medien";"GB82WEST12345698765432";"WESTGB2L";"-11,75";"EUR";"Umsatz gebucht"
            """;

    // "mit Kategorien" is Windows-1252 and puts the end-to-end reference into Sammlerreferenz
    static final String SPARKASSE_CATEGORIES = """
            "Auftragskonto";"Buchungstag";"Valutadatum";"Buchungstext";"Verwendungszweck";"Glaeubiger ID";"Mandatsreferenz";"Kundenreferenz (End-to-End)";"Sammlerreferenz";"Lastschrift Ursprungsbetrag";"Auslagenersatz Ruecklastschrift";"Beguenstigter/Zahlungspflichtiger";"Kontonummer/IBAN";"BIC (SWIFT-Code)";"Betrag";"Waehrung";"Info";"Kategorie"
            "DE89370400440532013000";"05.11.26";"05.11.26";"FOLGELASTSCHRIFT";"Beitrag 11/2026 : Mitglied 4711 ";"DE00ZZZ00000000001";"M-0001";"";"E2E-0001";"";"";"Beispiel Verein e.V.";"GB82WEST12345698765432";"WESTGB2L   ";"-12,00";"EUR";"Umsatz gebucht";"Sport und Hobby"
            "DE89370400440532013000";"31.10.26";"01.11.26";"GUTSCHR. UEBERWEISUNG";"Lohn 10/2026 ";"";"";"";"";"";"";"Firma Beispiel GmbH";"GB29NWBK60161331926819";"NWBKGB2L";"2.500,00";"EUR";"Umsatz gebucht";"Einkünfte"
            """;

    // VR-Bank (decision 64): ";", no quotes, dd.MM.yyyy, signed German amounts, newest first; SEPA tags with ":" at the
    // end of the purpose, card data after the merchant. All values fictional, the IBANs are ISO 13616 examples.
    static final String VR_BANK = """
            Bezeichnung Auftragskonto;IBAN Auftragskonto;BIC Auftragskonto;Bankname Auftragskonto;Buchungstag;Valutadatum;Name Zahlungsbeteiligter;IBAN Zahlungsbeteiligter;BIC (SWIFT-Code) Zahlungsbeteiligter;Buchungstext;Verwendungszweck;Betrag;Waehrung;Saldo nach Buchung;Bemerkung;Gekennzeichneter Umsatz;Glaeubiger ID;Mandatsreferenz
            Girokonto;DE89370400440532013000;GENODEF1XXX;Beispielbank eG;10.11.2026;10.11.2026;Beispiel Payment GmbH;GB82WEST12345698765432;WESTGB2L;Kartenzahlung girocard;Baeckerei Beispiel/Hauptstr. 1/Musterstadt/DE         09.11.2026 um 10:54:09 Uhr 12345678/123456/ECTL/NPIN  12345678/0001234567/1/1230 REF 123456/260024;-4,20;EUR;1395,85;;;DE00ZZZ00000000009;123456
            Girokonto;DE89370400440532013000;GENODEF1XXX;Beispielbank eG;06.11.2026;06.11.2026;Beispielbank eG;;;Auszahlung girocard;Beispielbank eG                             GS001 Musterstadt/Musterstadt/DE                      06.11.2026/18:25 girocard  GA 12345678/00001234/00123456/0001234567/1/1230;-90,00;EUR;1400,05;;;;
            Girokonto;DE89370400440532013000;GENODEF1XXX;Beispielbank eG;05.11.2026;05.11.2026;Beispiel Medien GmbH;GB29NWBK60161331926819;NWBKGB2L;LASTSCHRIFT;Abo 11/2026 Kunde 4711 EREF: E2E-0001 MREF: M-0001 CRED: DE00ZZZ00000000001 IBAN: GB29NWBK60161331926819 BIC: NWBKGB2L;-11,75;EUR;1490,05;;;DE00ZZZ00000000001;M-0001
            Girokonto;DE89370400440532013000;GENODEF1XXX;Beispielbank eG;01.11.2026;01.11.2026;Person B;GB82WEST12345698765432;WESTGB2L;DAUERAUFTRAG;Miete /*DA-1* IBAN: GB82WEST12345698765432 BIC: WESTGB2L;-510,00;EUR;1501,80;;;;
            Girokonto;DE89370400440532013000;GENODEF1XXX;Beispielbank eG;30.10.2026;31.10.2026;Firma Beispiel GmbH;GB29NWBK60161331926819;NWBKGB2L;LOHN/GEHALT;Lohn 10/2026 EREF: E2E-0002 ABWA: Firma Beispiel Personal;2500,00;EUR;2011,80;;;;
            Girokonto;DE89370400440532013000;GENODEF1XXX;Beispielbank eG;29.10.2026;29.10.2026;Kiosk Beispiel;GB82WEST12345698765432;WESTGB2L;Kartenzahlung girocard;/Musterstadt/DE         28.10.2026 um 08:00:00 Uhr 12345678/123457/ECTL/      12345678/0001234567/1/1230 REF 123457/260024;-2,50;EUR;-488,20;;;;
            Girokonto;DE89370400440532013000;GENODEF1XXX;Beispielbank eG;;11.11.2026;Beispiel Payment GmbH;GB82WEST12345698765432;WESTGB2L;Kartenzahlung girocard;Kiosk Beispiel/Musterstadt/DE         11.11.2026 um 09:00:00 Uhr 12345678/123458/ECTL/      12345678/0001234567/1/1230 REF 123458/260024;-3,00;EUR;;;;;
            """;

    @Test
    void readsTheVrBankExport() {
        BankFile file = BankCsv.read(("﻿" + VR_BANK.replace("\n", "\r\n")).getBytes(StandardCharsets.UTF_8));

        assertThat(file.profile()).isEqualTo(BankProfile.VR_BANK);
        BankTransaction debit = row(file, 4);
        assertThat(debit.accountName()).isEqualTo("DE89370400440532013000");
        assertThat(debit.accountIban()).isEqualTo("DE89370400440532013000");
        assertThat(debit.bookingDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(debit.amount()).isEqualByComparingTo("-11.75");
        assertThat(debit.currency()).isEqualTo("EUR");
        assertThat(debit.status()).isEqualTo(Status.BOOKED);
        assertThat(debit.counterpartyName()).isEqualTo("Beispiel Medien GmbH");
        assertThat(debit.counterpartyIban()).isEqualTo("GB29NWBK60161331926819");
        assertThat(debit.counterpartyBic()).isEqualTo("NWBKGB2L");
        // The purpose ends before the first tag; the end-to-end reference comes from "EREF: ", the rest from the columns
        assertThat(debit.remittanceInformation()).isEqualTo("Abo 11/2026 Kunde 4711");
        assertThat(debit.endToEndId()).isEqualTo("E2E-0001");
        assertThat(debit.mandateId()).isEqualTo("M-0001");
        assertThat(debit.creditorId()).isEqualTo("DE00ZZZ00000000001");
        assertThat(debit.bankTransactionCode()).isEqualTo("LASTSCHRIFT");
        assertThat(debit.bankCategory()).isEqualTo("LASTSCHRIFT");
        assertThat(debit.entryReference()).isNull();

        // The booking date, not the value date; "ABWA: " ends the end-to-end reference
        BankTransaction salary = row(file, 6);
        assertThat(salary.amount()).isEqualByComparingTo("2500.00");
        assertThat(salary.bookingDate()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(salary.valueDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(salary.remittanceInformation()).isEqualTo("Lohn 10/2026");
        assertThat(salary.endToEndId()).isEqualTo("E2E-0002");
        assertThat(salary.mandateId()).isNull();
        assertThat(salary.creditorId()).isNull();

        // The mark of a standing order goes
        assertThat(row(file, 5).remittanceInformation()).isEqualTo("Miete");
        assertThat(row(file, 5).endToEndId()).isNull();
    }

    // Decision 64: the merchant of a card payment is in the purpose; the column often names the payment provider
    @Test
    void aVrBankCardPaymentHasTheMerchantAsCounterparty() {
        BankFile file = BankCsv.read(VR_BANK.getBytes(StandardCharsets.UTF_8));

        BankTransaction card = row(file, 2);
        assertThat(card.counterpartyName()).isEqualTo("Baeckerei Beispiel");
        assertThat(card.remittanceInformation()).isEqualTo("Baeckerei Beispiel/Hauptstr. 1/Musterstadt/DE");
        assertThat(card.amount()).isEqualByComparingTo("-4.20");
        assertThat(card.counterpartyIban()).isEqualTo("GB82WEST12345698765432");
        assertThat(card.creditorId()).isEqualTo("DE00ZZZ00000000009");

        // Without a merchant the column stays
        assertThat(row(file, 7).counterpartyName()).isEqualTo("Kiosk Beispiel");
        assertThat(row(file, 7).remittanceInformation()).isEqualTo("/Musterstadt/DE");

        // At a cash machine the card data after date and time goes too, the counterparty stays the bank
        BankTransaction cash = row(file, 3);
        assertThat(cash.counterpartyName()).isEqualTo("Beispielbank eG");
        assertThat(cash.remittanceInformation()).startsWith("Beispielbank eG").endsWith("GS001 Musterstadt/Musterstadt/DE");
        assertThat(cash.counterpartyIban()).isNull();
    }

    @Test
    void aVrBankRowWithoutBookingDateIsPending() {
        BankTransaction pending = row(BankCsv.read(VR_BANK.getBytes(StandardCharsets.UTF_8)), 8);

        assertThat(pending.status()).isEqualTo(Status.PENDING);
        assertThat(pending.bookingDate()).isNull();
        assertThat(pending.valueDate()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(pending.counterpartyName()).isEqualTo("Kiosk Beispiel");
    }

    @Test
    void readsTheSepaTagsWithColonOfTheVrBank() {
        assertThat(BankCsv.colonTags("SecureGo plus IBAN: GB82WEST12345698765432 BIC: WESTGB2L"))
                .containsEntry("", "SecureGo plus")
                .containsEntry("IBAN", "GB82WEST12345698765432")
                .containsEntry("BIC", "WESTGB2L");
        // The value of a tag runs to the next tag, spaces included
        assertThat(BankCsv.colonTags("Tanken 04.11 EREF: ELV123 04.11 11.07 ME1 MREF: 99"))
                .containsEntry("", "Tanken 04.11")
                .containsEntry("EREF", "ELV123 04.11 11.07 ME1")
                .containsEntry("MREF", "99");
        // A tag only counts after whitespace or at the start, with ": "
        assertThat(BankCsv.colonTags("XIBAN: 1 und EREF:2")).containsOnlyKeys("");
        assertThat(BankCsv.colonTags("EREF: E2E-1")).containsOnlyKeys("EREF");
    }

    @Test
    void readsTheSparkasseCamtExport() {
        BankFile file = BankCsv.read(SPARKASSE_CAMT.getBytes(StandardCharsets.US_ASCII));

        assertThat(file.profile()).isEqualTo(BankProfile.SPARKASSE);
        BankTransaction debit = row(file, 2);
        assertThat(debit.accountName()).isEqualTo("DE89370400440532013000");
        assertThat(debit.accountIban()).isEqualTo("DE89370400440532013000");
        assertThat(debit.bookingDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(debit.amount()).isEqualByComparingTo("-12.00");
        assertThat(debit.currency()).isEqualTo("EUR");
        assertThat(debit.status()).isEqualTo(Status.BOOKED);
        assertThat(debit.counterpartyName()).isEqualTo("Beispiel Verein e.V.");
        assertThat(debit.counterpartyIban()).isEqualTo("GB82WEST12345698765432");
        assertThat(debit.counterpartyBic()).isEqualTo("WESTGB2L");
        assertThat(debit.remittanceInformation()).isEqualTo("Beitrag 11/2026 : Mitglied 4711");
        assertThat(debit.creditorId()).isEqualTo("DE00ZZZ00000000001");
        assertThat(debit.mandateId()).isEqualTo("M-0001");
        assertThat(debit.endToEndId()).isEqualTo("E2E-0001");
        assertThat(debit.bankTransactionCode()).isEqualTo("FOLGELASTSCHRIFT");
        assertThat(debit.bankCategory()).isEqualTo("FOLGELASTSCHRIFT");
        assertThat(debit.entryReference()).isNull();

        // A credit has no sign; the booking date, not the value date, and the year 2026 from "26"
        BankTransaction salary = row(file, 3);
        assertThat(salary.amount()).isEqualByComparingTo("2500.00");
        assertThat(salary.bookingDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(salary.valueDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(salary.creditorId()).isNull();
        assertThat(skipReason(file, 4)).isEqualTo("ohne Betrag");
    }

    // Decision 47: no sample had a pending row; "vorgemerkt" or an empty Buchungstag is pending, other states are not read
    @Test
    void readsPendingSparkasseRowsAndRejectsAnUnknownState() {
        BankFile file = BankCsv.read(SPARKASSE_CAMT.getBytes(StandardCharsets.US_ASCII));

        assertThat(row(file, 5).status()).isEqualTo(Status.PENDING);
        assertThat(row(file, 5).bookingDate()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(row(file, 6).status()).isEqualTo(Status.PENDING);
        assertThat(row(file, 6).bookingDate()).isNull();
        assertThat(skipReason(file, 7)).isEqualTo("Status unbekannt: Umsatz storniert");
    }

    @Test
    void readsTheSepaTagsOfTheSparkasseMt940Export() {
        BankFile file = BankCsv.read(SPARKASSE_MT940.getBytes(StandardCharsets.US_ASCII));

        assertThat(file.profile()).isEqualTo(BankProfile.SPARKASSE);
        BankTransaction debit = row(file, 2);
        // The purpose ends before the next tag (ABWA+)
        assertThat(debit.remittanceInformation()).isEqualTo("Beitrag 11/2026 . Mitglied4711");
        assertThat(debit.endToEndId()).isEqualTo("E2E-0001");
        assertThat(debit.mandateId()).isEqualTo("M-0001");
        assertThat(debit.creditorId()).isEqualTo("DE00ZZZ00000000001");
        assertThat(debit.counterpartyIban()).isEqualTo("GB82WEST12345698765432");
        assertThat(debit.counterpartyBic()).isEqualTo("WESTGB2L");
        assertThat(row(file, 3).remittanceInformation()).isEqualTo("Lohn 10/2026");
        assertThat(row(file, 3).amount()).isEqualByComparingTo("2500.00");
        // "Abo+" is no tag
        assertThat(row(file, 4).remittanceInformation()).isEqualTo("Abo+ Monat 11");
    }

    @Test
    void readsTheSparkasseExportWithCategoriesInWindows1252() {
        BankFile file = BankCsv.read(SPARKASSE_CATEGORIES.getBytes(Charset.forName("windows-1252")));

        assertThat(file.profile()).isEqualTo(BankProfile.SPARKASSE);
        BankTransaction debit = row(file, 2);
        assertThat(debit.bankCategory()).isEqualTo("Sport und Hobby");
        assertThat(debit.endToEndId()).isEqualTo("E2E-0001");
        assertThat(row(file, 3).bankCategory()).isEqualTo("Einkünfte");
    }

    // Decision 47: one booking is the same in every export, apart from the purpose that MT940 changes
    @Test
    void theSparkasseExportsGiveTheSameBooking() {
        BankTransaction camt = row(BankCsv.read(SPARKASSE_CAMT.getBytes(StandardCharsets.US_ASCII)), 2);
        BankTransaction mt940 = row(BankCsv.read(SPARKASSE_MT940.getBytes(StandardCharsets.US_ASCII)), 2);
        BankTransaction categories = row(BankCsv.read(SPARKASSE_CATEGORIES.getBytes(Charset.forName("windows-1252"))), 2);

        for (BankTransaction other : List.of(mt940, categories)) {
            assertThat(other).usingRecursiveComparison()
                    .ignoringFields("remittanceInformation", "bankCategory")
                    .isEqualTo(camt);
        }
    }

    @Test
    void readsC24WithGermanAmountsAndTheAccountName() {
        BankFile file = BankCsv.read(C24.getBytes(StandardCharsets.UTF_8));

        assertThat(file.profile()).isEqualTo(BankProfile.C24);
        BankTransaction card = row(file, 2);
        assertThat(card.bookingDate()).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(card.amount()).isEqualByComparingTo("-2.99");
        assertThat(card.amount().scale()).isEqualTo(2);
        assertThat(card.accountName()).isEqualTo("Ausgabenkonto");
        assertThat(card.counterpartyName()).isEqualTo("SPIELE SHOP");
        // No Verwendungszweck: the Beschreibung is the purpose
        assertThat(card.remittanceInformation()).isEqualTo("SPIELESHOP.EXAMPLE/BILL");
        assertThat(card.bankCategory()).isEqualTo("Freizeit & Unterhaltung › Digitale Produkte & Dienste");
        assertThat(card.bankTransactionCode()).isEqualTo("Online-Kartenzahlung");
        assertThat(card.entryReference()).isNull();

        BankTransaction rent = row(file, 3);
        assertThat(rent.amount()).isEqualByComparingTo("-1234.50");
        assertThat(rent.counterpartyIban()).isEqualTo("DE89370400440532013000");
        assertThat(rent.bankCategory()).isEqualTo("Wohnen");
        assertThat(row(file, 4).amount()).isEqualByComparingTo("2500.00");
    }

    // Decision 46: reading only normalizes; whether a pending row is imported is a rule (ImportRules.skipReason)
    @Test
    void readsAPendingC24RowWithoutBookingDateAsPending() {
        BankFile file = BankCsv.read(C24.getBytes(StandardCharsets.UTF_8));

        BankTransaction pending = row(file, 5);
        assertThat(pending.status()).isEqualTo(Status.PENDING);
        assertThat(pending.bookingDate()).isNull();
        assertThat(pending.amount()).isEqualByComparingTo("-4.20");
        assertThat(row(file, 2).status()).isEqualTo(Status.BOOKED);
        assertThat(row(file, 6).accountName()).isEqualTo("Pocket Urlaub");
    }

    @Test
    void fillsTheNormalizedFieldsEachBankHas() {
        BankTransaction rent = row(BankCsv.read(C24.getBytes(StandardCharsets.UTF_8)), 3);
        assertThat(rent.counterpartyBic()).isEqualTo("COBADEFFXXX");
        assertThat(rent.currency()).isEqualTo("EUR");
        assertThat(rent.valueDate()).isNull();

        BankTransaction transfer = row(BankCsv.read(N26.getBytes(StandardCharsets.UTF_8)), 3);
        assertThat(transfer.valueDate()).isEqualTo(LocalDate.of(2026, 1, 13));
        assertThat(transfer.currency()).isEqualTo("EUR");
        assertThat(transfer.bankTransactionCode()).isEqualTo("Credit Transfer");

        BankTransaction saveback = row(BankCsv.read(TRADE_REPUBLIC.getBytes(StandardCharsets.UTF_8)), 2);
        assertThat(saveback.currency()).isEqualTo("EUR");
        assertThat(saveback.status()).isEqualTo(Status.BOOKED);
        // No bank delivers these yet; they stay empty
        assertThat(saveback.endToEndId()).isNull();
        assertThat(saveback.mandateId()).isNull();
        assertThat(saveback.creditorId()).isNull();
        assertThat(saveback.accountIban()).isNull();
    }

    @Test
    void readsATradeRepublicRowWithoutTransactionId() {
        String file = TRADE_REPUBLIC.lines().findFirst().orElseThrow() + "\n"
                + "\"2026-01-06T10:00:00Z\",\"2026-01-06\",\"DEFAULT\",\"CASH\",\"INTEREST_PAYOUT\",\"\",\"\",\"\",\"\",\"\",\"1.23\",\"\",\"\",\"EUR\",\"\",\"\",\"\",\"Zinsen\",\"\",\"\",\"\",\"\",\"\"\n";

        BankTransaction interest = row(BankCsv.read(file.getBytes(StandardCharsets.UTF_8)), 2);

        assertThat(interest.entryReference()).isNull();
        assertThat(interest.amount()).isEqualByComparingTo("1.23");
    }

    @Test
    void readsTradeRepublicWithTransactionIdAndSecurities() {
        BankFile file = BankCsv.read(TRADE_REPUBLIC.getBytes(StandardCharsets.UTF_8));

        assertThat(file.profile()).isEqualTo(BankProfile.TRADE_REPUBLIC);
        BankTransaction saveback = row(file, 2);
        assertThat(saveback.amount()).isEqualByComparingTo("8.17");
        assertThat(saveback.amount().scale()).isEqualTo(2);
        assertThat(saveback.bookingDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(saveback.entryReference()).isEqualTo("00000000-0000-4000-8000-000000000001");
        assertThat(saveback.accountName()).isEqualTo("DEFAULT");
        assertThat(saveback.bankCategory()).isEqualTo("BENEFITS_SAVEBACK");
        assertThat(saveback.bankTransactionCode()).isEqualTo("BENEFITS_SAVEBACK");

        BankTransaction plan = row(file, 3);
        assertThat(plan.counterpartyName()).isEqualTo("Kauf Welt ETF");
        assertThat(plan.amount()).isEqualByComparingTo("-150.00");

        BankTransaction incoming = row(file, 4);
        assertThat(incoming.counterpartyName()).isEqualTo("Person A");
        assertThat(incoming.counterpartyIban()).isEqualTo("DE89370400440532013000");
        assertThat(incoming.remittanceInformation()).isEqualTo("Sparen");
    }

    @Test
    void readsAForeignCurrencyAndSkipsRowsWithoutMoney() {
        BankFile file = BankCsv.read(TRADE_REPUBLIC.getBytes(StandardCharsets.UTF_8));

        // The rule leaves it out later (ImportRules.skipReason)
        assertThat(row(file, 5).currency()).isEqualTo("USD");
        assertThat(skipReason(file, 6)).isEqualTo("ohne Betrag");
    }

    @Test
    void readsN26WithQuotesInsideFields() {
        BankFile file = BankCsv.read(N26.getBytes(StandardCharsets.UTF_8));

        assertThat(file.profile()).isEqualTo(BankProfile.N26);
        BankTransaction transfer = row(file, 3);
        // The booking date, not the value date
        assertThat(transfer.bookingDate()).isEqualTo(LocalDate.of(2026, 1, 12));
        assertThat(transfer.counterpartyIban()).isEqualTo("LU280019400644750000");
        assertThat(transfer.accountName()).isEqualTo("Hauptkonto");
        assertThat(transfer.bankCategory()).isEqualTo("Credit Transfer");
        BankTransaction cinema = row(file, 4);
        assertThat(cinema.counterpartyName()).isEqualTo("Kino \"Am Markt\"");
        assertThat(cinema.remittanceInformation()).isEqualTo("Kino, 2 Karten");
        assertThat(cinema.amount()).isEqualByComparingTo("-24.00");
    }

    @Test
    void skipsAPreambleAndReadsSemicolonsAndWindows1252() {
        String file = """
                Kontoauszug;Girokonto
                Zeitraum;01.11.2026 - 30.11.2026

                Transaktionstyp;Buchungsdatum;Betrag;Zahlungsempfänger;IBAN;Verwendungszweck;Beschreibung;Kontonummer;Kontoname;Kategorie;Unterkategorie
                Kartenzahlung;06.11.2026;-3,10 €;Bäckerei Müller;;Brötchen;;1000000001;Ausgabenkonto;Lebensmittel;
                """;

        BankFile parsed = BankCsv.read(file.getBytes(Charset.forName("windows-1252")));

        assertThat(parsed.profile()).isEqualTo(BankProfile.C24);
        BankTransaction row = row(parsed, 5);
        assertThat(row.counterpartyName()).isEqualTo("Bäckerei Müller");
        assertThat(row.remittanceInformation()).isEqualTo("Brötchen");
        assertThat(row.amount()).isEqualByComparingTo("-3.10");
    }

    @Test
    void readsUtf8WithByteOrderMarkAndWindowsLineBreaks() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = N26.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, bytes, 0, bom.length);
        System.arraycopy(body, 0, bytes, bom.length, body.length);

        BankFile file = BankCsv.read(bytes);

        assertThat(file.profile()).isEqualTo(BankProfile.N26);
        assertThat(row(file, 2).amount()).isEqualByComparingTo("0.01");
        assertThat(row(file, 2).accountName()).isEqualTo("Hauptkonto");
    }

    @Test
    void reportsUnreadableValuesPerLine() {
        String file = """
                "Booking Date","Value Date","Partner Name","Partner Iban",Type,"Payment Reference","Account Name","Amount (EUR)"
                01.01.2026,2026-01-01,Shop,,Presentment,,Hauptkonto,-5.00
                2026-01-02,2026-01-02,Shop,,Presentment,,Hauptkonto,minus fünf
                """;

        BankFile parsed = BankCsv.read(file.getBytes(StandardCharsets.UTF_8));

        assertThat(skipReason(parsed, 2)).isEqualTo("Datum unlesbar: 01.01.2026");
        assertThat(skipReason(parsed, 3)).isEqualTo("Betrag unlesbar: minus fünf");
    }

    @Test
    void anUnknownFileIsRejected() {
        assertThatThrownBy(() -> BankCsv.read("Datum;Wert\n01.01.2026;5\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BankCsv.UnknownFormatException.class);
        assertThatThrownBy(() -> BankCsv.read(new byte[0])).isInstanceOf(BankCsv.UnknownFormatException.class);
    }

    @Test
    void countsTheLinesWithoutHeaderAndEmptyLines() {
        BankFile file = BankCsv.read(N26.getBytes(StandardCharsets.UTF_8));

        assertThat(file.lines()).hasSize(3);
        assertThat(file.lines()).extracting(Line::number).containsExactly(2, 3, 4);
    }

    @Test
    void parsesGermanAndPlainNumbers() {
        assertThat(BankCsv.germanAmount("-1.234,56 €")).isEqualByComparingTo(new BigDecimal("-1234.56"));
        assertThat(BankCsv.germanAmount("12,3")).isEqualByComparingTo(new BigDecimal("12.30"));
        assertThat(BankCsv.germanAmount("+5")).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(BankCsv.plainAmount("-0.005")).isEqualByComparingTo(new BigDecimal("-0.01"));
        assertThat(BankCsv.plainAmount("1234.5")).isEqualByComparingTo(new BigDecimal("1234.50"));
        assertThat(BankCsv.germanAmount("abc")).isNull();
        assertThat(BankCsv.plainAmount("1,5")).isNull();
    }

    @Test
    void splitsRecordsWithQuotedLineBreaks() {
        List<List<String>> records = BankCsv.records("a;\"b\nc\";d\n\"e \"\"f\"\"\";;\n", ';');

        assertThat(records).containsExactly(List.of("a", "b\nc", "d"), List.of("e \"f\"", "", ""));
    }

    private static BankTransaction row(BankFile file, int number) {
        Line line = file.lines().stream().filter(l -> l.number() == number).findFirst().orElseThrow();
        assertThat(line.skipReason()).as("line %d", number).isNull();
        return line.row();
    }

    private static String skipReason(BankFile file, int number) {
        return file.lines().stream().filter(l -> l.number() == number).findFirst().orElseThrow().skipReason();
    }
}
