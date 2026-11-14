package io.github.mirco104.budget.importer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mirco104.budget.importer.BankCategoryMatcher.Own;
import io.github.mirco104.budget.importer.BankCategoryMatcher.Part;
import io.github.mirco104.budget.importer.BankCategoryMatcher.Status;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R33: matching the category of the bank with the active own categories. */
class BankCategoryMatcherTest {

    private static final List<Own> OWN = List.of(
            new Own(1, "Versicherung", null),
            new Own(2, "Haftpflicht", 1L),
            new Own(3, "Lebensmittel", null),
            new Own(4, "Freizeit", null),
            new Own(5, "Mobilität", null),
            new Own(6, "Sonstiges", 4L));

    @Test
    void splitsTheBankCategoryIntoParentAndChild() {
        var result = BankCategoryMatcher.match("Versicherungen › Haftpflicht", OWN);

        assertThat(result.parent()).isEqualTo("Versicherungen");
        assertThat(result.child()).isEqualTo("Haftpflicht");
        assertThat(BankCategoryMatcher.match("Lebensmittel", OWN).child()).isNull();
    }

    @Test
    void normalizedEqualNamesExist() {
        assertThat(BankCategoryMatcher.normalize("  Lebensmittel & Drogerie! ")).isEqualTo("lebensmittel drogerie");
        assertThat(BankCategoryMatcher.normalize("Mobilität")).isEqualTo("mobilitaet");
        assertThat(BankCategoryMatcher.normalize("Straße")).isEqualTo("strasse");

        assertThat(BankCategoryMatcher.match("LEBENSMITTEL", OWN).parentMatch()).isEqualTo(new Part(Status.EXISTING, 3L));
        assertThat(BankCategoryMatcher.match("Mobilitaet", OWN).parentMatch()).isEqualTo(new Part(Status.EXISTING, 5L));
    }

    // Endings "en", "n", "e", "s" per word
    @Test
    void guessesNamesThatDifferOnlyInTheirEnding() {
        var result = BankCategoryMatcher.match("Versicherungen › Haftpflicht", OWN);

        assertThat(result.parentMatch()).isEqualTo(new Part(Status.GUESSED, 1L));
        assertThat(result.childMatch()).isEqualTo(new Part(Status.EXISTING, 2L));
    }

    // Levenshtein: 1 character from 5 characters on, 2 from 10 on
    @Test
    void guessesSmallTypos() {
        assertThat(BankCategoryMatcher.match("Freizet", OWN).parentMatch()).isEqualTo(new Part(Status.GUESSED, 4L));
        assertThat(BankCategoryMatcher.match("Lebensmitel", OWN).parentMatch()).isEqualTo(new Part(Status.GUESSED, 3L));
        assertThat(BankCategoryMatcher.match("Lbensmitel", OWN).parentMatch()).isEqualTo(new Part(Status.GUESSED, 3L));
        // 2 typos in a name below 10 characters are too many
        assertThat(BankCategoryMatcher.match("Frizet", OWN).parentMatch()).isEqualTo(new Part(Status.NEW, null));
    }

    @Test
    void shortNamesAreNeverGuessedByTypo() {
        var own = List.of(new Own(7, "Bar", null));

        assertThat(BankCategoryMatcher.match("Bad", own).parentMatch()).isEqualTo(new Part(Status.NEW, null));
    }

    @Test
    void unknownNamesAreNew() {
        var result = BankCategoryMatcher.match("Haushalt › Möbel", OWN);

        assertThat(result.parentMatch()).isEqualTo(new Part(Status.NEW, null));
        assertThat(result.childMatch()).isEqualTo(new Part(Status.NEW, null));
    }

    // The child is matched only with the subcategories of the found parent
    @Test
    void matchesTheChildOnlyUnderItsParent() {
        var result = BankCategoryMatcher.match("Lebensmittel › Haftpflicht", OWN);

        assertThat(result.parentMatch()).isEqualTo(new Part(Status.EXISTING, 3L));
        assertThat(result.childMatch()).isEqualTo(new Part(Status.NEW, null));
        assertThat(BankCategoryMatcher.match("Freizeit › Sonstiges", OWN).childMatch())
                .isEqualTo(new Part(Status.EXISTING, 6L));
    }

    // A parent only matches top-level categories
    @Test
    void doesNotMatchAParentWithASubcategory() {
        assertThat(BankCategoryMatcher.match("Haftpflicht", OWN).parentMatch()).isEqualTo(new Part(Status.NEW, null));
    }

    @Test
    void prefersTheSmallestDistanceThenTheAlphabeticallyFirst() {
        var own = List.of(new Own(10, "Kleidungen", null), new Own(11, "Kleidung", null), new Own(12, "Kleidunk", null));

        assertThat(BankCategoryMatcher.match("Kleidung", own).parentMatch()).isEqualTo(new Part(Status.EXISTING, 11L));
        var tie = List.of(new Own(20, "Reisex", null), new Own(21, "Reisea", null));
        assertThat(BankCategoryMatcher.match("Reiseb", tie).parentMatch()).isEqualTo(new Part(Status.GUESSED, 21L));
    }

    @Test
    void theSuggestedCategoryIsTheChildOrTheParent() {
        assertThat(BankCategoryMatcher.match("Versicherungen › Haftpflicht", OWN).categoryId()).isEqualTo(2L);
        assertThat(BankCategoryMatcher.match("Versicherungen", OWN).categoryId()).isEqualTo(1L);
        assertThat(BankCategoryMatcher.match("Versicherungen › Kfz", OWN).categoryId()).isNull();
    }
}
