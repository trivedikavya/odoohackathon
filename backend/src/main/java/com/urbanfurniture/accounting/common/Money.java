package com.urbanfurniture.accounting.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money helpers. Every monetary value in the system is a {@link BigDecimal}
 * scaled to 2 decimal places with HALF_UP rounding - the same scale as the
 * NUMERIC(15,2) columns - so ledger totals cannot drift by rounding.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);

    private Money() {
    }

    public static BigDecimal of(BigDecimal value) {
        return value == null ? ZERO : value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal of(double value) {
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal nullSafe(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return of(nullSafe(a).add(nullSafe(b)));
    }

    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return of(nullSafe(a).subtract(nullSafe(b)));
    }

    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        return of(nullSafe(a).multiply(nullSafe(b)));
    }

    public static boolean isZero(BigDecimal value) {
        return nullSafe(value).compareTo(BigDecimal.ZERO) == 0;
    }

    public static boolean isPositive(BigDecimal value) {
        return nullSafe(value).compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isNegative(BigDecimal value) {
        return nullSafe(value).compareTo(BigDecimal.ZERO) < 0;
    }

    public static boolean eq(BigDecimal a, BigDecimal b) {
        return nullSafe(a).compareTo(nullSafe(b)) == 0;
    }

    public static boolean gt(BigDecimal a, BigDecimal b) {
        return nullSafe(a).compareTo(nullSafe(b)) > 0;
    }
}
