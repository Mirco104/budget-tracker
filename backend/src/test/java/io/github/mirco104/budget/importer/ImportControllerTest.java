package io.github.mirco104.budget.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.mirco104.budget.TestUsers;
import io.github.mirco104.budget.category.Category;
import io.github.mirco104.budget.category.CategoryRepository;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** R26 to R30, US33, US34: preview, store and undo an import, only with the user's own data (R15). */
@SpringBootTest
@Transactional
class ImportControllerTest {

    // ISO 13616 documentation example, not a real account of anyone
    private static final String GIRO_IBAN = "DE89370400440532013000";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryRepository categoryRepository;

    private MockMvc mockMvc;
    private MockMvc other;
    private User user;
    private User otherUser;

    @BeforeEach
    void signIn() {
        user = TestUsers.create(userService, "person-i@example.test");
        otherUser = TestUsers.create(userService, "person-j@example.test");
        mockMvc = TestUsers.mockMvcFor(context, user);
        other = TestUsers.mockMvcFor(context, otherUser);
    }

    // --- preview ---

    @Test
    void previewsAC24FileWithSourcesStatusesAndSuggestions() throws Exception {
        book(mockMvc, -10, "SPIELE SHOP", "2026-10-01", category(user, "Freizeit"));

        preview(mockMvc, BankCsvTest.C24)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile").value("C24"))
                .andExpect(jsonPath("$.profileLabel").value("C24"))
                .andExpect(jsonPath("$.rows").value(5))
                .andExpect(jsonPath("$.newRows").value(4))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.duplicates").value(0))
                .andExpect(jsonPath("$.sources", hasSize(2)))
                .andExpect(jsonPath("$.sources[0].key").value("C24:Ausgabenkonto"))
                .andExpect(jsonPath("$.sources[0].accountId").isEmpty())
                .andExpect(jsonPath("$.sources[0].suggestedName").value("C24 Ausgabenkonto"))
                .andExpect(jsonPath("$.entries[0].line").value(2))
                .andExpect(jsonPath("$.entries[0].status").value("NEW"))
                .andExpect(jsonPath("$.entries[0].description").value("SPIELE SHOP · SPIELESHOP.EXAMPLE/BILL"))
                .andExpect(jsonPath("$.entries[0].counterparty").value("SPIELE SHOP"))
                .andExpect(jsonPath("$.entries[0].suggestionKey").value("spiele shop"))
                .andExpect(jsonPath("$.entries[0].categoryId").value(category(user, "Freizeit")))
                .andExpect(jsonPath("$.entries[0].certain").value(true))
                .andExpect(jsonPath("$.entries[0].fingerprint").isString())
                // No history: the C24 category "Wohnen" names an own category
                .andExpect(jsonPath("$.entries[1].description").value("Person B · Miete November"))
                .andExpect(jsonPath("$.entries[1].categoryId").value(category(user, "Wohnen")))
                .andExpect(jsonPath("$.entries[1].certain").value(false))
                .andExpect(jsonPath("$.entries[3].status").value("SKIPPED"))
                .andExpect(jsonPath("$.entries[3].reason").value("vorgemerkt (noch nicht gebucht)"));
    }

    // Decision 46: the hints of the kind come from the profile Trade Republic only
    @Test
    void onlyTradeRepublicKindsPointToInvestitionen() throws Exception {
        long investitionen = category(user, "Investitionen");
        String tradeRepublic = previewJson(mockMvc, BankCsvTest.TRADE_REPUBLIC);
        Map<String, Object> plan = JsonPath.<List<Map<String, Object>>>read(tradeRepublic, "$.entries[?(@.line == 3)]").getFirst();
        assertThat(((Number) plan.get("categoryId")).longValue()).isEqualTo(investitionen);

        String c24 = """
                Transaktionstyp,Buchungsdatum,Betrag,Zahlungsempfänger,IBAN,Verwendungszweck,Beschreibung,Kontonummer,Kontoname,Kategorie,Unterkategorie
                Lastschrift,03.11.2026,"-150,00 €",Broker,,Sparplan,,1000000001,Ausgabenkonto,SAVINGS_PLAN,
                """;
        Map<String, Object> row = JsonPath.<List<Map<String, Object>>>read(previewJson(mockMvc, c24), "$.entries").getFirst();
        assertThat(row.get("categoryId")).isNull();
    }

