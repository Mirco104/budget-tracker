package io.github.mirco104.budget.closing;

import java.time.YearMonth;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** P10: the view of a past month and its closing (27h). */
@RestController
@RequestMapping("/api/month-closings")
public class MonthClosingController {

    private final MonthClosingService service;

    public MonthClosingController(MonthClosingService service) {
        this.service = service;
    }

    /** The goal of the leftover; null closes the month without a goal ("Nicht zuordnen"). */
    public record CloseRequest(Long savingGoalId) {
    }

    @GetMapping("/{month}")
    public MonthClosingService.MonthView view(@PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return service.view(month);
    }

    @PostMapping("/{month}")
    public MonthClosingService.MonthView close(
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestBody(required = false) CloseRequest request) {
        return service.close(month, request == null ? null : request.savingGoalId());
    }

    @DeleteMapping("/{month}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void undo(@PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        service.undo(month);
    }
}
