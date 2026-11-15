package io.github.mirco104.budget.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 27h: the notifications of the bell, "Erledigt" and "Übernehmen" of a price change. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    public record DismissRequest(String key) {
    }

    public record PriceChangeRequest(
            @NotNull(message = "Der Posten ist Pflicht.") Long itemId,
            @NotNull(message = "Der Betrag ist Pflicht.")
            @Digits(integer = 10, fraction = 2, message = "Der Betrag darf höchstens 2 Nachkommastellen haben.")
            BigDecimal amount) {
    }

    @GetMapping
    public List<NotificationService.Notification> notifications() {
        return service.notifications();
    }

    @PostMapping("/done")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void done(@RequestBody DismissRequest request) {
        service.dismiss(request.key());
    }

    @PostMapping("/price-change")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acceptPriceChange(@Valid @RequestBody PriceChangeRequest request) {
        service.acceptPriceChange(request.itemId(), request.amount());
    }
}
