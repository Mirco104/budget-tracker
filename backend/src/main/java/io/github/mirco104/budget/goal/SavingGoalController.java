package io.github.mirco104.budget.goal;

import io.github.mirco104.budget.goal.SavingGoalService.GoalRequest;
import io.github.mirco104.budget.goal.SavingGoalService.GoalResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Saving goals (section 19.4). */
@RestController
@RequestMapping("/api/saving-goals")
public class SavingGoalController {

    private final SavingGoalService goalService;

    public SavingGoalController(SavingGoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping
    public List<GoalResponse> findAll() {
        return goalService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@Valid @RequestBody GoalRequest request) {
        return goalService.create(request);
    }

    @PutMapping("/{id}")
    public GoalResponse update(@PathVariable long id, @Valid @RequestBody GoalRequest request) {
        return goalService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        goalService.delete(id);
    }
}
