package com.apex.backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtils {

    public static final int SCALE = 4;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);

    private MoneyUtils() {
    }

    public static BigDecimal bd(String value) {
        if (value == null || value.isBlank()) {
            return ZERO;
        }
        return scale(new BigDecimal(value));
    }

    public static BigDecimal bd(double value) {
        return scale(BigDecimal.valueOf(value));
    }

    public static BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal add(BigDecimal left, BigDecimal right) {
        return scale(scale(left).add(scale(right)));
    }

    public static BigDecimal subtract(BigDecimal left, BigDecimal right) {
        return scale(scale(left).subtract(scale(right)));
    }

    public static BigDecimal multiply(BigDecimal left, BigDecimal right) {
        return scale(scale(left).multiply(scale(right)));
    }

    public static BigDecimal multiply(BigDecimal left, int right) {
        return scale(scale(left).multiply(BigDecimal.valueOf(right)));
    }
}
