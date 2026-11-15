package io.github.mirco104.budget.bankaccount;

import io.github.mirco104.budget.bankaccount.AccountOverviewService.BalanceLine;
import io.github.mirco104.budget.bankaccount.AccountOverviewService.ReconcileResult;
import io.github.mirco104.budget.bankaccount.AccountOverviewService.RoleGroup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    /** P12: the lower limit of an account; amount only for CUSTOM. */
    public record LowerLimitRequest(
            @NotNull(message = "Die Untergrenze ist Pflicht.") LowerLimitMode mode,
            @jakarta.validation.constraints.PositiveOrZero(message = "Der Betrag darf nicht negativ sein.")
            @Digits(integer = 10, fraction = 2, message = "Der Betrag darf höchstens 2 Nachkommastellen haben.")
            BigDecimal amount) {
    }

    @org.springframework.web.bind.annotation.PutMapping("/api/bank-accounts/{id}/lower-limit")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void lowerLimit(@PathVariable long id, @Valid @RequestBody LowerLimitRequest request) {
        overviewService.setLowerLimit(id, request.mode(), request.amount());
    }

    @GetMapping("/api/accounts")
    public List<RoleGroup> accounts() {
        return overviewService.groups();
    }

    @PostMapping("/api/bank-accounts/{id}/balances")
    public ReconcileResult reconcile(@PathVariable long id, @Valid @RequestBody BalanceRequest request) {
        return overviewService.reconcile(id, request.date(), request.amount());
    }

    /** Ticket #135: the history of the balances from the bank, newest first. */
    @GetMapping("/api/bank-accounts/{id}/balances")
    public List<BalanceLine> balances(@PathVariable long id) {
        return overviewService.history(id);
    }

    /** Ticket #135: deletes a wrong balance from the bank. */
    @DeleteMapping("/api/bank-accounts/{id}/balances/{balanceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBalance(@PathVariable long id, @PathVariable long balanceId) {
        overviewService.deleteBalance(id, balanceId);
    }
}
