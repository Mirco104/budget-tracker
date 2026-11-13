package io.github.mirco104.budget.dashboard;

import io.github.mirco104.budget.plan.TermService;
import java.time.YearMonth;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final TermService termService;

    public DashboardController(DashboardService dashboardService, TermService termService) {
        this.termService = termService;
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardResponse get(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        // P6: due automatic entries are made before the month is calculated
        termService.enterDue();
        return dashboardService.calculate(month);
    }
}
