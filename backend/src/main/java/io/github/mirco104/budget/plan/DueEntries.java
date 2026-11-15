package io.github.mirco104.budget.plan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * P6: makes the due automatic entries before a page reads bookings. The pages Transaktionen and Erwartet load bookings
 * and terms at the same time, so two requests can enter the same term at once; the unique term (item_key, term_date)
 * then rejects the second one and rolls its transaction back. That request has nothing left to do, so it goes on
 * instead of failing with 500 (found in the night run of 14.11.2026).
 */
@Component
public class DueEntries {

    private static final Logger LOG = LoggerFactory.getLogger(DueEntries.class);

    private final TermService termService;

    public DueEntries(TermService termService) {
        this.termService = termService;
    }

    public void enter() {
        try {
            termService.enterDue();
        } catch (DataIntegrityViolationException e) {
            LOG.info("Due automatic entries were entered by a parallel request: {}", e.getMostSpecificCause().getMessage());
        }
    }
}
