package io.github.mirco104.budget.plan;

import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.transaction.TransactionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Terms of the planned items (section 19.4): list with status (P5), tick-off and skip (P7). */
@RestController
@RequestMapping("/api/terms")
public class TermController {

    private static final int MAX_PERIOD_MONTHS = 24;

    private final TermService termService;
    private final DueEntries dueEntries;

    public TermController(TermService termService, DueEntries dueEntries) {
        this.termService = termService;
        this.dueEntries = dueEntries;
    }

    /** P7: the term and, optionally, another amount (without sign) and booking date. */
    public record PayRequest(
            @NotNull(message = "Der Posten ist Pflicht.") Long itemId,
            @NotNull(message = "Das Datum des Termins ist Pflicht.") LocalDate date,
            @Digits(integer = 10, fraction = 2, message = "Der Betrag darf höchstens 2 Nachkommastellen haben.")
            BigDecimal amount,
            LocalDate bookingDate) {
    }

    /** Terms with their planned date from and to; automatic entries that are due are made first (P6). */
    @GetMapping
    public List<TermResponse> terms(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (to.isBefore(from)) {
            throw new InvalidFieldException("to", "„Bis“ darf nicht vor „Von“ liegen.");
        }
        if (YearMonth.from(to).isAfter(YearMonth.from(from).plusMonths(MAX_PERIOD_MONTHS))) {
            throw new InvalidFieldException("to", "Der Zeitraum darf höchstens 24 Monate lang sein.");
        }
        dueEntries.enter();
        return termService.terms(from, to).stream().map(TermResponse::from).toList();
    }

    @PostMapping("/paid")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse pay(@Valid @RequestBody PayRequest request) {
        return termService.pay(request.itemId(), request.date(), request.amount(), request.bookingDate());
    }

    @DeleteMapping("/paid")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unpay(
            @RequestParam long itemId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        termService.unpay(itemId, date);
    }

    @PostMapping("/skip")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void skip(@Valid @RequestBody TermRef term) {
        termService.skip(term.itemId(), term.date());
    }

    @DeleteMapping("/skip")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unskip(
            @RequestParam long itemId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        termService.unskip(itemId, date);
    }
}
