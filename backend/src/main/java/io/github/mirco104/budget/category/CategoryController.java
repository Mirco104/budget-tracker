package io.github.mirco104.budget.category;

import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryResponse> findAll(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return categoryService.findAll(month);
    }

    @PutMapping("/{id}/target")
    public CategoryResponse updateTarget(
            @PathVariable long id,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @Valid @RequestBody UpdateTargetRequest request) {
        return categoryService.updateTarget(id, month, request);
    }
}
