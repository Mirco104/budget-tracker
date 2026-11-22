package io.github.mirco104.budget.plan;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 27i: suggested items from the bookings; "Ausblenden" uses POST /api/notifications/done with the key. */
@RestController
public class PlanSuggestionController {

    private final PlanSuggestionService service;

    public PlanSuggestionController(PlanSuggestionService service) {
        this.service = service;
    }

    @GetMapping("/api/plan/suggestions")
    public List<PlanSuggestionService.PlanSuggestion> suggestions() {
        return service.suggestions();
    }

    /** R38: budget suggestions of the setup wizard. */
    @GetMapping("/api/plan/budget-suggestions")
    public List<BudgetSuggester.Suggestion> budgetSuggestions() {
        return service.budgetSuggestions();
    }
}
