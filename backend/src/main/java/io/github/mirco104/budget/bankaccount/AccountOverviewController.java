package io.github.mirco104.budget.bankaccount;

import io.github.mirco104.budget.bankaccount.AccountOverviewService.ReconcileResult;
import io.github.mirco104.budget.bankaccount.AccountOverviewService.RoleGroup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The page Konten (section 19.4): accounts by role with their app balance and the reconciliation (P11). */
@RestController
public class AccountOverviewController {

    private final AccountOverviewService overviewService;

    public AccountOverviewController(AccountOverviewService overviewService) {
        this.overviewService = overviewService;
    }

    public record BalanceRequest(
            @NotNull(message = "Der Stichtag ist Pflicht.") LocalDate date,
            @NotNull(message = "Der Kontostand ist Pflicht.")
            @Digits(integer = 10, fraction = 2, message = "Der Betrag darf höchstens 2 Nachkommastellen haben.")
            BigDecimal amount) {
    }

    @GetMapping("/api/accounts")
    public List<RoleGroup> accounts() {
        return overviewService.groups();
    }

    @PostMapping("/api/bank-accounts/{id}/balances")
    public ReconcileResult reconcile(@PathVariable long id, @Valid @RequestBody BalanceRequest request) {
        return overviewService.reconcile(id, request.date(), request.amount());
    }
}
