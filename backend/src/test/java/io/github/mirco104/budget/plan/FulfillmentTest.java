package io.github.mirco104.budget.plan;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mirco104.budget.plan.Fulfillment.Booking;
import io.github.mirco104.budget.plan.Fulfillment.Result;
import io.github.mirco104.budget.plan.Fulfillment.Status;
import io.github.mirco104.budget.plan.Fulfillment.Term;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Rule P5: status of the terms and which bookings fulfil them (fix). */
class FulfillmentTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-13");
    private static final long WOHNEN = 1;
    private static final long FREIZEIT = 2;

    private static Term term(long item, String date, String amount, String name, Long category) {
        return new Term(item, new UUID(0, item), LocalDate.parse(date), LocalDate.parse(date), new BigDecimal(amount),
                name, category, 5, null, false);
    }

    private static Booking booking(long id, String date, String amount, String text, Long category) {
        return new Booking(id, LocalDate.parse(date), new BigDecimal(amount), text, category);
    }

    @Test
    void aLinkedBookingFulfilsItsTermAndIsFix() {
        Term miete = new Term(1, new UUID(0, 1), LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-01"),
                new BigDecimal("-900"), "Miete", WOHNEN, 5, 10L, false);

        Result result = Fulfillment.of(List.of(miete), List.of(booking(10, "2026-11-03", "-950", "Überweisung", WOHNEN)), TODAY);

        assertThat(result.status(miete)).isEqualTo(Status.FULFILLED);
        assertThat(result.fulfilledBy(miete)).isEqualTo(10L);
        assertThat(result.fixIds()).containsExactly(10L);
    }

    @Test
    void anUnlinkedBookingThatMatchesFulfilsTheTerm() {
        Term strom = term(2, "2026-11-15", "-80", "Strom Stadtwerke", WOHNEN);

        Result result = Fulfillment.of(List.of(strom), List.of(booking(11, "2026-11-12", "-84", "STADTWERKE", FREIZEIT)), TODAY);

        assertThat(result.status(strom)).isEqualTo(Status.FULFILLED);
        assertThat(result.fulfilledBy(strom)).isEqualTo(11L);
        assertThat(result.fixIds()).containsExactly(11L);
    }

    @Test
    void expectedUntilFiveDaysAfterTheDueDateThenMissed() {
        Term abo = term(3, "2026-11-10", "-9.99", "Streaming Abo", FREIZEIT);
        Term frisch = term(4, "2026-11-08", "-9.99", "Musikdienst", FREIZEIT);
        Term spaeter = term(5, "2026-11-20", "-50", "Verein", FREIZEIT);

        Result result = Fulfillment.of(List.of(abo, frisch, spaeter), List.of(), TODAY);

        // 10 + 5 = 15 ≥ 13: still expected; 8 + 5 = 13 is not before today: expected; later ones too
        assertThat(result.status(abo)).isEqualTo(Status.EXPECTED);
        assertThat(result.status(frisch)).isEqualTo(Status.EXPECTED);
        assertThat(result.status(spaeter)).isEqualTo(Status.EXPECTED);
        assertThat(Fulfillment.of(List.of(frisch), List.of(), TODAY.plusDays(1)).status(frisch)).isEqualTo(Status.MISSED);
    }

    @Test
    void aSkippedTermIsSkippedAndTakesNoBooking() {
        Term skipped = new Term(6, new UUID(0, 6), LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-01"),
                new BigDecimal("-900"), "Miete", WOHNEN, 5, null, true);

        Result result = Fulfillment.of(List.of(skipped), List.of(booking(12, "2026-11-01", "-900", "Miete", WOHNEN)), TODAY);

        assertThat(result.status(skipped)).isEqualTo(Status.SKIPPED);
        assertThat(result.fixIds()).isEmpty();
    }

    @Test
    void incomeFulfilsButIsNeverFixAndALinkedBookingIsNotUsedTwice() {
        Term gehalt = term(7, "2026-11-01", "2500", "Gehalt Firma", 9L);
        Term miete = new Term(1, new UUID(0, 1), LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-01"),
                new BigDecimal("-900"), "Miete", WOHNEN, 5, 13L, false);
        Term zweiteMiete = term(8, "2026-11-01", "-900", "Miete Garage", WOHNEN);

        Result result = Fulfillment.of(List.of(gehalt, miete, zweiteMiete), List.of(
                booking(13, "2026-11-01", "-900", "Miete", WOHNEN),
                booking(14, "2026-11-02", "2500", "FIRMA GEHALT", 9L)), TODAY);

        assertThat(result.status(gehalt)).isEqualTo(Status.FULFILLED);
        // The linked booking does not count again; 1 November plus 5 days is before today
        assertThat(result.status(zweiteMiete)).isEqualTo(Status.MISSED);
        assertThat(result.fixIds()).containsExactly(13L);
    }
}
