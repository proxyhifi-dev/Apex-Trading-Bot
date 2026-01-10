package com.apex.backend.service.marketdata;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Consumer;

public interface FyersMarketDataClient {

    BigDecimal getLtp(String symbol, String token);

    Optional<FyersQuote> getQuote(String symbol, String token);

    Optional<FyersMarketDepth> getMarketDepth(String symbol, String token);

    AutoCloseable streamTicks(String symbol, String token, Consumer<FyersTick> consumer);
}
