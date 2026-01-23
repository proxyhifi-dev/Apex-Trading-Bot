package com.apex.backend.service;

import com.apex.backend.model.Candle;
import com.apex.backend.model.UserProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.apex.backend.util.MoneyUtils;
import com.google.gson.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Service
@Slf4j
@RequiredArgsConstructor
public class FyersService {

    private final Gson gson = new Gson();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${fyers.api.access-token:}")
    private String accessToken;
    @Value("${fyers.api.base-url:https://api-t1.fyers.in/api/v3}")
    private String apiBaseUrl;
    @Value("${fyers.data.base-url:https://api-t1.fyers.in/data}")
    private String dataBaseUrl;

    private final Environment environment;
    private final MetricsService metricsService;
    private final AlertService alertService;
    private final FyersHttpClient fyersHttpClient;
    private final FyersTokenService fyersTokenService;
    private final InstrumentService instrumentService;

    // Semaphore Rate Limiter (Max 8 concurrent)
    private final Semaphore rateLimiter = new Semaphore(8);
    private final Map<String, CacheEntry> candleCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry> ltpCache = new ConcurrentHashMap<>();

    // ... (Keep existing getLTP and getHistoricalData methods)

    public double getLTP(String symbol) {
        List<Candle> history = getHistoricalData(symbol, 1, "5", null);
        return (history != null && !history.isEmpty()) ? history.get(history.size() - 1).getClose() : 0.0;
    }

    public Map<String, BigDecimal> getLtpBatch(List<String> symbols) {
        return getLtpBatch(symbols, null);
    }

