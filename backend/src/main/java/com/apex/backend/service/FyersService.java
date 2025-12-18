package com.apex.backend.service;

import com.apex.backend.model.Candle;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
public class FyersService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final Gson gson = new Gson();

    @Value("${fyers.api.app-id}")
    private String appId;

    @Value("${fyers.api.secret-key}")
    private String secretKey;

    private String accessToken = null;
    private final String DATE_FORMAT = "yyyy-MM-dd";
    private final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT);

    // --- Authentication ---
    public void setAccessToken(String token) {
        this.accessToken = token;
    }

    public boolean generateAccessToken(String authCode) {
        String tokenUrl = "https://api.fyers.in/api/v2/token";
        String appIdHash = sha256(appId + ":" + secretKey);

        Map<String, String> requestBody = Map.of(
                "grant_type", "authorization_code",
                "appIdHash", appIdHash,
                "code", authCode
        );

        try {
            String response = restTemplate.postForObject(tokenUrl, requestBody, String.class);
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            if (json.has("s") && json.get("s").getAsString().equals("ok") && json.has("access_token")) {
                this.accessToken = json.get("access_token").getAsString();
                return true;
            }
        } catch (Exception e) {
            log.error("Token Error: " + e.getMessage());
        }
        return false;
    }

    // --- Data Fetching ---
    public String getQuote(String symbol) {
        if (accessToken == null) return null;
        return executeGetRequest("https://api.fyers.in/data-rest/v2/quotes/?symbols=" + symbol);
    }

    public List<Candle> getHistoricalData(String symbol, int count) {
        if (accessToken == null) return Collections.emptyList();

        String toDate = LocalDateTime.now().format(DATE_FORMATTER);
        String fromDate = LocalDateTime.now().minusDays(10).format(DATE_FORMATTER); // Fetch enough days

        String historyUrl = String.format(
                "https://api.fyers.in/api/v2/history?symbol=%s&resolution=5&date_format=1&range_from=%s&range_to=%s&cont_flag=1",
                symbol, fromDate, toDate
        );

        String jsonResponse = executeGetRequest(historyUrl);
        List<Candle> candles = new ArrayList<>();

        try {
            JsonObject json = JsonParser.parseString(jsonResponse).getAsJsonObject();
            if (json.has("s") && json.get("s").getAsString().equals("ok") && json.has("candles")) {
                for (var item : json.getAsJsonArray("candles")) {
                    Object[] data = gson.fromJson(item.toString(), Object[].class);
                    long epoch = ((Number) data[0]).longValue();

                    Candle c = new Candle(
                            ((Number) data[1]).doubleValue(), // Open
                            ((Number) data[2]).doubleValue(), // High
                            ((Number) data[3]).doubleValue(), // Low
                            ((Number) data[4]).doubleValue(), // Close
                            ((Number) data[5]).longValue(),   // Vol
                            LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault())
                    );
                    candles.add(c);
                }
            }
        } catch (Exception e) {
            log.error("History Error: " + e.getMessage());
        }

        // Ensure we only return 'count' amount and handle sorting
        if (candles.size() > count) {
            return candles.subList(candles.size() - count, candles.size());
        }
        return candles;
    }

    // --- Execution ---
    public void placeOrder(String symbol, int qty, String side, String type, double price, boolean isPaper) {
        int sideInt = side.equalsIgnoreCase("BUY") ? 1 : -1;

        if (isPaper) {
            log.info("📝 PAPER TRADE: {} {} Qty: {} @ {}", side, symbol, qty, price == 0 ? "MKT" : price);
            return;
        }

        if (accessToken == null) return;

        String url = "https://api.fyers.in/api/v2/orders";
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("symbol", symbol);
            body.put("qty", qty);
            body.put("side", sideInt);
            body.put("type", 2); // 2 = Market
            body.put("productType", "INTRADAY");
            body.put("validity", "DAY");
            body.put("disclosedQty", 0);
            body.put("offlineOrder", false);
            body.put("stopLoss", 0);
            body.put("takeProfit", 0);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(gson.toJson(body), headers);
            String response = restTemplate.postForObject(url, entity, String.class);
            log.info("✅ REAL ORDER PLACED: " + response);

        } catch (Exception e) {
            log.error("❌ Order Failed: " + e.getMessage());
        }
    }

    // --- Utilities ---
    private String executeGetRequest(String url) {
        try {
            return restTemplate.execute(url, HttpMethod.GET, request -> {
                request.getHeaders().set("Authorization", "Bearer " + accessToken);
            }, response -> new String(response.getBody().readAllBytes()));
        } catch (Exception e) {
            return null;
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}