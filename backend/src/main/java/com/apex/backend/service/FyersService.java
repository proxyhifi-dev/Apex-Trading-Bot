package com.apex.backend.service;

import com.apex.backend.model.Candle;
import com.apex.backend.model.UserProfile;
import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
public class FyersService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final Gson gson = new Gson();

    @Value("${fyers.api.app-id}")
    private String appId;

    // Loaded from fyers_token.txt on startup
    private String accessToken = null;

    public FyersService() {
        try {
            if (Files.exists(Path.of("fyers_token.txt"))) {
                this.accessToken = Files.readString(Path.of("fyers_token.txt")).trim();
                log.info("✅ Loaded Fyers token from fyers_token.txt ({} chars)", accessToken.length());
            } else {
                log.warn("⚠️ fyers_token.txt not found. Please run TokenGenerator first.");
            }
        } catch (IOException e) {
            log.error("❌ Failed to read fyers_token.txt: {}", e.getMessage());
        }
    }

    // =====================================================================
    // HISTORICAL DATA
    // =====================================================================

    public List<Candle> getHistoricalData(String symbol, int count) {
        if (accessToken == null) {
            log.error("❌ No API Token - Cannot fetch history for {}", symbol);
            return Collections.emptyList();
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String toDate = LocalDateTime.now().format(dtf);
        String fromDate = LocalDateTime.now().minusDays(10).format(dtf);

        String url =
                "https://api-t1.fyers.in/data/history?symbol=" + symbol +
                        "&resolution=5&date_format=1&range_from=" + fromDate +
                        "&range_to=" + toDate + "&cont_flag=1";

        try {
            String response = executeGetRequest(url);
            if (response == null || response.isEmpty()) {
                log.warn("Empty history response for {}", symbol);
                return Collections.emptyList();
            }

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            List<Candle> candles = new ArrayList<>();

            if (json.has("s") && json.get("s").getAsString().equals("ok")) {
                JsonArray candleArray = json.getAsJsonArray("candles");
                for (JsonElement element : candleArray) {
                    JsonArray data = element.getAsJsonArray();
                    candles.add(new Candle(
                            data.get(1).getAsDouble(), // open
                            data.get(2).getAsDouble(), // high
                            data.get(3).getAsDouble(), // low
                            data.get(4).getAsDouble(), // close
                            data.get(5).getAsLong(),   // volume
                            LocalDateTime.now()        // timestamp (simplified)
                    ));
                }
            }

            return candles.size() > count
                    ? candles.subList(candles.size() - count, candles.size())
                    : candles;

        } catch (Exception e) {
            log.error("❌ History API Error for {}: {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    // =====================================================================
    // ORDER PLACEMENT
    // =====================================================================

    public void placeOrder(
            String symbol,
            int qty,
            String side,
            String type,
            double price,
            boolean isPaper
    ) {
        if (isPaper) {
            log.info("📝 [PAPER TRADE] {} {} Qty: {}", side, symbol, qty);
            // Paper trades are persisted via TradeExecutionService only
            return;
        }

        if (accessToken == null) {
            throw new RuntimeException("Access token not available. Cannot place real order.");
        }

        String url = "https://api-t1.fyers.in/api/v3/orders";
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("symbol", symbol);
            body.put("qty", qty);
            body.put("side", side.equalsIgnoreCase("BUY") ? 1 : -1);
            body.put("type", 2);          // MARKET
            body.put("productType", "INTRADAY");

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", appId + ":" + accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(gson.toJson(body), headers);
            String response = restTemplate.postForObject(url, entity, String.class);
            log.info("🚀 [REAL ORDER PLACED] {} -> {}", symbol, response);
        } catch (Exception e) {
            log.error("❌ Order Failed: {}", e.getMessage());
            throw new RuntimeException("Order placement failed: " + e.getMessage(), e);
        }
    }

    // =====================================================================
    // PROFILE / FUNDS / HOLDINGS (AGGREGATED)
    // =====================================================================

    public JsonObject getRawProfile() {
        String url = "https://api-t1.fyers.in/api/v3/profile";
        String response = executeGetRequest(url);
        return response != null ? JsonParser.parseString(response).getAsJsonObject() : null;
    }

    public JsonObject getRawFunds() {
        String url = "https://api-t1.fyers.in/api/v3/funds";
        String response = executeGetRequest(url);
        return response != null ? JsonParser.parseString(response).getAsJsonObject() : null;
    }

    public JsonObject getRawHoldings() {
        String url = "https://api-t1.fyers.in/api/v3/holdings";
        String response = executeGetRequest(url);
        return response != null ? JsonParser.parseString(response).getAsJsonObject() : null;
    }

    /**
     * Map Fyers profile + funds + holdings into high-level UserProfile numbers.
     */
    public UserProfile getUnifiedUserProfile() {
        if (accessToken == null) {
            log.warn("⚠️ No access token set. Returning empty UserProfile.");
            UserProfile p = new UserProfile();
            p.setName("No Token");
            p.setAvailableFunds(0.0);
            p.setTotalInvested(0.0);
            p.setCurrentValue(0.0);
            p.setTodaysPnl(0.0);
            return p;
        }

        JsonObject profileJson = getRawProfile();
        JsonObject fundsJson = getRawFunds();
        JsonObject holdingsJson = getRawHoldings();

        UserProfile profile = new UserProfile();

        // Name
        String name = "Fyers User";
        if (profileJson != null && profileJson.has("data")) {
            JsonObject data = profileJson.getAsJsonObject("data");
            if (data.has("name")) {
                name = data.get("name").getAsString();
            }
        }
        profile.setName(name);

        // Funds: pick "Available Balance"
        double available = 0.0;
        if (fundsJson != null && fundsJson.has("fund_limit")) {
            JsonArray arr = fundsJson.getAsJsonArray("fund_limit");
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("title") && obj.get("title").getAsString().equalsIgnoreCase("Available Balance")) {
                    if (obj.has("equityAmount")) {
                        available = obj.get("equityAmount").getAsDouble();
                    }
                }
            }
        }
        profile.setAvailableFunds(available);

        // Aggregate holdings into totals only
        double totalInvested = 0.0;
        double currentValue = 0.0;
        double todaysPnl = 0.0;

        if (holdingsJson != null && holdingsJson.has("holdings")) {
            JsonArray arr = holdingsJson.getAsJsonArray("holdings");
            for (JsonElement el : arr) {
                JsonObject h = el.getAsJsonObject();

                double qty = h.has("qty") ? h.get("qty").getAsDouble() : 0.0;
                double avgPrice = h.has("avgPrice") ? h.get("avgPrice").getAsDouble() : 0.0;
                double ltp = h.has("ltp") ? h.get("ltp").getAsDouble() : 0.0;

                totalInvested += avgPrice * qty;
                currentValue += ltp * qty;
                todaysPnl += h.has("pnl")
                        ? h.get("pnl").getAsDouble()
                        : (ltp - avgPrice) * qty;
            }
        }

        profile.setTotalInvested(totalInvested);
        profile.setCurrentValue(currentValue);
        profile.setTodaysPnl(todaysPnl);

        log.info("✅ Unified profile: name={}, totalInvested={}, currentValue={}, avail={}",
                name, totalInvested, currentValue, available);
        return profile;
    }

    // =====================================================================
    // LOW-LEVEL HTTP
    // =====================================================================

    private String executeGetRequest(String url) {
        if (accessToken == null) {
            log.error("No token set – cannot call {}", url);
            return null;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", appId + ":" + accessToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            return response.getBody();
        } catch (Exception e) {
            log.error("GET {} failed: {}", url, e.getMessage());
            return null;
        }
    }

    // Optional setter for token refresh
    public void setAccessToken(String token) {
        this.accessToken = token;
        log.info("✅ Access token set ({} chars)", token != null ? token.length() : 0);
    }
}
