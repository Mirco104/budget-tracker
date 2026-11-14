package io.github.mirco104.budget.importer;

import io.github.mirco104.budget.importer.BankCategoryMappingRequest.Stored;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** R33, US38: the mappings of bank categories the card "Kategorien der Bank" confirms. */
@RestController
public class BankCategoryMappingController {

    private final BankCategoryMappingService service;

    public BankCategoryMappingController(BankCategoryMappingService service) {
        this.service = service;
    }

    @PutMapping("/api/bank-category-mappings")
    public List<Stored> save(@Valid @RequestBody BankCategoryMappingRequest request) {
        return service.save(request);
    }
}
