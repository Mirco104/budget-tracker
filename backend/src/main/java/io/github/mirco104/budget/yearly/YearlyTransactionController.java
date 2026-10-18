package io.github.mirco104.budget.yearly;

import jakarta.validation.Valid;
import java.net.URI;
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
@RequestMapping("/api/yearly-transactions")
public class YearlyTransactionController {

    private final YearlyTransactionService yearlyTransactionService;

    public YearlyTransactionController(YearlyTransactionService yearlyTransactionService) {
        this.yearlyTransactionService = yearlyTransactionService;
    }

    @GetMapping
    public List<YearlyTransactionResponse> findValidIn(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return yearlyTransactionService.findValidIn(month);
    }

    @PostMapping
    public ResponseEntity<YearlyTransactionResponse> create(@Valid @RequestBody YearlyTransactionRequest request) {
        YearlyTransactionResponse created = yearlyTransactionService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public YearlyTransactionResponse update(
            @PathVariable long id, @Valid @RequestBody YearlyTransactionRequest request) {
        return yearlyTransactionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        yearlyTransactionService.delete(id);
    }
}
