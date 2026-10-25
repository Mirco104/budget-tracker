package io.github.mirco104.budget.recurring;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.mirco104.budget.category.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecurringTransactionControllerTest {

    private static final String BASE = "/api/recurring-transactions";
    private static final String OCCURRENCE = BASE + "/{id}/occurrences/{date}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    // --- POST /api/recurring-transactions (US6): success ---

    @Test
    void createsSeriesWithLocationAndScaledAmount() throws Exception {
        long categoryId = categoryId("Versicherungen");

        postSeries(series("-240", "Haftpflicht", categoryId, "YEARLY", "2026-10-15", null))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/recurring-transactions/\\d+$")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.seriesId").isString())
                .andExpect(jsonPath("$.amount").value(-240.00))
                .andExpect(jsonPath("$.description").value("Haftpflicht"))
                .andExpect(jsonPath("$.categoryId").value(categoryId))
                .andExpect(jsonPath("$.categoryName").value("Versicherungen"))
                .andExpect(jsonPath("$.interval").value("YEARLY"))
                .andExpect(jsonPath("$.startDate").value("2026-10-15"))
                .andExpect(jsonPath("$.endDate").doesNotExist())
                .andExpect(jsonPath("$.monthlyEquivalent").value(20.00));
    }

    @Test
    void acceptsEndDateEqualToStartDate() throws Exception {
        postSeries(series("-10", "Einmal", categoryId("Freizeit"), "MONTHLY", "2026-10-05", "2026-10-05"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endDate").value("2026-10-05"));
    }

    // --- POST /api/recurring-transactions (US6): validation ---

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "1.234"})
    void rejectsInvalidAmount(String amount) throws Exception {
        postSeries(series(amount, "Test", categoryId("Freizeit"), "MONTHLY", "2026-10-05", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
    }

    @Test
    void rejectsEndDateBeforeStartDate() throws Exception {
        postSeries(series("-10", "Test", categoryId("Freizeit"), "MONTHLY", "2026-10-05", "2026-10-04"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("endDate"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Das Enddatum darf nicht vor dem Startdatum liegen."));
    }

    @Test
    void rejectsRemainderCategory() throws Exception {
        postSeries(series("-10", "Test", remainderId(), "MONTHLY", "2026-10-05", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Auf die Rest-Kategorie kann nicht gebucht werden."));
    }

    @Test
    void rejectsUnknownCategory() throws Exception {
        postSeries(series("-10", "Test", 999_999, "MONTHLY", "2026-10-05", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"));
    }

    @Test
    void rejectsMissingStartDateAndInterval() throws Exception {
        postSeries("{\"amount\": -10, \"description\": \"Test\", \"categoryId\": " + categoryId("Freizeit") + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)));
    }

    @Test
    void rejectsUnknownInterval() throws Exception {
        postSeries(series("-10", "Test", categoryId("Freizeit"), "WEEKLY", "2026-10-05", null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDescriptionLongerThan100Characters() throws Exception {
        postSeries(series("-10", "x".repeat(101), categoryId("Freizeit"), "MONTHLY", "2026-10-05", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
    }

    // --- GET /api/recurring-transactions?month= ---

    @Test
    void listsSeriesActiveInTheMonth() throws Exception {
        long categoryId = categoryId("Freizeit");
        create(series("-15", "Läuft", categoryId, "MONTHLY", "2026-01-05", null));
        create(series("-300", "Quartal", categoryId, "QUARTERLY", "2026-11-01", null));
        create(series("-5", "Endet im Oktober", categoryId, "MONTHLY", "2026-01-05", "2026-10-01"));
        create(series("-5", "Endete im September", categoryId, "MONTHLY", "2026-01-05", "2026-09-30"));
        create(series("-5", "Beginnt im November", categoryId, "MONTHLY", "2026-11-01", null));

        mockMvc.perform(get(BASE).param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].description", contains("Läuft", "Endet im Oktober")))
                .andExpect(jsonPath("$[0].monthlyEquivalent").value(15.00));
        mockMvc.perform(get(BASE).param("month", "2026-11"))
                .andExpect(jsonPath("$[?(@.description == 'Quartal')].monthlyEquivalent", contains(100.00)));
    }

    @Test
    void rejectsMissingMonth() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Der Parameter 'month' fehlt."));
    }

    // --- occurrences in GET /api/transactions (US6) ---

    @Test
    void occurrencesAppearInTheTransactionListOfTheirMonth() throws Exception {
        long id = create(series("-240", "Haftpflicht", categoryId("Versicherungen"), "YEARLY", "2026-10-15",
                null));

        mockMvc.perform(get("/api/transactions").param("month", "2026-10"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].kind").value("RECURRING"))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].recurringId").value(id))
                .andExpect(jsonPath("$[0].occurrenceDate").value("2026-10-15"))
                .andExpect(jsonPath("$[0].date").value("2026-10-15"))
                .andExpect(jsonPath("$[0].amount").value(-240.00))
                .andExpect(jsonPath("$[0].categoryName").value("Versicherungen"));
        assertDescriptions("2026-11");
        assertDescriptions("2027-10", "Haftpflicht");
    }

    // --- PUT .../occurrences/{date}?scope=THIS (R13) ---

    @Test
    void updateThisSuspendsTheOccurrenceAndBooksAReplacement() throws Exception {
        long categoryId = categoryId("Freizeit");
        long id = create(series("-15", "Streaming", categoryId, "MONTHLY", "2026-01-05", null));

        putOccurrence(id, "2026-10-05", "THIS", transaction("-20", "Streaming teurer", "2026-10-07", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("SINGLE"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.amount").value(-20.00));

        mockMvc.perform(get("/api/transactions").param("month", "2026-10"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].kind").value("SINGLE"))
                .andExpect(jsonPath("$[0].description").value("Streaming teurer"));
        assertDescriptions("2026-09", "Streaming");
        assertDescriptions("2026-11", "Streaming");
    }

    @Test
    void updateThisWithInvalidBodyChangesNothing() throws Exception {
        long id = create(series("-15", "Streaming", categoryId("Freizeit"), "MONTHLY", "2026-01-05", null));

        putOccurrence(id, "2026-10-05", "THIS", transaction("-20", "Test", "2026-10-05", remainderId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"));
        assertDescriptions("2026-10", "Streaming");
    }

    @Test
    void suspendedOccurrenceCannotBeChangedAgain() throws Exception {
        long categoryId = categoryId("Freizeit");
        long id = create(series("-15", "Streaming", categoryId, "MONTHLY", "2026-01-05", null));
        deleteOccurrence(id, "2026-10-05", "THIS").andExpect(status().isNoContent());

        putOccurrence(id, "2026-10-05", "THIS", transaction("-20", "Test", "2026-10-05", categoryId))
                .andExpect(status().isNotFound());
    }

    // --- PUT .../occurrences/{date}?scope=FOLLOWING (R13) ---

    @Test
    void updateFollowingOnFirstOccurrenceChangesTheSeriesItself() throws Exception {
        long categoryId = categoryId("Freizeit");
        long id = create(series("-15", "Streaming", categoryId, "MONTHLY", "2026-01-05", null));
        deleteOccurrence(id, "2026-03-05", "THIS").andExpect(status().isNoContent());

        putOccurrence(id, "2026-01-05", "FOLLOWING",
                series("-18", "Streaming neu", categoryId, "MONTHLY", "2026-01-10", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.amount").value(-18.00))
                .andExpect(jsonPath("$.startDate").value("2026-01-10"));

        assertDescriptions("2026-01", "Streaming neu");
        // the exception of the old schedule no longer applies
        assertDescriptions("2026-03", "Streaming neu");
        mockMvc.perform(get(BASE).param("month", "2026-10"))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void updateFollowingOnLaterOccurrenceSplitsTheSeries() throws Exception {
        long categoryId = categoryId("Wohnen");
        long id = create(series("-900", "Miete", categoryId, "MONTHLY", "2026-01-01", null));
        deleteOccurrence(id, "2026-12-01", "THIS").andExpect(status().isNoContent());

        String json = putOccurrence(id, "2026-10-01", "FOLLOWING",
                series("-950", "Miete neu", categoryId, "MONTHLY", "2026-10-01", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(-950.00))
                .andReturn().getResponse().getContentAsString();
        long newId = ((Number) JsonPath.read(json, "$.id")).longValue();
        String newSeriesId = JsonPath.read(json, "$.seriesId");

        assertDescriptions("2026-09", "Miete");
        assertDescriptions("2026-10", "Miete neu");
        // exceptions from the split on are dropped, so December counts again
        assertDescriptions("2026-12", "Miete neu");
        mockMvc.perform(get(BASE).param("month", "2026-09"))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].endDate").value("2026-09-30"))
                .andExpect(jsonPath("$[0].seriesId").value(newSeriesId));
        mockMvc.perform(get(BASE).param("month", "2026-10"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(newId));
    }

    @Test
    void splitAcceptsAStartDateLaterThanTheOccurrence() throws Exception {
        long categoryId = categoryId("Wohnen");
        long id = create(series("-900", "Miete", categoryId, "MONTHLY", "2026-01-01", null));

        putOccurrence(id, "2026-10-01", "FOLLOWING",
                series("-900", "Miete", categoryId, "MONTHLY", "2026-09-02", null))
                .andExpect(status().isOk());
    }

    @Test
    void splitRejectsStartDateNotAfterThePreviousOccurrence() throws Exception {
        long categoryId = categoryId("Wohnen");
        long id = create(series("-900", "Miete", categoryId, "MONTHLY", "2026-01-01", null));

        putOccurrence(id, "2026-10-01", "FOLLOWING",
                series("-950", "Miete neu", categoryId, "MONTHLY", "2026-09-01", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("startDate"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Das Startdatum muss nach dem vorherigen Vorkommen (01.09.2026) liegen."));
        assertDescriptions("2026-10", "Miete");
    }

    @Test
    void updateFollowingOntoRemainderCategoryReturns400() throws Exception {
        long id = create(series("-900", "Miete", categoryId("Wohnen"), "MONTHLY", "2026-01-01", null));

        putOccurrence(id, "2026-10-01", "FOLLOWING",
                series("-900", "Miete", remainderId(), "MONTHLY", "2026-10-01", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"));
    }

    // --- PUT: unknown occurrence, scope ---

    @Test
    void unknownSeriesReturns404() throws Exception {
        putOccurrence(999_999, "2026-10-05", "THIS",
                transaction("-20", "Test", "2026-10-05", categoryId("Freizeit")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Nicht gefunden"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-06", "2025-12-05", "2027-01-05"})
    void dateThatIsNoOccurrenceReturns404(String date) throws Exception {
        long categoryId = categoryId("Freizeit");
        long id = create(series("-15", "Streaming", categoryId, "MONTHLY", "2026-01-05", "2026-12-31"));

        putOccurrence(id, date, "THIS", transaction("-20", "Test", date, categoryId))
                .andExpect(status().isNotFound());
        deleteOccurrence(id, date, "FOLLOWING")
                .andExpect(status().isNotFound());
    }

    @Test
    void missingOrInvalidScopeReturns400() throws Exception {
        long categoryId = categoryId("Freizeit");
        long id = create(series("-15", "Streaming", categoryId, "MONTHLY", "2026-01-05", null));
        String body = transaction("-20", "Test", "2026-10-05", categoryId);

        mockMvc.perform(put(OCCURRENCE, id, "2026-10-05").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ungültige Anfrage"));
        putOccurrence(id, "2026-10-05", "ALL", body)
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete(OCCURRENCE, id, "2026-10-05"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Der Parameter 'scope' fehlt."));
        deleteOccurrence(id, "2026-10-05", "ALL")
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidDateInPathReturns400() throws Exception {
        deleteOccurrence(1, "2026-13-01", "THIS")
                .andExpect(status().isBadRequest());
    }

    // --- DELETE .../occurrences/{date} (R13) ---

    @Test
    void deleteThisSuspendsOnlyThatOccurrence() throws Exception {
        long id = create(series("-15", "Streaming", categoryId("Freizeit"), "MONTHLY", "2026-01-05", null));

        deleteOccurrence(id, "2026-10-05", "THIS").andExpect(status().isNoContent());

        assertDescriptions("2026-09", "Streaming");
        assertDescriptions("2026-10");
        assertDescriptions("2026-11", "Streaming");
    }

    @Test
    void deleteFollowingOnLaterOccurrenceEndsTheSeriesTheDayBefore() throws Exception {
        long id = create(series("-15", "Streaming", categoryId("Freizeit"), "MONTHLY", "2026-01-05", null));

        deleteOccurrence(id, "2026-10-05", "FOLLOWING").andExpect(status().isNoContent());

        assertDescriptions("2026-09", "Streaming");
        assertDescriptions("2026-10");
        assertDescriptions("2027-01");
        mockMvc.perform(get(BASE).param("month", "2026-09"))
                .andExpect(jsonPath("$[0].endDate").value("2026-10-04"));
    }

    @Test
    void deleteFollowingOnFirstOccurrenceDeletesTheSeriesWithItsExceptions() throws Exception {
        long id = create(series("-15", "Streaming", categoryId("Freizeit"), "MONTHLY", "2026-01-05", null));
        deleteOccurrence(id, "2026-03-05", "THIS").andExpect(status().isNoContent());

        deleteOccurrence(id, "2026-01-05", "FOLLOWING").andExpect(status().isNoContent());

        assertDescriptions("2026-01");
        mockMvc.perform(get(BASE).param("month", "2026-01"))
                .andExpect(jsonPath("$", hasSize(0)));
        deleteOccurrence(id, "2026-02-05", "THIS").andExpect(status().isNotFound());
    }

    // --- helpers ---

    private static String series(
            String amount, String description, long categoryId, String interval, String startDate,
            String endDate) {
        return """
                {"amount": %s, "description": "%s", "categoryId": %d, "interval": "%s", "startDate": "%s",
                 "endDate": %s}
                """.formatted(amount, description, categoryId, interval, startDate,
                endDate == null ? "null" : "\"" + endDate + "\"");
    }

    private static String transaction(String amount, String description, String date, long categoryId) {
        return """
                {"amount": %s, "description": "%s", "date": "%s", "categoryId": %d}
                """.formatted(amount, description, date, categoryId);
    }

    private long create(String body) throws Exception {
        String json = postSeries(body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private ResultActions postSeries(String body) throws Exception {
        return mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions putOccurrence(long id, String date, String scope, String body) throws Exception {
        return mockMvc.perform(put(OCCURRENCE, id, date)
                .param("scope", scope)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions deleteOccurrence(long id, String date, String scope) throws Exception {
        return mockMvc.perform(delete(OCCURRENCE, id, date).param("scope", scope));
    }

    private void assertDescriptions(String month, String... descriptions) throws Exception {
        ResultActions result = mockMvc.perform(get("/api/transactions").param("month", month))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(descriptions.length)));
        if (descriptions.length > 0) {
            result.andExpect(jsonPath("$[*].description", contains(descriptions)));
        }
    }

    private long categoryId(String name) {
        return categoryRepository.findAll().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst().orElseThrow()
                .getId();
    }

    private long remainderId() {
        return categoryRepository.findAll().stream()
                .filter(c -> c.isSystem())
                .findFirst().orElseThrow()
                .getId();
    }
}
