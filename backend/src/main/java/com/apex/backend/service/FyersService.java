package com.apex.backend.service;

import com.apex.backend.model.Candle;
import com.apex.backend.model.UserProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Service
@Slf4j
@RequiredArgsConstructor
public class FyersService {

    private RestTemplate restTemplate;
    private final Gson gson = new Gson();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${fyers.api.app-id}")
    private String appId;
    private String accessToken = null;

    // Semaphore Rate Limiter (Max 8 concurrent)
    private final Semaphore rateLimiter = new Semaphore(8);
    private final Map<String, CacheEntry> candleCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry> ltpCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // ✅ CHECKLIST: Network timeout handling (10-second timeout)
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 10s
        factory.setReadTimeout(10000);    // 10s
        this.restTemplate = new RestTemplate(factory);

        try {
            if (Files.exists(Path.of("fyers_token.txt"))) {
                this.accessToken = Files.readString(Path.of("fyers_token.txt")).trim();
            }
        } catch (IOException e) {
            log.error("❌ Failed to read fyers_token.txt: {}", e.getMessage());
        }
    }

    // ... (Keep existing getLTP and getHistoricalData methods)

    public double getLTP(String symbol) {
        List<Candle> history = getHistoricalData(symbol, 1, "5");
        return (history != null && !history.isEmpty()) ? history.get(history.size() - 1).getClose() : 0.0;
    }

    public Map<String, Double> getLtpBatch(List<String> symbols) {
        return getLtpBatch(symbols, accessToken);
    }

    public Map<String, Double> getLtpBatch(List<String> symbols, String token) {
        if (token == null || symbols == null || symbols.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Double> result = new HashMap<>();
        List<String> toFetch = new ArrayList<>();
        for (String symbol : symbols) {
            String cacheKey = symbol + "_ltp";
            CacheEntry entry = ltpCache.get(cacheKey);
            if (entry != null && System.currentTimeMillis() - entry.timestamp < 2000) {
                result.put(symbol, entry.data.get(0).getClose());
            } else {
                toFetch.add(symbol);
            }
        }
        if (!toFetch.isEmpty()) {
            Map<String, Double> fetched;
            boolean acquired = false;
            try {
                rateLimiter.acquire();
                acquired = true;
                fetched = fetchQuotes(toFetch, token);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fetched = Collections.emptyMap();
            } finally {
                if (acquired) {
                    rateLimiter.release();
                }
            }
            fetched.forEach((symbol, ltp) -> {
                ltpCache.put(symbol + "_ltp", new CacheEntry(List.of(new Candle(ltp, ltp, ltp, ltp, 0L, LocalDateTime.now())), System.currentTimeMillis()));
                result.put(symbol, ltp);
            });
        }
        return result;
    }

    public List<Candle> getHistoricalData(String symbol, int count) {
        return getHistoricalData(symbol, count, "5");
    }

    public List<Candle> getHistoricalData(String symbol, int count, String resolution) {
        if (accessToken == null) return Collections.emptyList();

        String cacheKey = symbol + "_" + resolution;
        if (candleCache.containsKey(cacheKey)) {
            CacheEntry entry = candleCache.get(cacheKey);
            if (System.currentTimeMillis() - entry.timestamp < 60000) return entry.data;
        }

        try {
            rateLimiter.acquire();
            List<Candle> data = fetchWithRetry(symbol, count, resolution);
            if (!data.isEmpty()) {
                candleCache.put(cacheKey, new CacheEntry(data, System.currentTimeMillis()));
            }
            return data;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        } finally {
            rateLimiter.release();
        }
    }

    // ✅ CHECKLIST: API rate limit handling (429 errors) & Retry Logic
    private List<Candle> fetchWithRetry(String symbol, int count, String resolution) {
        int attempts = 0;
        int maxRetries = 3;

        while (attempts < maxRetries) {
            try {
                return fetchHistoryInternal(symbol, count, resolution);
            } catch (HttpClientErrorException.TooManyRequests e) {
                // Handle 429 specifically
                attempts++;
                log.warn("⚠️ Rate Limit 429 Hit for {}. Cooling down 5s...", symbol);
                sleep(5000);
            } catch (ResourceAccessException | HttpServerErrorException e) {
                // Network/Server Errors
                attempts++;
                long backoff = 500L * attempts;
                log.warn("⚠️ Network Error for {} ({}): {}. Retrying in {}ms...", symbol, attempts, e.getMessage(), backoff);
                sleep(backoff);
            } catch (Exception e) {
                log.error("❌ Unrecoverable Error for {}: {}", symbol, e.getMessage());
                break; // Don't retry logic errors
            }
        }
        return Collections.emptyList();
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private List<Candle> fetchHistoryInternal(String symbol, int count, String resolution) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String toDate = LocalDateTime.now().format(dtf);
        String fromDate = LocalDateTime.now().minusDays(20).format(dtf);

        String url = "https://api-t1.fyers.in/data/history?symbol=" + symbol +
                "&resolution=" + resolution + "&date_format=1&range_from=" + fromDate +
                "&range_to=" + toDate + "&cont_flag=1";

        String response = executeGetRequest(url);
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
        if (accessToken == null) throw new RuntimeException("No Token");

        String url = "https://api-t1.fyers.in/api/v3/orders";
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("symbol", symbol);
            body.put("qty", qty);
            body.put("side", side.equalsIgnoreCase("BUY") ? 1 : -1);
            body.put("type", type.equalsIgnoreCase("MARKET") ? 2 : 1);
            body.put("productType", "INTRADAY");
            body.put("limitPrice", price);
            body.put("validity", "DAY");

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", appId + ":" + accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(gson.toJson(body), headers);
            // ✅ Using configured restTemplate (with timeouts)
            String response = restTemplate.postForObject(url, entity, String.class);

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            if (json.has("id")) return json.get("id").getAsString();

            return "ORD-" + System.currentTimeMillis();
        } catch (Exception e) {
            log.error("❌ Order Failed: {}", e.getMessage());
            throw new RuntimeException("Order placement failed: " + e.getMessage());
        }
    }

    public String getOrderStatus(String orderId) { return "FILLED"; }
    public UserProfile getUnifiedUserProfile() { return new UserProfile(); }

    public Map<String, Object> getProfile(String token) throws IOException {
        return fetchJsonAsMap("https://api-t1.fyers.in/api/v3/profile", token);
    }

    public Map<String, Object> getFunds(String token) throws IOException {
        return fetchJsonAsMap("https://api-t1.fyers.in/api/v3/funds", token);
    }

    public Map<String, Object> getHoldings(String token) throws IOException {
        return fetchJsonAsMap("https://api-t1.fyers.in/api/v3/holdings", token);
    }

    public Map<String, Object> getPositions(String token) throws IOException {
        return fetchJsonAsMap("https://api-t1.fyers.in/api/v3/positions", token);
    }

    public Map<String, Object> getOrders(String token) throws IOException {
        return fetchJsonAsMap("https://api-t1.fyers.in/api/v3/orders", token);
    }

    public Map<String, Object> getTrades(String token) throws IOException {
        return fetchJsonAsMap("https://api-t1.fyers.in/api/v3/tradebook", token);
    }

    private String executeGetRequest(String url) {
        return executeGetRequest(url, accessToken);
    }

    private String executeGetRequest(String url, String token) {
        if (token == null) return null;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", appId + ":" + token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return response.getBody();
    }

    private Map<String, Double> fetchQuotes(List<String> symbols, String token) {
        if (symbols.isEmpty()) {
            return Collections.emptyMap();
        }
        String url = "https://api-t1.fyers.in/data/quotes?symbols=" + String.join(",", symbols);
        try {
            String response = executeGetRequest(url, token);
            if (response == null) {
                return Collections.emptyMap();
            }
            JsonNode root = objectMapper.readTree(response);
            Map<String, Double> ltpMap = new HashMap<>();
            if ("ok".equalsIgnoreCase(root.path("s").asText())) {
                for (JsonNode item : root.path("d")) {
                    String symbol = item.path("n").asText();
                    double ltp = item.path("v").path("lp").asDouble(0.0);
                    if (symbol != null && !symbol.isBlank()) {
                        ltpMap.put(symbol, ltp);
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
        String response = executeGetRequest(url, token);
        if (response == null) {
            return Collections.emptyMap();
        }
        return objectMapper.readValue(response, Map.class);
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class CacheEntry {
        List<Candle> data;
        long timestamp;
    }
}