    public Map<String, BigDecimal> getLtpBatch(List<String> symbols, String token) {
        String resolvedToken = resolveToken(token);
        if (resolvedToken == null || symbols == null || symbols.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, BigDecimal> result = new HashMap<>();
        List<String> toFetch = new ArrayList<>();
        Map<String, String> symbolMap = new HashMap<>();
        for (String symbol : symbols) {
            Optional<String> resolvedSymbol = instrumentService.resolveTradingSymbol(symbol);
            if (resolvedSymbol.isEmpty()) {
                instrumentService.logMissingInstrument(symbol);
                continue;
            }
            String tradingSymbol = resolvedSymbol.get();
            symbolMap.put(tradingSymbol, symbol);
            String cacheKey = tradingSymbol + "_ltp";
            CacheEntry entry = ltpCache.get(cacheKey);
            if (entry != null && System.currentTimeMillis() - entry.timestamp < 2000) {
                result.put(symbol, MoneyUtils.bd(entry.data.get(0).getClose()));
            } else {
                toFetch.add(tradingSymbol);
            }
        }
        if (!toFetch.isEmpty()) {
            Map<String, BigDecimal> fetched;
            boolean acquired = false;
            try {
                rateLimiter.acquire();
                acquired = true;
                fetched = fetchQuotes(toFetch, resolvedToken);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fetched = Collections.emptyMap();
            } finally {
                if (acquired) {
                    rateLimiter.release();
                }
            }
            fetched.forEach((symbol, ltp) -> {
                double ltpValue = ltp != null ? ltp.doubleValue() : 0.0;
                ltpCache.put(symbol + "_ltp", new CacheEntry(List.of(new Candle(ltpValue, ltpValue, ltpValue, ltpValue, 0L, LocalDateTime.now())), System.currentTimeMillis()));
                String original = symbolMap.getOrDefault(symbol, symbol);
                result.put(original, ltp);
            });
        }
        return result;
    }

    public List<Candle> getHistoricalData(String symbol, int count) {
        return getHistoricalData(symbol, count, "5", null);
    }

    public List<Candle> getHistoricalData(String symbol, int count, String resolution) {
        return getHistoricalData(symbol, count, resolution, null);
    }

    public List<Candle> getHistoricalData(String symbol, int count, String resolution, String token) {
        String resolvedToken = resolveToken(token);
        if (resolvedToken == null) return Collections.emptyList();

        Optional<String> resolvedSymbol = instrumentService.resolveTradingSymbol(symbol);
        if (resolvedSymbol.isEmpty()) {
            instrumentService.logMissingInstrument(symbol);
            return Collections.emptyList();
        }
        String tradingSymbol = resolvedSymbol.get();
        String cacheKey = tradingSymbol + "_" + resolution;
        if (candleCache.containsKey(cacheKey)) {
            CacheEntry entry = candleCache.get(cacheKey);
            if (System.currentTimeMillis() - entry.timestamp < 60000) return entry.data;
        }

        try {
            rateLimiter.acquire();
            List<Candle> data = fetchHistoryInternal(tradingSymbol, count, resolution, resolvedToken);
            if (!data.isEmpty()) {
                candleCache.put(cacheKey, new CacheEntry(data, System.currentTimeMillis()));
            }
            return data;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        } catch (com.apex.backend.exception.FyersCircuitOpenException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to fetch historical data for {}: {}", symbol, e.getMessage());
            return Collections.emptyList();
        } finally {
            rateLimiter.release();
        }
    }

    private List<Candle> fetchHistoryInternal(String symbol, int count, String resolution, String token) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String toDate = LocalDateTime.now().format(dtf);
        String fromDate = LocalDateTime.now().minusDays(20).format(dtf);

        String url = UriComponentsBuilder.fromHttpUrl(dataBaseUrl + "/history")
                .queryParam("symbol", symbol)
                .queryParam("resolution", resolution)
                .queryParam("date_format", 1)
                .queryParam("range_from", fromDate)
                .queryParam("range_to", toDate)
                .queryParam("cont_flag", 1)
                .toUriString();

        String response = executeGetRequest(url, token);
        if (response == null) throw new RuntimeException("Empty Response");

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        List<Candle> candles = new ArrayList<>();

        if (json.has("s") && json.get("s").getAsString().equals("ok")) {
            JsonArray candleArray = json.getAsJsonArray("candles");
            for (JsonElement element : candleArray) {
                JsonArray data = element.getAsJsonArray();
                candles.add(new Candle(
                        data.get(1).getAsDouble(), data.get(2).getAsDouble(),
                        data.get(3).getAsDouble(), data.get(4).getAsDouble(),
                        data.get(5).getAsLong(), LocalDateTime.now()
                ));
            }
        }
        return candles.size() > count ? candles.subList(candles.size() - count, candles.size()) : candles;
    }

    public String placeOrder(String symbol, int qty, String side, String type, double price) {
        return placeOrder(symbol, qty, side, type, price, UUID.randomUUID().toString());
    }

