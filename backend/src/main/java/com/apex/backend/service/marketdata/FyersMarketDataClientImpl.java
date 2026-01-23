package com.apex.backend.service.marketdata;

import com.apex.backend.exception.FyersApiException;
import com.apex.backend.service.FyersHttpClient;
import com.apex.backend.service.InstrumentService;
import com.apex.backend.util.MoneyUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class FyersMarketDataClientImpl implements FyersMarketDataClient {

    private final FyersHttpClient fyersHttpClient;
    private final InstrumentService instrumentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${fyers.data.base-url:https://api-t1.fyers.in/data}")
    private String dataBaseUrl;

    @Value("${fyers.data.depth-path:/depth}")
    private String depthPath;

    @Value("${fyers.market.stream.interval-ms:1000}")
    private long streamIntervalMs;

    @Override
    public BigDecimal getLtp(String symbol, String token) {
        return getQuote(symbol, token)
                .map(FyersQuote::lastTradedPrice)
                .orElse(MoneyUtils.ZERO);
    }

    @Override
    public Optional<FyersQuote> getQuote(String symbol, String token) {
        Optional<String> resolvedSymbol = resolveSymbol(symbol);
        if (resolvedSymbol.isEmpty()) {
            return Optional.empty();
        }
        String tradingSymbol = resolvedSymbol.get();
        String url = dataBaseUrl + "/quotes?symbols=" + tradingSymbol;
        try {
            String response = fyersHttpClient.get(url, token);
            if (response == null) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response);
            if (!"ok".equalsIgnoreCase(root.path("s").asText())) {
                return Optional.empty();
            }
            JsonNode dataNode = root.path("d");
            if (!dataNode.isArray() || dataNode.isEmpty()) {
                return Optional.empty();
            }
            JsonNode first = dataNode.get(0);
            String quoteSymbol = first.path("n").asText(tradingSymbol);
            JsonNode values = first.path("v");
            BigDecimal ltp = MoneyUtils.bd(values.path("lp").asDouble(0.0));
            BigDecimal bid = MoneyUtils.bd(firstNonZero(values, "bp", "bid", "best_bid"));
            BigDecimal ask = MoneyUtils.bd(firstNonZero(values, "ap", "ask", "best_ask"));
            return Optional.of(new FyersQuote(quoteSymbol, ltp, bid, ask));
        } catch (Exception e) {
            log.warn("Failed to fetch quote for {}: {}", tradingSymbol, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<FyersMarketDepth> getMarketDepth(String symbol, String token) {
        Optional<String> resolvedSymbol = resolveSymbol(symbol);
        if (resolvedSymbol.isEmpty()) {
            return Optional.empty();
        }
        String tradingSymbol = resolvedSymbol.get();
        String url = dataBaseUrl + depthPath + "?symbol=" + tradingSymbol;
        try {
            String response = fyersHttpClient.get(url, token);
            if (response == null) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response);
            if (!"ok".equalsIgnoreCase(root.path("s").asText())) {
                return Optional.empty();
            }
            JsonNode dataNode = root.path("d");
            JsonNode depthNode = dataNode.path("depth");
            if (depthNode.isMissingNode() || depthNode.isNull()) {
                depthNode = dataNode;
            }
            List<FyersDepthLevel> bids = parseDepth(depthNode.path("bids"));
            List<FyersDepthLevel> asks = parseDepth(depthNode.path("asks"));
            return Optional.of(new FyersMarketDepth(tradingSymbol, bids, asks));
        } catch (FyersApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to fetch market depth for {}: {}", tradingSymbol, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public AutoCloseable streamTicks(String symbol, String token, Consumer<FyersTick> consumer) {
        Optional<String> resolvedSymbol = resolveSymbol(symbol);
        if (resolvedSymbol.isEmpty()) {
            return () -> {};
        }
        String tradingSymbol = resolvedSymbol.get();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "fyers-tick-stream-" + tradingSymbol);
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(() -> {
            try {
                getQuote(tradingSymbol, token).ifPresent(quote -> consumer.accept(new FyersTick(
                        quote.symbol(),
                        quote.lastTradedPrice(),
                        quote.bidPrice(),
                        quote.askPrice(),
                        Instant.now()
                )));
            } catch (Exception ex) {
                log.warn("Tick stream error for {}: {}", symbol, ex.getMessage());
            }
        }, 0, streamIntervalMs, TimeUnit.MILLISECONDS);
        return executor::shutdownNow;
    }

    private Optional<String> resolveSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        Optional<String> resolved = instrumentService.resolveTradingSymbol(symbol);
        if (resolved.isEmpty()) {
            instrumentService.logMissingInstrument(symbol);
        }
        return resolved;
    }

    private double firstNonZero(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field)) {
                double value = node.path(field).asDouble(0.0);
                if (value > 0) {
                    return value;
                }
            }
        }
        return 0.0;
    }

    private List<FyersDepthLevel> parseDepth(JsonNode depthNode) {
        if (depthNode == null || !depthNode.isArray()) {
            return Collections.emptyList();
        }
        List<FyersDepthLevel> levels = new ArrayList<>();
        for (JsonNode node : depthNode) {
            BigDecimal price = MoneyUtils.bd(node.path("price").asDouble(node.path("p").asDouble(0.0)));
            BigDecimal qty = MoneyUtils.bd(node.path("quantity").asDouble(node.path("q").asDouble(0.0)));
            if (price.doubleValue() > 0) {
                levels.add(new FyersDepthLevel(price, qty));
            }
        }
        return levels;
    }
}
