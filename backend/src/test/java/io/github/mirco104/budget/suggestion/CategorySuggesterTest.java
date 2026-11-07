package io.github.mirco104.budget.suggestion;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mirco104.budget.suggestion.CategorySuggester.CategoryData;
import io.github.mirco104.budget.suggestion.CategorySuggester.Entry;
import io.github.mirco104.budget.suggestion.CategorySuggester.KindHint;
import io.github.mirco104.budget.suggestion.CategorySuggester.Suggestion;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Rule R29: a category from the user's own history, certain only when the latest entries agree (decision 41). */
class CategorySuggesterTest {

    private static final long LEBENSMITTEL = 7;
    private static final long FREIZEIT = 8;
    private static final long WOHNEN = 1;
    private static final long INVESTITIONEN = 13;
    private static final long KAPITALERTRAEGE = 18;
    private static final long GEHALT = 15;
    private static final long ARCHIVIERT = 99;

    private static final List<CategoryData> CATEGORIES = List.of(
            new CategoryData(WOHNEN, "Wohnen"),
            new CategoryData(LEBENSMITTEL, "Lebensmittel"),
            new CategoryData(FREIZEIT, "Freizeit"),
            new CategoryData(INVESTITIONEN, "Investitionen"),
            new CategoryData(GEHALT, "Gehalt/Lohn (netto)"),
            new CategoryData(KAPITALERTRAEGE, "Kapitalerträge"));

    // --- normalizing and key ---

    @Test
    void keyIsTheFirstTwoWordsInLowerCaseWithoutDigitsAndPunctuation() {
        assertThat(CategorySuggester.key("REWE Markt GmbH 4711 Berlin")).isEqualTo("rewe markt");
        assertThat(CategorySuggester.key("  Bäckerei-Müller, Filiale 3")).isEqualTo("bäckerei müller");
        assertThat(CategorySuggester.key("APPLE.COM/BILL")).isEqualTo("apple com");
        // Words with less than 2 letters are dropped
        assertThat(CategorySuggester.key("A 1 Kino X")).isEqualTo("kino");
        assertThat(CategorySuggester.key("12345 ...")).isEmpty();
        assertThat(CategorySuggester.key(null)).isEmpty();
    }

    // --- (1) certain ---

    @Test
    void certainWhenTheLatestEntriesWithTheKeyAgree() {
        List<Entry> history = List.of(
                expense("REWE Markt GmbH Berlin", LEBENSMITTEL, "2026-10-01"),
                expense("Rewe Markt Hamburg", LEBENSMITTEL, "2026-09-12"));

        assertThat(suggest("REWE MARKT GMBH 0815", false, null, history))
                .contains(new Suggestion(LEBENSMITTEL, true));
    }

    @Test
    void onlyEntriesWithTheSameSignCount() {
        List<Entry> history = List.of(
                expense("Kino am Markt", FREIZEIT, "2026-10-01"),
                income("Kino am Markt", GEHALT, "2026-10-02"));

        assertThat(suggest("Kino am Markt", false, null, history)).contains(new Suggestion(FREIZEIT, true));
        assertThat(suggest("Kino am Markt", true, null, history)).contains(new Suggestion(GEHALT, true));
    }

    @Test
    void onlyTheTenLatestEntriesWithTheKeyCount() {
        List<Entry> history = new ArrayList<>();
        for (int day = 1; day <= 10; day++) {
            history.add(expense("Kiosk Ecke", FREIZEIT, "2026-10-%02d".formatted(day)));
        }
        // Older than the ten latest, so they do not make it uncertain
        history.add(expense("Kiosk Ecke", LEBENSMITTEL, "2026-01-01"));
        history.add(expense("Kiosk Ecke", LEBENSMITTEL, "2026-01-02"));

        assertThat(suggest("Kiosk Ecke", false, null, history)).contains(new Suggestion(FREIZEIT, true));
    }

    // --- (2) uncertain, most frequent ---

    @Test
    void uncertainWithTheMostFrequentCategoryWhenTheEntriesDisagree() {
        List<Entry> history = List.of(
                expense("Drogerie Kette", FREIZEIT, "2026-10-05"),
                expense("Drogerie Kette", LEBENSMITTEL, "2026-10-01"),
                expense("Drogerie Kette", LEBENSMITTEL, "2026-09-01"));

        assertThat(suggest("Drogerie Kette", false, null, history)).contains(new Suggestion(LEBENSMITTEL, false));
    }

    @Test
    void aTieGoesToTheCategoryOfTheNewestEntry() {
        List<Entry> history = List.of(
                expense("Drogerie Kette", LEBENSMITTEL, "2026-09-01"),
                expense("Drogerie Kette", FREIZEIT, "2026-10-05"));

        assertThat(suggest("Drogerie Kette", false, null, history)).contains(new Suggestion(FREIZEIT, false));
    }

    // --- (3) first word ---

    @Test
    void uncertainFromTheFirstWordWithAtLeastFourLetters() {
        List<Entry> history = List.of(
                expense("Amazon Marketplace", FREIZEIT, "2026-10-01"),
                expense("Amazon Prime", FREIZEIT, "2026-09-01"),
                expense("Tee Laden", LEBENSMITTEL, "2026-09-01"));

        assertThat(suggest("AMAZON PAYMENTS", false, null, history)).contains(new Suggestion(FREIZEIT, false));
        // "tee" has fewer than 4 letters
        assertThat(suggest("Tee Haus", false, null, history)).isEmpty();
    }