    @Test
    void anUnknownFileIsAFieldError() throws Exception {
        preview(mockMvc, "Datum;Wert\n01.01.2026;5\n")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"));
    }

    @Test
    void aFileWithMoreThan5000LinesIsRejected() throws Exception {
        StringBuilder file = new StringBuilder("\"Booking Date\",\"Partner Name\",\"Account Name\",\"Amount (EUR)\"\n");
        for (int i = 0; i < 5001; i++) {
            file.append("2026-01-01,Shop,Hauptkonto,-1.00\n");
        }
        preview(mockMvc, file.toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Die Datei darf höchstens 5000 Zeilen haben."));
    }

    // --- store ---

    @Test
    void storesTheChosenRowsOnNewAccountsAndNeverTwice() throws Exception {
        String preview = previewJson(mockMvc, BankCsvTest.C24);
        long freizeit = category(user, "Freizeit");

        String result = commit(mockMvc, preview, entry -> freizeit, true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(4))
                .andExpect(jsonPath("$.skippedDuplicates").value(0))
                .andExpect(jsonPath("$.batch.profile").value("C24"))
                .andExpect(jsonPath("$.batch.firstDate").value("2026-11-01"))
                .andExpect(jsonPath("$.batch.lastDate").value("2026-11-04"))
                .andReturn().getResponse().getContentAsString();
        assertThat(result).contains("\"count\":4");

        mockMvc.perform(get("/api/bank-accounts"))
                .andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "Girokonto", "C24 Ausgabenkonto", "C24 Pocket Urlaub")));
        mockMvc.perform(get("/api/transactions").param("month", "2026-11"))
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[?(@.description == 'SPIELE SHOP · SPIELESHOP.EXAMPLE/BILL')].accountName")
                        .value("C24 Ausgabenkonto"));

        // The second preview finds every row imported and puts the sources on their accounts
        String again = previewJson(mockMvc, BankCsvTest.C24);
        assertThat(JsonPath.<Integer>read(again, "$.duplicates")).isEqualTo(4);
        assertThat(JsonPath.<Integer>read(again, "$.newRows")).isZero();
        assertThat(JsonPath.<Object>read(again, "$.sources[0].accountId")).isNotNull();
        // Sending the same rows again creates nothing
        commit(mockMvc, preview, entry -> freizeit, true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.skippedDuplicates").value(4))
                .andExpect(jsonPath("$.batch").isEmpty());
    }

    @Test
    void theCounterpartyTeachesTheSuggestion() throws Exception {
        String preview = previewJson(mockMvc, BankCsvTest.N26);
        long freizeit = category(user, "Freizeit");
        commit(mockMvc, preview, entry -> freizeit, true).andExpect(status().isCreated());

        mockMvc.perform(get("/api/booking-suggestion").param("text", "Kino \"Am Markt\"").param("income", "false"))
                .andExpect(jsonPath("$.categoryId").value(freizeit))
                .andExpect(jsonPath("$.certain").value(true));
    }

    @Test
    void rowsNeedACategoryOrATransferAndAMappedSource() throws Exception {
        String preview = previewJson(mockMvc, BankCsvTest.N26);

        commit(mockMvc, preview, entry -> null, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("rows[0].categoryId"));
        commit(mockMvc, preview, entry -> category(user, "Freizeit"), false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("rows[0].source"));
    }

    // --- R27 ---

    @Test
    void twoSourcesOfTheFileWithOppositeAmountsAreATransfer() throws Exception {
        String file = BankCsvTest.C24 + "Umbuchung,02.11.2026,,\"100,00 €\",,,,Von Ausgabenkonto,,1000000001,Ausgabenkonto,,,\n";
        // The C24 row 6 takes 100 from Pocket Urlaub; the added row puts 100 on Ausgabenkonto
        String preview = previewJson(mockMvc, file);

        List<Boolean> transfers = JsonPath.read(preview, "$.entries[?(@.status == 'NEW')].transfer");
        assertThat(transfers).containsExactly(false, false, false, true, true);
        List<Object> categories = JsonPath.read(preview, "$.entries[?(@.transfer == true)].categoryId");
        assertThat(categories).containsOnlyNulls();
        // R27: each side names the other line as its counterpart
        List<Integer> lines = JsonPath.read(preview, "$.entries[?(@.transfer == true)].line");
        List<Integer> peers = JsonPath.read(preview, "$.entries[?(@.transfer == true)].transferPeer.line");
        assertThat(peers).containsExactly(lines.get(1), lines.get(0));
    }

    @Test
    void aStoredCounterpartBecomesATransfer() throws Exception {
        long wohnen = category(user, "Wohnen");
        long stored = idOf(book(mockMvc, -500, "Aufs Depot", "2026-01-02", wohnen));

        String preview = previewJson(mockMvc, BankCsvTest.TRADE_REPUBLIC);
        Map<String, Object> incoming = JsonPath.<List<Map<String, Object>>>read(preview, "$.entries[?(@.line == 4)]").getFirst();
        assertThat(incoming.get("transfer")).isEqualTo(true);
        assertThat(((Number) incoming.get("transferPeerId")).longValue()).isEqualTo(stored);
        @SuppressWarnings("unchecked")
        Map<String, Object> peer = (Map<String, Object>) incoming.get("transferPeer");
        assertThat(peer).containsEntry("date", "2026-01-02").containsEntry("description", "Aufs Depot")
                .containsEntry("accountName", "Girokonto").containsEntry("line", null);

        commit(mockMvc, preview, entry -> category(user, "Investitionen"), true).andExpect(status().isCreated());
        mockMvc.perform(get("/api/transactions").param("month", "2026-01"))
                .andExpect(jsonPath("$[?(@.description == 'Aufs Depot')].transfer").value(true))
                .andExpect(jsonPath("$[?(@.description == 'Aufs Depot')].categoryId",
                        org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
    }

    // US34, code review 08.11.2026: undoing the import gives the stored counterpart its category back
    @Test
    void undoingTheImportGivesTheCounterpartItsCategoryBack() throws Exception {
        long wohnen = category(user, "Wohnen");
        long stored = idOf(book(mockMvc, -500, "Aufs Depot", "2026-01-02", wohnen));
        String result = commit(mockMvc, previewJson(mockMvc, BankCsvTest.TRADE_REPUBLIC),
                entry -> category(user, "Investitionen"), true)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long batch = ((Number) JsonPath.read(result, "$.batch.id")).longValue();

        mockMvc.perform(delete("/api/imports/{id}", batch)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/transactions").param("month", "2026-01"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(stored))
                .andExpect(jsonPath("$[0].transfer").value(false))
                .andExpect(jsonPath("$[0].categoryId").value(wohnen));
    }

    @Test
    void aCounterpartChangedByHandStaysAsItIsOnUndo() throws Exception {
        long wohnen = category(user, "Wohnen");
        long freizeit = category(user, "Freizeit");
        long stored = idOf(book(mockMvc, -500, "Aufs Depot", "2026-01-02", wohnen));
        String result = commit(mockMvc, previewJson(mockMvc, BankCsvTest.TRADE_REPUBLIC),
                entry -> category(user, "Investitionen"), true)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long batch = ((Number) JsonPath.read(result, "$.batch.id")).longValue();
        mockMvc.perform(put("/api/transactions/{id}", stored).contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": -500, "description": "Aufs Depot", "date": "2026-01-02", "categoryId": %d}"""
                        .formatted(freizeit)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/imports/{id}", batch)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/transactions").param("month", "2026-01"))
                .andExpect(jsonPath("$[0].transfer").value(false))
                .andExpect(jsonPath("$[0].categoryId").value(freizeit));
    }

    @Test
    void theIbanOfAnOwnAccountIsACertainTransfer() throws Exception {
        long giro = ((Number) JsonPath.read(mockMvc.perform(get("/api/bank-accounts")).andReturn().getResponse()
                .getContentAsString(), "$[0].id")).longValue();
        mockMvc.perform(put("/api/bank-accounts/{id}", giro).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Girokonto", "iban": "%s", "archived": false, "defaultAccount": true}"""
                        .formatted(GIRO_IBAN)))
                .andExpect(status().isOk());

        String preview = previewJson(mockMvc, BankCsvTest.TRADE_REPUBLIC);

        Map<String, Object> incoming = JsonPath.<List<Map<String, Object>>>read(preview, "$.entries[?(@.line == 4)]").getFirst();
        assertThat(incoming.get("transfer")).isEqualTo(true);
        assertThat(incoming.get("transferCertain")).isEqualTo(true);
        assertThat(incoming.get("categoryId")).isNull();
    }

    // --- Sparkasse (decision 47) ---

    @Test
    void aSparkasseBookingIsImportedOnceWhicheverExportComesLater() throws Exception {
        String camt = previewJson(mockMvc, BankCsvTest.SPARKASSE_CAMT);
        assertThat(JsonPath.<String>read(camt, "$.profile")).isEqualTo("SPARKASSE");
        assertThat(JsonPath.<String>read(camt, "$.profileLabel")).isEqualTo("Sparkasse");
        assertThat(JsonPath.<Integer>read(camt, "$.rows")).isEqualTo(6);
        assertThat(JsonPath.<Integer>read(camt, "$.newRows")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(camt, "$.skipped")).isEqualTo(4);
        assertThat(JsonPath.<String>read(camt, "$.sources[0].key")).isEqualTo("SPARKASSE:" + GIRO_IBAN);
        assertThat(JsonPath.<String>read(camt, "$.sources[0].suggestedName")).isEqualTo("Sparkasse …3000");
        List<String> reasons = JsonPath.read(camt, "$.entries[?(@.status == 'SKIPPED')].reason");
        assertThat(reasons).containsExactlyInAnyOrder("ohne Betrag", "vorgemerkt (noch nicht gebucht)",
                "vorgemerkt (noch nicht gebucht)", "Status unbekannt: Umsatz storniert");
        long freizeit = category(user, "Freizeit");
        commit(mockMvc, camt, entry -> freizeit, true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.batch.profile").value("SPARKASSE"));

        // The same bookings as MT940 and "mit Kategorien" are already imported; only the new one is open
        String mt940 = previewJson(mockMvc, BankCsvTest.SPARKASSE_MT940);
        assertThat(JsonPath.<Integer>read(mt940, "$.duplicates")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(mt940, "$.newRows")).isEqualTo(1);
        assertThat(JsonPath.<Object>read(mt940, "$.sources[0].accountId")).isNotNull();
        String categories = previewJson(mockMvc, BankCsvTest.SPARKASSE_CATEGORIES);
        assertThat(JsonPath.<Integer>read(categories, "$.duplicates")).isEqualTo(2);
    }

    @Test
    void aSourceGoesToTheOwnAccountWithItsIban() throws Exception {
        long giro = ((Number) JsonPath.read(mockMvc.perform(get("/api/bank-accounts")).andReturn().getResponse()
                .getContentAsString(), "$[0].id")).longValue();
        mockMvc.perform(put("/api/bank-accounts/{id}", giro).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Girokonto", "iban": "%s", "archived": false, "defaultAccount": true}"""
                        .formatted("DE89 3704 0044 0532 0130 00")))
                .andExpect(status().isOk());

        String preview = previewJson(mockMvc, BankCsvTest.SPARKASSE_MT940);

        assertThat(((Number) JsonPath.read(preview, "$.sources[0].accountId")).longValue()).isEqualTo(giro);
    }

    // --- R30 possible duplicates ---

    @Test
    void aBookingByHandIsAPossibleDuplicate() throws Exception {
        long id = idOf(book(mockMvc, -24, "Kino", "2026-01-15", category(user, "Freizeit")));

        String preview = previewJson(mockMvc, BankCsvTest.N26);

        Map<String, Object> cinema = JsonPath.<List<Map<String, Object>>>read(preview, "$.entries[?(@.line == 4)]").getFirst();
        assertThat(cinema.get("status")).isEqualTo("POSSIBLE_DUPLICATE");
        @SuppressWarnings("unchecked")
        Map<String, Object> duplicateOf = (Map<String, Object>) cinema.get("duplicateOf");
        assertThat(((Number) duplicateOf.get("id")).longValue()).isEqualTo(id);
        assertThat(duplicateOf.get("description")).isEqualTo("Kino");
    }

    // Code review 08.11.2026: a booking by hand that replaces an occurrence (R28) still warns of a duplicate
    @Test
    void aBookingByHandThatReplacesAnOccurrenceIsAPossibleDuplicate() throws Exception {
        long freizeit = category(user, "Freizeit");
        String series = mockMvc.perform(post("/api/recurring-transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": -24, "description": "Kino-Abo", "categoryId": %d, "interval": "MONTHLY",
                         "startDate": "2025-12-15"}""".formatted(freizeit)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long recurringId = ((Number) JsonPath.read(series, "$.id")).longValue();
        long id = idOf(mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": -24, "description": "Kino", "date": "2026-01-15", "categoryId": %d,
                         "replaces": {"recurringId": %d, "date": "2026-01-15"}}""".formatted(freizeit, recurringId)))
                .andExpect(status().isCreated()));

        String preview = previewJson(mockMvc, BankCsvTest.N26);

        Map<String, Object> cinema = JsonPath.<List<Map<String, Object>>>read(preview, "$.entries[?(@.line == 4)]").getFirst();
        assertThat(cinema.get("status")).isEqualTo("POSSIBLE_DUPLICATE");
        @SuppressWarnings("unchecked")
        Map<String, Object> duplicateOf = (Map<String, Object>) cinema.get("duplicateOf");
        assertThat(((Number) duplicateOf.get("id")).longValue()).isEqualTo(id);
        assertThat(cinema.get("transfer")).isEqualTo(false);
    }

    // --- R31 monthly series ---

    @Test
    void suggestsAMonthlySeriesAndItsRowsReplaceTheOccurrencesOnceItExists() throws Exception {
        String file = """
                "Booking Date","Value Date","Partner Name","Partner Iban",Type,"Payment Reference","Account Name","Amount (EUR)"
                2026-08-01,2026-08-01,"Firma Beispiel",,"Credit Transfer","Lohn",Hauptkonto,2500.00
                2026-08-31,2026-08-31,"Firma Beispiel",,"Credit Transfer","Lohn",Hauptkonto,2500.00
                2026-10-01,2026-10-01,"Firma Beispiel",,"Credit Transfer","Lohn",Hauptkonto,2500.00
                2026-11-02,2026-11-02,"Firma Beispiel",,"Credit Transfer","Lohn",Hauptkonto,2500.00
                2026-11-03,2026-11-03,"Kino Nord",,Presentment,,Hauptkonto,-12.00
                """;
        String preview = previewJson(mockMvc, file);
        assertThat(JsonPath.<List<Integer>>read(preview, "$.series[0].lines")).containsExactly(2, 3, 4, 5);
        assertThat(JsonPath.<String>read(preview, "$.series[0].startDate")).isEqualTo("2026-08-01");
        assertThat(JsonPath.<String>read(preview, "$.series[0].description")).isEqualTo("Firma Beispiel · Lohn");
        assertThat(JsonPath.<String>read(preview, "$.series[0].source")).isEqualTo("N26:Hauptkonto");
        assertThat(JsonPath.<List<Object>>read(preview, "$.series")).hasSize(1);

        mockMvc.perform(post("/api/recurring-transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": 2500, "description": "Firma Beispiel · Lohn", "categoryId": %d, "interval": "MONTHLY",
                         "startDate": "2026-08-01"}""".formatted(category(user, "Gehalt/Lohn (netto)"))))
                .andExpect(status().isCreated());

        String again = previewJson(mockMvc, file);
        assertThat(JsonPath.<List<Object>>read(again, "$.series")).isEmpty();
        assertThat(JsonPath.<List<String>>read(again, "$.entries[?(@.line < 6)].replaces.date"))
                .containsExactly("2026-08-01", "2026-09-01", "2026-10-01", "2026-11-01");
    }

    // --- US34 ---

    @Test
    void listsAndUndoesAnImport() throws Exception {
        long freizeit = category(user, "Freizeit");
        String result = commit(mockMvc, previewJson(mockMvc, BankCsvTest.N26), entry -> freizeit, true)
                .andReturn().getResponse().getContentAsString();
        long batch = ((Number) JsonPath.read(result, "$.batch.id")).longValue();

        mockMvc.perform(get("/api/imports"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(batch))
                .andExpect(jsonPath("$[0].profileLabel").value("N26"))
                .andExpect(jsonPath("$[0].count").value(3));
        other.perform(get("/api/imports")).andExpect(jsonPath("$", hasSize(0)));
        other.perform(delete("/api/imports/{id}", batch)).andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/imports/{id}", batch)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/imports")).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/transactions").param("month", "2026-01")).andExpect(jsonPath("$", hasSize(0)));
        // Afterwards the rows are new again
        assertThat(JsonPath.<Integer>read(previewJson(mockMvc, BankCsvTest.N26), "$.newRows")).isEqualTo(3);
    }

    // --- R15 ---

    @Test
    void anotherUsersAccountCategoryOrCounterpartIsInvalid() throws Exception {
        long othersAccount = ((Number) JsonPath.read(other.perform(get("/api/bank-accounts")).andReturn().getResponse()
                .getContentAsString(), "$[0].id")).longValue();
        long othersBooking = idOf(book(other, -500, "Fremd", "2026-01-03", category(otherUser, "Wohnen")));
        String preview = previewJson(mockMvc, BankCsvTest.N26);

        String withForeignAccount = commitBody(preview, entry -> category(user, "Freizeit"), true)
                .replace("\"newAccountName\":\"N26 Hauptkonto\"", "\"accountId\":" + othersAccount);
        mockMvc.perform(post("/api/imports").contentType(MediaType.APPLICATION_JSON).content(withForeignAccount))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sources[0].accountId"));
        commit(mockMvc, preview, entry -> category(otherUser, "Freizeit"), true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("rows[0].categoryId"));
        String withForeignPeer = commitBody(preview, entry -> null, true)
                .replaceFirst("\"categoryId\":null,\"transfer\":false", "\"categoryId\":null,\"transfer\":true,\"transferPeerId\":" + othersBooking);
        mockMvc.perform(post("/api/imports").contentType(MediaType.APPLICATION_JSON).content(withForeignPeer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("rows[0].transferPeerId"));
        other.perform(get("/api/transactions").param("month", "2026-01"))
                .andExpect(jsonPath("$[0].transfer").value(false));
    }

    // --- helpers ---

    private ResultActions preview(MockMvc client, String file) throws Exception {
        return client.perform(multipart("/api/imports/preview")
                .file(new MockMultipartFile("file", "export.csv", "text/csv", file.getBytes(StandardCharsets.UTF_8))));
    }

    private String previewJson(MockMvc client, String file) throws Exception {
        return preview(client, file).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private ResultActions commit(MockMvc client, String preview, Function<Map<String, Object>, Long> category,
            boolean mapSources) throws Exception {
        return client.perform(post("/api/imports").contentType(MediaType.APPLICATION_JSON)
                .content(commitBody(preview, category, mapSources)));
    }

    // Every new row; a suggested transfer stays a transfer, every other row gets the category of the function
    private static String commitBody(String preview, Function<Map<String, Object>, Long> category, boolean mapSources) {
        List<Map<String, Object>> sources = JsonPath.read(preview, "$.sources");
        List<String> mappings = new ArrayList<>();
        if (mapSources) {
            for (Map<String, Object> source : sources) {
                Object accountId = source.get("accountId");
                mappings.add(accountId != null
                        ? "{\"key\":%s,\"accountId\":%s}".formatted(quote(source.get("key")), accountId)
                        : "{\"key\":%s,\"newAccountName\":%s}".formatted(quote(source.get("key")),
                                quote(source.get("suggestedName"))));
            }
        }
        List<String> rows = new ArrayList<>();
        List<Map<String, Object>> entries = JsonPath.read(preview, "$.entries[?(@.status == 'NEW' || @.status == 'POSSIBLE_DUPLICATE')]");
        for (Map<String, Object> entry : entries) {
            boolean transfer = Boolean.TRUE.equals(entry.get("transfer"));
            Long categoryId = transfer ? null : category.apply(entry);
            rows.add(("{\"fingerprint\":%s,\"source\":%s,\"date\":%s,\"amount\":%s,\"description\":%s,"
                    + "\"counterparty\":%s,\"bankCategory\":%s,\"categoryId\":%s,\"transfer\":%s%s}").formatted(
                    quote(entry.get("fingerprint")), quote(entry.get("source")), quote(entry.get("date")),
                    entry.get("amount"), quote(entry.get("description")), quote(entry.get("counterparty")),
                    quote(entry.get("bankCategory")), categoryId, transfer,
                    entry.get("transferPeerId") == null ? "" : ",\"transferPeerId\":" + entry.get("transferPeerId")));
        }
        return "{\"profile\":%s,\"sources\":[%s],\"rows\":[%s]}".formatted(
                quote(JsonPath.read(preview, "$.profile")), String.join(",", mappings), String.join(",", rows));
    }

    private static String quote(Object value) {
        return value == null ? "null" : "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private ResultActions book(MockMvc client, double amount, String description, String date, long categoryId)
            throws Exception {
        return client.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": %s, "description": "%s", "date": "%s", "categoryId": %d}"""
                        .formatted(amount, description, date, categoryId)))
                .andExpect(status().isCreated());
    }

    private static long idOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long category(User owner, String name) {
        return categoryRepository.findAllByOwner(owner).stream()
                .filter(c -> c.getName().equals(name))
                .map(Category::getId)
                .findFirst()
                .orElseThrow();
    }
}