    public String placeOrder(String symbol, int qty, String side, String type, double price, String clientOrderId) {
        String resolvedToken = resolveToken(null);
        if (resolvedToken == null || resolvedToken.isBlank()) {
            throw new RuntimeException("No Token");
        }
        Optional<String> resolvedSymbol = instrumentService.resolveTradingSymbol(symbol);
        if (resolvedSymbol.isEmpty()) {
            instrumentService.logMissingInstrument(symbol);
            throw new IllegalArgumentException("Instrument not found for symbol: " + symbol);
        }
        String tradingSymbol = resolvedSymbol.get();
        String url = apiBaseUrl + "/orders";
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("symbol", tradingSymbol);
            body.put("qty", qty);
            body.put("side", side.equalsIgnoreCase("BUY") ? 1 : -1);
            body.put("type", type.equalsIgnoreCase("MARKET") ? 2 : 1);
            body.put("productType", "INTRADAY");
            body.put("limitPrice", price);
            body.put("validity", "DAY");
            body.put("clientId", clientOrderId);

            String response = fyersHttpClient.post(url, resolvedToken, gson.toJson(body));
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            if (json.has("id")) {
                return json.get("id").getAsString();
            }
            return "ORD-" + System.currentTimeMillis();
        } catch (Exception e) {
            metricsService.incrementBrokerFailures();
            alertService.sendAlert("BROKER_ERROR", e.getMessage());
            log.error("❌ Order Failed: {}", e.getMessage());
            throw new RuntimeException("Order placement failed: " + e.getMessage());
        }
    }

    public String cancelOrder(String brokerOrderId, String token) {
        String resolvedToken = resolveToken(token);
        if (resolvedToken == null || resolvedToken.isBlank()) {
            throw new RuntimeException("No Token for cancel");
        }
        if (brokerOrderId == null || brokerOrderId.isBlank()) {
            throw new IllegalArgumentException("Broker order ID is required");
        }
        String url = apiBaseUrl + "/orders/" + brokerOrderId;
        try {
            // FYERS uses DELETE method for order cancellation
            String response = fyersHttpClient.delete(url, resolvedToken);
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            if (json.has("id")) {
                return json.get("id").getAsString();
            }
            log.warn("Cancel order response missing id: {}", response);
            return brokerOrderId; // Return original ID if response doesn't have new ID
        } catch (Exception e) {
            log.error("Failed to cancel order {}: {}", brokerOrderId, e.getMessage());
            throw new RuntimeException("Order cancellation failed: " + e.getMessage(), e);
        }
    }

    public String modifyOrder(String orderId, com.apex.backend.dto.OrderModifyRequest request) {
        String resolvedToken = resolveToken(null);
        if (resolvedToken == null || resolvedToken.isBlank()) {
            throw new RuntimeException("No Token");
        }
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Order ID is required");
        }
        String url = apiBaseUrl + "/orders";
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("id", orderId);
            if (request.getQty() != null) {
                body.put("qty", request.getQty());
            }
            if (request.getPrice() != null) {
                body.put("limitPrice", request.getPrice());
            }
            if (request.getTriggerPrice() != null) {
                body.put("stopPrice", request.getTriggerPrice());
            }
            if (request.getOrderType() != null) {
                body.put("type", request.getOrderType() == com.apex.backend.dto.PlaceOrderRequest.OrderType.MARKET ? 2 : 1);
            }
            if (request.getValidity() != null) {
                body.put("validity", request.getValidity().name());
            }
            String response = fyersHttpClient.put(url, resolvedToken, gson.toJson(body));
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            if (json.has("id")) {
                return json.get("id").getAsString();
            }
            log.warn("Modify order response missing id: {}", response);
            return orderId;
        } catch (Exception e) {
            log.error("Failed to modify order {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Order modification failed: " + e.getMessage(), e);
        }
    }

    public String placeStopLossOrder(String symbol, int qty, String side, double stopPrice, String clientOrderId, String token) {
        String resolvedToken = resolveToken(token);
        if (resolvedToken == null || resolvedToken.isBlank()) {
            throw new RuntimeException("No Token for stop-loss");
        }
        String url = apiBaseUrl + "/orders";
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("symbol", symbol);
            body.put("qty", qty);
            body.put("side", side.equalsIgnoreCase("BUY") ? 1 : -1);
            body.put("type", 3); // STOP_LOSS order type in FYERS
            body.put("stopPrice", stopPrice);
            body.put("productType", "INTRADAY");
            body.put("validity", "DAY");
            body.put("clientId", clientOrderId);

            String response = fyersHttpClient.post(url, resolvedToken, gson.toJson(body));
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            if (json.has("id")) {
                return json.get("id").getAsString();
            }
            throw new RuntimeException("Stop-loss order placement failed: " + response);
        } catch (Exception e) {
            log.error("Failed to place stop-loss order: {}", e.getMessage());
            throw new RuntimeException("Stop-loss order placement failed: " + e.getMessage(), e);
        }
    }

    public String getOrderStatus(String orderId) {
        return getOrderStatus(orderId, null);
    }

    public String getOrderStatus(String orderId, String token) {
        return getOrderDetails(orderId, token)
                .map(FyersOrderStatus::status)
                .orElse("UNKNOWN");
    }

    public Optional<FyersOrderStatus> getOrderDetails(String orderId, String token) {
        String resolvedToken = resolveToken(token);
        if (resolvedToken == null || orderId == null || orderId.isBlank()) {
            return Optional.empty();
        }
        String url = UriComponentsBuilder.fromHttpUrl(apiBaseUrl + "/orders")
                .queryParam("id", orderId)
                .toUriString();
        try {
            String response = executeGetRequest(url, resolvedToken);
            if (response == null) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response);
            JsonNode dataNode = root.path("data");
            if (dataNode.isObject()) {
                return Optional.of(parseOrderNode(orderId, dataNode));
            }
            if (dataNode.isArray()) {
                for (JsonNode orderNode : dataNode) {
                    String id = orderNode.path("id").asText();
                    if (orderId.equalsIgnoreCase(id)) {
                        return Optional.of(parseOrderNode(orderId, orderNode));
                    }
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("❌ Failed to fetch order status: {}", e.getMessage());
            return Optional.empty();
        }
    }
    public UserProfile getUnifiedUserProfile() {
        String token = resolveToken(null);
        if (token == null || token.isBlank()) {
            return UserProfile.builder()
                    .brokerStatus("DISCONNECTED")
                    .statusMessage("Fyers token not available")
                    .build();
        }
        try {
            Map<String, Object> profile = getProfile(token);
            return mapUserProfile(profile);
        } catch (Exception e) {
            return UserProfile.builder()
                    .brokerStatus("DISCONNECTED")
                    .statusMessage("Fyers profile unavailable: " + e.getMessage())
                    .build();
        }
    }

    public Map<String, Object> getProfile(String token) throws IOException {
        return fetchJsonAsMap(apiBaseUrl + "/profile", token);
    }

    public Map<String, Object> getProfileForUser(Long userId) throws IOException {
        return fetchJsonAsMapWithRefresh(apiBaseUrl + "/profile", userId);
    }

    public Map<String, Object> getFunds(String token) throws IOException {
        return fetchJsonAsMap(apiBaseUrl + "/funds", token);
    }

    public Map<String, Object> getFundsForUser(Long userId) throws IOException {
        return fetchJsonAsMapWithRefresh(apiBaseUrl + "/funds", userId);
    }

    public Map<String, Object> getHoldings(String token) throws IOException {
        return fetchJsonAsMap(apiBaseUrl + "/holdings", token);
    }

    public Map<String, Object> getHoldingsForUser(Long userId) throws IOException {
        return fetchJsonAsMapWithRefresh(apiBaseUrl + "/holdings", userId);
    }

    public Map<String, Object> getPositions(String token) throws IOException {
        return fetchJsonAsMap(apiBaseUrl + "/positions", token);
    }

    public Map<String, Object> getPositionsForUser(Long userId) throws IOException {
        return fetchJsonAsMapWithRefresh(apiBaseUrl + "/positions", userId);
    }

    public Map<String, Object> getOrders(String token) throws IOException {
        return fetchJsonAsMap(apiBaseUrl + "/orders", token);
    }

    public Map<String, Object> getOrdersForUser(Long userId) throws IOException {
        return fetchJsonAsMapWithRefresh(apiBaseUrl + "/orders", userId);
    }

    public Map<String, Object> getTrades(String token) throws IOException {
        return fetchJsonAsMap(apiBaseUrl + "/tradebook", token);
    }

    public Map<String, Object> getTradesForUser(Long userId) throws IOException {
        return fetchJsonAsMapWithRefresh(apiBaseUrl + "/tradebook", userId);
    }

    private String executeGetRequest(String url, String token) {
        if (token == null) return null;
        return fyersHttpClient.get(url, token);
    }

    private Map<String, BigDecimal> fetchQuotes(List<String> symbols, String token) {
        if (symbols.isEmpty()) {
            return Collections.emptyMap();
        }
        String url = dataBaseUrl + "/quotes?symbols=" + String.join(",", symbols);
        try {
            String response = executeGetRequest(url, token);
            if (response == null) {
                return Collections.emptyMap();
            }
            JsonNode root = objectMapper.readTree(response);
            Map<String, BigDecimal> ltpMap = new HashMap<>();
            if ("ok".equalsIgnoreCase(root.path("s").asText())) {
                for (JsonNode item : root.path("d")) {
                    String symbol = item.path("n").asText();
                    double ltp = item.path("v").path("lp").asDouble(0.0);
                    if (symbol != null && !symbol.isBlank()) {
                        ltpMap.put(symbol, MoneyUtils.bd(ltp));
                    }
                }
            }
            return ltpMap;
        } catch (Exception e) {
            log.error("❌ Failed to fetch quotes: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> fetchJsonAsMap(String url, String token) throws IOException {
        String resolvedToken = resolveToken(token);
        String response = executeGetRequest(url, resolvedToken);
        if (response == null) {
            return Collections.emptyMap();
        }
        return objectMapper.readValue(response, Map.class);
    }

    private String resolveToken(String token) {
        if (token != null && !token.isBlank()) {
            return token;
        }
        boolean isProd = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("prod"));
        if (isProd) {
            return null;
        }
        return (accessToken != null && !accessToken.isBlank()) ? accessToken : null;
    }

    private Map<String, Object> fetchJsonAsMapWithRefresh(String url, Long userId) throws IOException {
        String token = fyersTokenService.getAccessToken(userId);
        try {
            return fetchJsonAsMap(url, token);
        } catch (com.apex.backend.exception.FyersApiException e) {
            if (e.getStatusCode() == 401) {
                Optional<String> refreshed = fyersTokenService.refreshAccessToken(userId);
                if (refreshed.isPresent()) {
                    return fetchJsonAsMap(url, refreshed.get());
                }
            }
            throw e;
        }
    }

    private FyersOrderStatus parseOrderNode(String orderId, JsonNode orderNode) {
        String status = orderNode.path("status").asText("UNKNOWN");
        int filledQty = orderNode.path("filledQty").asInt(orderNode.path("filled_qty").asInt(0));
        double avgPrice = orderNode.path("avgPrice").asDouble(orderNode.path("avg_price").asDouble(0.0));
        return new FyersOrderStatus(orderId, status, filledQty, MoneyUtils.bd(avgPrice));
    }

    private UserProfile mapUserProfile(Map<String, Object> profileResponse) {
        if (profileResponse == null || profileResponse.isEmpty()) {
            return UserProfile.builder()
                    .brokerStatus("DISCONNECTED")
                    .statusMessage("Empty profile response")
                    .build();
        }
        JsonNode root = objectMapper.valueToTree(profileResponse);
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull() || data.isEmpty()) {
            return UserProfile.builder()
                    .brokerStatus("DISCONNECTED")
                    .statusMessage(root.path("message").asText("Missing profile data"))
                    .build();
        }
        String name = firstNonBlank(
                data.path("name").asText(null),
                data.path("display_name").asText(null)
        );
        String clientId = firstNonBlank(
                data.path("fy_id").asText(null),
                data.path("client_id").asText(null)
        );
        return UserProfile.builder()
                .name(name)
                .clientId(clientId)
                .broker("FYERS")
                .brokerStatus("CONNECTED")
                .email(data.path("email_id").asText(null))
                .mobileNumber(data.path("mobile_number").asText(null))
                .statusMessage(root.path("message").asText("Connected"))
                .build();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class CacheEntry {
        List<Candle> data;
        long timestamp;
    }
}
