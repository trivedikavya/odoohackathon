package com.urbanfurniture.accounting.report;

/**
 * Aging buckets, by whole days past due.
 * <p>
 * Closed intervals, so every possible day count falls into exactly one
 * bucket. That exhaustiveness is what makes the columns add up to the
 * report total, which is what allows the total to be reconciled against
 * the ledger.
 */
enum AgingBucket {

    CURRENT("Current"),
    D1_30("1-30"),
    D31_60("31-60"),
    D61_90("61-90"),
    D90_PLUS("90+");

    private final String label;

    AgingBucket(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }

    static AgingBucket forDays(long daysOverdue) {
        if (daysOverdue <= 0) return CURRENT;
        if (daysOverdue <= 30) return D1_30;
        if (daysOverdue <= 60) return D31_60;
        if (daysOverdue <= 90) return D61_90;
        return D90_PLUS;
    }
}
