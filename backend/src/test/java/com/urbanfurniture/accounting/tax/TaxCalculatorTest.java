package com.urbanfurniture.accounting.tax;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TaxCalculatorTest {

    @Test
    void sameStateIsIntraState() {
        assertThat(TaxCalculator.treatmentFor("Gujarat", "Gujarat"))
                .isEqualTo(TaxTreatment.INTRA_STATE);
    }

    @Test
    void differentStateIsInterState() {
        assertThat(TaxCalculator.treatmentFor("Gujarat", "Maharashtra"))
                .isEqualTo(TaxTreatment.INTER_STATE);
    }

    @Test
    @DisplayName("state names are compared case- and whitespace-insensitively")
    void normalisesStateNames() {
        // Free-text fields typed by humans. Treating these as different
        // states would wrongly charge IGST on a local sale.
        assertThat(TaxCalculator.treatmentFor("Gujarat", "  gujarat "))
                .isEqualTo(TaxTreatment.INTRA_STATE);
        assertThat(TaxCalculator.treatmentFor("Tamil Nadu", "TAMIL  NADU"))
                .isEqualTo(TaxTreatment.INTRA_STATE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("a blank place of supply is UNSPECIFIED, never guessed")
    void blankPlaceOfSupplyIsUnspecified(String blank) {
        assertThat(TaxCalculator.treatmentFor("Gujarat", blank)).isEqualTo(TaxTreatment.UNSPECIFIED);
    }

    @Test
    void unknownSellerStateIsUnspecified() {
        assertThat(TaxCalculator.treatmentFor(null, "Gujarat")).isEqualTo(TaxTreatment.UNSPECIFIED);
    }

    @Test
    void interStateIsAllIgst() {
        GstSplit split = TaxCalculator.split(new BigDecimal("180.00"), TaxTreatment.INTER_STATE);
        assertThat(split.igst()).isEqualByComparingTo("180.00");
        assertThat(split.cgst()).isEqualByComparingTo("0.00");
        assertThat(split.sgst()).isEqualByComparingTo("0.00");
    }

    @Test
    void intraStateSplitsInHalf() {
        GstSplit split = TaxCalculator.split(new BigDecimal("180.00"), TaxTreatment.INTRA_STATE);
        assertThat(split.cgst()).isEqualByComparingTo("90.00");
        assertThat(split.sgst()).isEqualByComparingTo("90.00");
        assertThat(split.igst()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("an odd number of paise still sums back to the original exactly")
    void oddPaiseDoesNotDrift() {
        // 0.01 cannot be halved evenly. Rounding both halves would give
        // 0.02 and unbalance the journal entry, so the second half is
        // derived by subtraction.
        GstSplit split = TaxCalculator.split(new BigDecimal("0.01"), TaxTreatment.INTRA_STATE);
        assertThat(split.cgst()).isEqualByComparingTo("0.01");
        assertThat(split.sgst()).isEqualByComparingTo("0.00");
        assertThat(split.total()).isEqualByComparingTo("0.01");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "0.03", "9.51", "52.83", "180.00", "1234.57", "99999.99"})
    @DisplayName("components always sum back to the tax they came from")
    void componentsAlwaysSumBackToTotal(String tax) {
        BigDecimal amount = new BigDecimal(tax);
        assertThat(TaxCalculator.split(amount, TaxTreatment.INTRA_STATE).total())
                .isEqualByComparingTo(amount);
        assertThat(TaxCalculator.split(amount, TaxTreatment.INTER_STATE).total())
                .isEqualByComparingTo(amount);
    }

    @Test
    @DisplayName("an unspecified treatment produces no split at all")
    void unspecifiedProducesNoSplit() {
        GstSplit split = TaxCalculator.split(new BigDecimal("180.00"), TaxTreatment.UNSPECIFIED);
        assertThat(split.total()).isEqualByComparingTo("0.00");
        assertThat(split.isUnsplit()).isTrue();
    }

    @Test
    void nullTaxIsTreatedAsZero() {
        assertThat(TaxCalculator.split(null, TaxTreatment.INTRA_STATE).total())
                .isEqualByComparingTo("0.00");
    }
}
