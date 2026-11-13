package io.github.mirco104.budget.plan;

import io.github.mirco104.budget.dashboard.DashboardService;
import jakarta.validation.Valid;
import java.time.YearMonth;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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

/** Planning (section 19.4): the plan of a month and its planned items. */
@RestController
@RequestMapping("/api")
public class PlanController {

    private final PlanService planService;
    private final DashboardService dashboardService;

    public PlanController(PlanService planService, DashboardService dashboardService) {
        this.planService = planService;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/plan")
    public PlanResponse plan(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return planService.plan(month, dashboardService.incomeAverage(month));
    }

    @PostMapping("/planned-items")
    @ResponseStatus(HttpStatus.CREATED)
    public PlannedItemResponse create(@Valid @RequestBody PlannedItemRequest request) {
        return planService.create(request);
    }

    @PutMapping("/planned-items/{id}")
    public PlannedItemResponse update(@PathVariable long id, @Valid @RequestBody PlannedItemRequest request) {
        return planService.update(id, request);
    }

    @DeleteMapping("/planned-items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable long id,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth from) {
        planService.delete(id, from);
    }
}
