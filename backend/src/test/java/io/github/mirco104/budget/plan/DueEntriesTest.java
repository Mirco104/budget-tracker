package io.github.mirco104.budget.plan;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** P6: a parallel request that entered the same terms first does not make the page fail. */
class DueEntriesTest {

    @Test
    void entersTheDueTerms() {
        TermService termService = mock(TermService.class);

        new DueEntries(termService).enter();

        verify(termService).enterDue();
    }

    @Test
    void goesOnWhenAParallelRequestEnteredTheSameTerm() {
        TermService termService = mock(TermService.class);
        when(termService.enterDue()).thenThrow(new DataIntegrityViolationException("uk_item_terms_key_date"));

        assertThatCode(() -> new DueEntries(termService).enter()).doesNotThrowAnyException();
    }

    @Test
    void otherErrorsStillFail() {
        TermService termService = mock(TermService.class);
        when(termService.enterDue()).thenThrow(new IllegalStateException("something else"));

        assertThatThrownBy(() -> new DueEntries(termService).enter()).isInstanceOf(IllegalStateException.class);
    }
}
