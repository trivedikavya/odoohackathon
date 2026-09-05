package com.urbanfurniture.accounting.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boundaries are where bucketing goes wrong, so they are tested
 * exhaustively: an off-by-one here would move real money between columns
 * and break the reconciliation against the ledger.
 */
class AgingBucketTest {

    @ParameterizedTest(name = "{0} days overdue -> {1}")
    @CsvSource({
            "-30, CURRENT", "-1, CURRENT", "0, CURRENT",
            "1, D1_30", "30, D1_30",
            "31, D31_60", "60, D31_60",
            "61, D61_90", "90, D61_90",
            "91, D90_PLUS", "3650, D90_PLUS"
    })
    void bucketsByDaysOverdue(long daysOverdue, AgingBucket expected) {
        assertThat(AgingBucket.forDays(daysOverdue)).isEqualTo(expected);
    }

    @Test
    @DisplayName("a document due today is Current, not overdue")
    void dueTodayIsCurrent() {
        assertThat(AgingBucket.forDays(0)).isEqualTo(AgingBucket.CURRENT);
    }

    @Test
    @DisplayName("every possible day count lands in exactly one bucket")
    void everyDayCountIsClassified() {
        // Exhaustive classification is what makes the bucket columns sum
        // to the report total, which is what allows the reconciliation.
        for (long days = -400; days <= 400; days++) {
            assertThat(AgingBucket.forDays(days)).isNotNull();
        }
    }

    @Test
    void labelsAreStableForTheUi() {
        assertThat(AgingBucket.CURRENT.label()).isEqualTo("Current");
        assertThat(AgingBucket.D1_30.label()).isEqualTo("1-30");
        assertThat(AgingBucket.D31_60.label()).isEqualTo("31-60");
        assertThat(AgingBucket.D61_90.label()).isEqualTo("61-90");
        assertThat(AgingBucket.D90_PLUS.label()).isEqualTo("90+");
    }
}
