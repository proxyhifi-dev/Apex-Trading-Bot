package com.apex.backend.dto;

public record MacdConfirmationDto(
        double macdLine,
        double signalLine,
        double histogram,
        boolean bullishCrossover,
        boolean bearishCrossover,
        boolean zeroLineCrossUp,
        boolean zeroLineCrossDown,
        boolean histogramIncreasing,
        boolean histogramDecreasing,
        boolean bullishDivergence,
        boolean bearishDivergence
) {}
