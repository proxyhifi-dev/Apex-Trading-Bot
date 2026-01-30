package com.apex.backend.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.annotation.PostConstruct;
import com.apex.backend.model.User;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.service.secrets.SecretsManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.io.IOException;
import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FyersAuthService {

    @Value("${fyers.api.app-id:}")
    private String appId;

    @Value("${fyers.api.secret-key:}")
    private String secretKey;

    @Value("${fyers.redirect-uri:}")
    private String redirectUri;

    @Value("${fyers.api.http.connect-timeout-seconds:5}")
    private int connectTimeoutSeconds;

    @Value("${fyers.api.http.read-timeout-seconds:10}")
    private int readTimeoutSeconds;

    @Value("${fyers.api.http.write-timeout-seconds:10}")
    private int writeTimeoutSeconds;

    @Value("${fyers.api.http.max-retries:2}")
    private int maxRetries;

    private static final String AUTH_CODE_URL = "https://api-t1.fyers.in/api/v3/generate-authcode";
    private static final String VALIDATE_URL = "https://api-t1.fyers.in/api/v3/validate-authcode";
    private static final String PROFILE_URL = "https://api-t1.fyers.in/api/v3/profile";

    @Value("${fyers.api.refresh-url:https://api-t1.fyers.in/api/v3/refresh-token}")
    private String refreshUrl;

    private OkHttpClient httpClient;
    private final Gson gson = new Gson();
    private final UserRepository userRepository;
    private final AsyncDelayService asyncDelayService;
    private final SecretsManagerService secretsManagerService;

    @PostConstruct
    void validateConfig() {
        appId = secretsManagerService.resolve("FYERS_API_APP_ID", appId);
        secretKey = secretsManagerService.resolve("FYERS_API_SECRET_KEY", secretKey);
        redirectUri = secretsManagerService.resolve("FYERS_REDIRECT_URI", redirectUri);

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .readTimeout(Duration.ofSeconds(readTimeoutSeconds))
                .writeTimeout(Duration.ofSeconds(writeTimeoutSeconds))
                .retryOnConnectionFailure(true)
                .build();

        if (appId == null || appId.isBlank()) {
            log.warn("Missing config fyers.api.app-id (FYERS_API_APP_ID). Fyers endpoints will be unavailable.");
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            log.warn("Missing config fyers.redirect-uri (FYERS_REDIRECT_URI). Fyers endpoints will be unavailable.");
        }
        if (secretKey == null || secretKey.isBlank()) {
            log.warn("Missing config fyers.api.secret-key (FYERS_API_SECRET_KEY). Fyers endpoints will be unavailable.");
        }
    }

    public void ensureAuthUrlConfig() {
        List<String> missing = new java.util.ArrayList<>();
        if (appId == null || appId.isBlank()) {
            missing.add("FYERS_API_APP_ID");
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            missing.add("FYERS_REDIRECT_URI");
        }
        if (secretKey == null || secretKey.isBlank()) {
            missing.add("FYERS_API_SECRET_KEY");
        }
        if (!missing.isEmpty()) {
            log.warn("Missing FYERS config keys: {}", String.join(", ", missing));
            throw new com.apex.backend.exception.BadRequestException(
                    "Missing FYERS config: " + String.join(", ", missing));
        }
    }

    private void ensureTokenExchangeConfig() {
        ensureAuthUrlConfig();
    }

    public String generateAuthUrl(String state) {
        ensureAuthUrlConfig();
        return UriComponentsBuilder.fromHttpUrl(AUTH_CODE_URL)
                .queryParam("client_id", appId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("state", state == null ? "" : state)
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }

    public FyersTokens exchangeAuthCodeForToken(String authCode) throws Exception {
        ensureTokenExchangeConfig();
        String appHash = generateAppHash();

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("grant_type", "authorization_code");
        requestBody.addProperty("appIdHash", appHash);
        requestBody.addProperty("code", authCode);
        requestBody.addProperty("redirect_uri", redirectUri);

        // ✅ FIX: Correct order for OkHttp 4.x (String content, MediaType type)
        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(VALIDATE_URL)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();

            if (!response.isSuccessful()) {
                throw new Exception("Fyers API Error (" + response.code() + "): " + responseBody);
            }

            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            if (json.has("access_token")) {
                log.info("✅ Successfully obtained Fyers access token");
                String accessToken = json.get("access_token").getAsString();
                String refreshToken = json.has("refresh_token") ? json.get("refresh_token").getAsString() : null;
                return new FyersTokens(accessToken, refreshToken);
            } else {
                throw new Exception("Failed to obtain Fyers token: " + responseBody);
            }
        }
    }

    public FyersTokens refreshAccessToken(String refreshToken) throws Exception {
        ensureTokenExchangeConfig();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new Exception("Refresh token missing");
        }
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("grant_type", "refresh_token");
        requestBody.addProperty("refresh_token", refreshToken);

        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(refreshUrl)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new Exception("Failed to refresh token: " + responseBody);
            }
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            if (json.has("access_token")) {
                String accessToken = json.get("access_token").getAsString();
                String newRefreshToken = json.has("refresh_token") ? json.get("refresh_token").getAsString() : refreshToken;
                return new FyersTokens(accessToken, newRefreshToken);
            }
            throw new Exception("Invalid refresh response: " + responseBody);
        }
    }

    public FyersProfile getUserProfile(String fyersToken) throws Exception {
        Request request = new Request.Builder()
                .url(PROFILE_URL)
                .header("Authorization", fyersToken)
                .get()
                .build();

        try (Response response = executeWithRetry(request, Math.max(1, maxRetries))) {
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
        storeFyersTokens(userId, new FyersTokens(token, null));
    }

    public void storeFyersTokens(Long userId, FyersTokens tokens) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found for token storage"));
        user.setFyersToken(tokens.accessToken());
        if (tokens.refreshToken() != null && !tokens.refreshToken().isBlank()) {
            user.setFyersRefreshToken(tokens.refreshToken());
        }
        user.setFyersConnected(true);
        userRepository.save(user);
    }

    public String getFyersToken(Long userId) {
        return userRepository.findById(userId)
                .filter(User::getFyersConnected)
                .map(User::getFyersToken)
                .orElse(null);
    }

    public String getFyersRefreshToken(Long userId) {
        return userRepository.findById(userId)
                .filter(User::getFyersConnected)
                .map(User::getFyersRefreshToken)
                .orElse(null);
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

    private Response executeWithRetry(Request request, int attempts) throws IOException {
        int remaining = Math.max(1, attempts);
        IOException last = null;
        while (remaining-- > 0) {
            try {
                return httpClient.newCall(request).execute();
            } catch (IOException ex) {
                last = ex;
                if (remaining <= 0) {
                    break;
                }
                log.warn("FYERS request failed, retrying: {}", ex.getMessage());
                asyncDelayService.awaitMillis(200);
            }
        }
        throw last;
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

    public record FyersTokens(String accessToken, String refreshToken) {}
}
