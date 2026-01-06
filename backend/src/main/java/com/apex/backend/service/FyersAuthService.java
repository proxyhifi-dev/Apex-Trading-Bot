package com.apex.backend.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class FyersAuthService {

    @Value("${fyers.api.app-id}")
    private String appId;

    @Value("${fyers.api.secret-key}")
    private String secretKey;

    @Value("${fyers.redirect-uri}")
    private String redirectUri;

    private static final String AUTH_CODE_URL = "https://api-t1.fyers.in/api/v3/generate-authcode";
    private static final String VALIDATE_URL = "https://api-t1.fyers.in/api/v3/validate-authcode";
    private static final String PROFILE_URL = "https://api-t1.fyers.in/api/v3/profile";

    private final OkHttpClient httpClient = new OkHttpClient();
    private final Gson gson = new Gson();
    private final Map<String, String> fyersTokenStore = new HashMap<>();

    public String generateAuthUrl(String state) {
        if (state == null) state = "apex_" + System.currentTimeMillis();
        return String.format("%s?client_id=%s&redirect_uri=%s&response_type=code&state=%s",
                AUTH_CODE_URL, appId, redirectUri, state);
    }

    public String exchangeAuthCodeForToken(String authCode) throws Exception {
        String appHash = generateAppHash();

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("grant_type", "authorization_code");
        requestBody.addProperty("appIdHash", appHash);
        requestBody.addProperty("code", authCode);
        
        // ✅ FIX 1: This line was missing in your upload. It is required!
        requestBody.addProperty("redirect_uri", redirectUri);

        // ✅ FIX 2: Correct order (MediaType, String) for OkHttp 4.x
        RequestBody body = RequestBody.create(
            MediaType.parse("application/json; charset=utf-8"),
            requestBody.toString()
        );

        Request request = new Request.Builder()
                .url(VALIDATE_URL)
                .post(body) // Use the fixed body
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            // Log exactly what Fyers says so we can debug
            log.info("Fyers Token Exchange Response: {}", responseBody);

            if (!response.isSuccessful()) {
                // This helps us see if it's "invalid_grant" or "redirect_mismatch"
                throw new Exception("Fyers API Error (" + response.code() + "): " + responseBody);
            }

            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            if (json.has("access_token")) {
                log.info("✅ Successfully obtained Fyers access token");
                return json.get("access_token").getAsString();
            } else {
                throw new Exception("Failed to obtain Fyers token: " + responseBody);
            }
        }
    }

    public FyersProfile getUserProfile(String fyersToken) throws Exception {
        Request request = new Request.Builder()
                .url(PROFILE_URL)
                .header("Authorization", fyersToken)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new Exception("Failed to get profile: " + responseBody);
            }
            
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            if (json.has("data")) {
                JsonObject data = json.getAsJsonObject("data");
                FyersProfile profile = new FyersProfile();
                profile.setFyId(data.get("fy_id").getAsString());
                profile.setName(data.has("name") ? data.get("name").getAsString() : "Fyers User");
                profile.setEmail(data.has("email_id") ? data.get("email_id").getAsString() : null);
                return profile;
            }
            throw new Exception("Invalid profile response: " + responseBody);
        }
    }

    public void storeFyersToken(Long userId, String token) {
        fyersTokenStore.put(userId.toString(), token);
    }

    public String getFyersToken(Long userId) {
        return fyersTokenStore.get(userId.toString());
    }

    private String generateAppHash() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest((appId + ":" + secretKey).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            String h = Integer.toHexString(0xff & b);
            if (h.length() == 1) hex.append('0');
            hex.append(h);
        }
        return hex.toString();
    }

    public static class FyersProfile {
        private String fyId, name, email;
        public String getFyId() { return fyId; }
        public void setFyId(String fyId) { this.fyId = fyId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }
}