package io.github.mirco104.budget.recurring;

import io.github.mirco104.budget.transaction.TransactionRequest;
import io.github.mirco104.budget.transaction.TransactionResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/recurring-transactions")
public class RecurringTransactionController {

    private static final String OCCURRENCE_PATH = "/{id}/occurrences/{date}";

    private final RecurringTransactionService recurringService;

    public RecurringTransactionController(RecurringTransactionService recurringService) {
        this.recurringService = recurringService;
    }

    @GetMapping
    public List<RecurringTransactionResponse> findActive(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return recurringService.findActive(month);
    }

    @PostMapping
    public ResponseEntity<RecurringTransactionResponse> create(
            @Valid @RequestBody RecurringTransactionRequest request) {
        RecurringTransactionResponse created = recurringService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    // The body depends on the scope, so each scope has its own mapping (R13)
    @PutMapping(path = OCCURRENCE_PATH, params = "scope=THIS")
    public TransactionResponse updateThis(
            @PathVariable long id, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody TransactionRequest request) {
        return recurringService.updateThis(id, date, request);
    }

    @PutMapping(path = OCCURRENCE_PATH, params = "scope=FOLLOWING")
    public RecurringTransactionResponse updateFollowing(
            @PathVariable long id, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody RecurringTransactionRequest request) {
        return recurringService.updateFollowing(id, date, request);
    }

    @DeleteMapping(OCCURRENCE_PATH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable long id, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam OccurrenceScope scope) {
        recurringService.delete(id, date, scope);
    }
}
