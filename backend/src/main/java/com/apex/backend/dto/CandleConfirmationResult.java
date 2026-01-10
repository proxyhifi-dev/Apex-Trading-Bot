package com.apex.backend.dto;

public record CandleConfirmationResult(
        boolean bullishConfirmed,
        boolean bearishConfirmed,
        boolean volumeConfirmed
) {}
