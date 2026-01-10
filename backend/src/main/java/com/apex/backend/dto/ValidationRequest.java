package com.apex.backend.dto;

import jakarta.validation.constraints.NotNull;

public record ValidationRequest(@NotNull Long backtestResultId) {}
