package com.apex.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaperSummaryDTO {
    private double cash;
    private double used;
    private double free;
    private double pnl;
    private int positions;
}
