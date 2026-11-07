package io.github.mirco104.budget.bankaccount;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** R26: an IBAN is stored without spaces in upper case and checked for its structure and check digits. */
class IbanTest {

    // Example IBANs from the published ISO 13616 / bank documentation, not real accounts of anyone
    private static final String GERMAN_EXAMPLE = "DE89370400440532013000";

    @Test
    void normalizesSpacesAndCase() {
        assertThat(Iban.normalize(" de89 3704 0044 0532 0130 00 ")).isEqualTo(GERMAN_EXAMPLE);
    }

    @Test
    void blankIsNoIban() {
        assertThat(Iban.normalize("   ")).isNull();
        assertThat(Iban.normalize(null)).isNull();
    }

    @Test
    void acceptsValidCheckDigits() {
        assertThat(Iban.isValid(GERMAN_EXAMPLE)).isTrue();
        assertThat(Iban.isValid("GB82WEST12345698765432")).isTrue();
        assertThat(Iban.isValid("LU280019400644750000")).isTrue();
    }

    @Test
    void rejectsWrongCheckDigits() {
        assertThat(Iban.isValid("DE88370400440532013000")).isFalse();
    }

    @Test
    void rejectsWrongStructure() {
        assertThat(Iban.isValid("DE8937040044")).isFalse();
        assertThat(Iban.isValid("1E89370400440532013000")).isFalse();
        assertThat(Iban.isValid("DE89-370400440532013000")).isFalse();
        assertThat(Iban.isValid("DE89370400440532013000123456789012345")).isFalse();
    }
}
