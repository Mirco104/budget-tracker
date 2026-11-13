package io.github.mirco104.budget.transaction;

import io.github.mirco104.budget.plan.TermService;
import jakarta.validation.Valid;
import java.net.URI;
import io.github.mirco104.budget.common.BadRequestException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final TermService termService;

    public TransactionController(TransactionService transactionService, TermService termService) {
        this.termService = termService;
        this.transactionService = transactionService;
    }

    /** 27f4: the first and the last day of the user's bookings, for the quick choice "Gesamt" (US26). */
    @GetMapping("/range")
    public TransactionService.Range range() {
        return transactionService.range();
    }

    /**
     * Entries of a month ({@code month}) or of a period ({@code from}, {@code to}, both days included, US26).
     * The month is the short form of a period; one of the two is required, not both.
     */
    @GetMapping
    public List<TransactionResponse> find(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to)
            throws MissingServletRequestParameterException {
        // P6: due automatic entries are made before the list is read
        termService.enterDue();
        if (month != null) {
            if (from != null || to != null) {
                throw new BadRequestException("Entweder 'month' oder 'from' und 'to' angeben.");
            }
            return transactionService.findBetween(month.atDay(1), month.atEndOfMonth());
        }
        if (from == null && to == null) {
            throw new MissingServletRequestParameterException("month", "YearMonth");
        }
        if (from == null || to == null) {
            throw new MissingServletRequestParameterException(from == null ? "from" : "to", "LocalDate");
        }
        return transactionService.findBetween(from, to);
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        TransactionResponse created = transactionService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public TransactionResponse update(@PathVariable long id, @Valid @RequestBody TransactionRequest request) {
        return transactionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        transactionService.delete(id);
    }
}
