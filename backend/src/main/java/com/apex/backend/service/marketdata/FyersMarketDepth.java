package com.apex.backend.service.marketdata;

import java.util.List;

public record FyersMarketDepth(
        String symbol,
        List<FyersDepthLevel> bids,
        List<FyersDepthLevel> asks
) {}
