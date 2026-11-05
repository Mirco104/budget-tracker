package io.github.mirco104.budget.reserve;

import java.time.YearMonth;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Auswertung (R14, decision 29): look back over a period and outlook from the current month on. */
@RestController
public class ReserveController {

    private final ReserveService reserveService;

    public ReserveController(ReserveService reserveService) {
        this.reserveService = reserveService;
    }

    /** Without parameters the 12 months before the current month; otherwise both months are needed. */
    @GetMapping("/api/history")
    public HistoryResponse history(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth to)
            throws MissingServletRequestParameterException {
        if ((from == null) != (to == null)) {
            throw new MissingServletRequestParameterException(from == null ? "from" : "to", "YearMonth");
        }
        return reserveService.history(from, to);
    }

    /** The month stays a parameter so tests can use a fixed month; the app asks for the current month. */
    @GetMapping("/api/reserves")
    public ReserveResponse outlook(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return reserveService.outlook(month);
    }
}