    // --- (4) bank category ---

    @Test
    void uncertainFromEntriesWithTheSameBankCategory() {
        List<Entry> history = List.of(
                new Entry("Imbiss Eins", FREIZEIT, LocalDate.parse("2026-10-01"), false, "Essen & Trinken › Restaurants"));

        assertThat(suggest("Pizzeria Zwei", false, "essen & trinken › restaurants", history))
                .contains(new Suggestion(FREIZEIT, false));
    }

    @Test
    void uncertainFromACategoryNamedLikeTheFirstWordOfTheBankCategory() {
        assertThat(suggest("Supermarkt Nord", false, "Lebensmittel › Supermarkt", List.of()))
                .contains(new Suggestion(LEBENSMITTEL, false));
        assertThat(suggest("Spiele-Shop", false, "Freizeit & Unterhaltung › Digitale Produkte", List.of()))
                .contains(new Suggestion(FREIZEIT, false));
    }

    @Test
    void kindHintsOfTheProfilePointToInvestitionenAndKapitalertraege() {
        assertThat(withHints("Kauf Welt-ETF", false, "BUY")).contains(new Suggestion(INVESTITIONEN, false));
        assertThat(withHints("Sparplan Welt-ETF", false, "SAVINGS_PLAN")).contains(new Suggestion(INVESTITIONEN, false));
        assertThat(withHints("Verkauf Welt-ETF", true, "SELL")).contains(new Suggestion(INVESTITIONEN, false));
        assertThat(withHints("Zinsen", true, "INTEREST_PAYOUT")).contains(new Suggestion(KAPITALERTRAEGE, false));
        assertThat(withHints("Dividende Welt-ETF", true, "DIVIDEND")).contains(new Suggestion(KAPITALERTRAEGE, false));
    }

    // Decision 46: without the hints of the profile Trade Republic a kind like BUY means nothing
    @Test
    void withoutKindHintsTheKindsOfTradeRepublicSuggestNothing() {
        assertThat(suggest("Kauf Welt-ETF", false, "BUY", List.of())).isEmpty();
        assertThat(suggest("Zinsen", true, "INTEREST_PAYOUT", List.of())).isEmpty();
    }

    @Test
    void aHintForACategoryTheUserDoesNotHaveSuggestsNothing() {
        List<KindHint> hints = List.of(new KindHint("BUY", "Sparen"));

        assertThat(CategorySuggester.suggest("Kauf", false, "BUY", hints, List.of(), CATEGORIES)).isEmpty();
    }

    @Test
    void theHistoryGoesBeforeTheBankCategory() {
        List<Entry> history = List.of(expense("Supermarkt Nord", WOHNEN, "2026-10-01"));

        assertThat(suggest("Supermarkt Nord", false, "Lebensmittel › Supermarkt", history))
                .contains(new Suggestion(WOHNEN, true));
    }

    // --- (5) none, archived ---

    @Test
    void noSuggestionWithoutAnyMatch() {
        assertThat(suggest("Völlig Neu", false, null, List.of(expense("Kino", FREIZEIT, "2026-10-01")))).isEmpty();
        assertThat(suggest("", false, null, List.of(expense("Kino", FREIZEIT, "2026-10-01")))).isEmpty();
    }

    @Test
    void archivedCategoriesAreNeverSuggested() {
        List<Entry> history = List.of(
                expense("Alter Verein", ARCHIVIERT, "2026-10-01"),
                expense("Alter Verein", ARCHIVIERT, "2026-09-01"));

        assertThat(suggest("Alter Verein", false, null, history)).isEmpty();
    }

    @Test
    void archivedEntriesDoNotMakeTheOthersUncertain() {
        List<Entry> history = List.of(
                expense("Verein Sport", ARCHIVIERT, "2026-10-01"),
                expense("Verein Sport", FREIZEIT, "2026-09-01"));

        assertThat(suggest("Verein Sport", false, null, history)).contains(new Suggestion(FREIZEIT, true));
    }

    private static Optional<Suggestion> suggest(String text, boolean income, String bankCategory, List<Entry> history) {
        return CategorySuggester.suggest(text, income, bankCategory, List.of(), history, CATEGORIES);
    }

    // The hints the profile Trade Republic gives (BankProfile)
    private static final List<KindHint> TRADE_REPUBLIC = List.of(
            new KindHint("BUY", "Investitionen"), new KindHint("SELL", "Investitionen"),
            new KindHint("SAVINGS", "Investitionen"), new KindHint("INTEREST", "Kapitalerträge"),
            new KindHint("DIVIDEND", "Kapitalerträge"));

    private static Optional<Suggestion> withHints(String text, boolean income, String bankCategory) {
        return CategorySuggester.suggest(text, income, bankCategory, TRADE_REPUBLIC, List.of(), CATEGORIES);
    }

    private static Entry expense(String text, long categoryId, String date) {
        return new Entry(text, categoryId, LocalDate.parse(date), false, null);
    }

    private static Entry income(String text, long categoryId, String date) {
        return new Entry(text, categoryId, LocalDate.parse(date), true, null);
    }
}
