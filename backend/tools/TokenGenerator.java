package com.apex.backend.tools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.awt.Desktop;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.Scanner;

public class TokenGenerator {

    public static void main(String[] args) throws Exception {
        String appId = requiredEnv("FYERS_API_APP_ID");
        String secretKey = requiredEnv("FYERS_API_SECRET_KEY");
        String redirectUri = requiredEnv("FYERS_REDIRECT_URI");

        System.out.println("🚀 GENERATING NEW TOKEN...");

        String authUrl = "https://api-t1.fyers.in/api/v3/generate-authcode?" +
                "client_id=" + appId + "&redirect_uri=" + redirectUri + "&response_type=code&state=sample";

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(new URI(authUrl));
        }
        System.out.println("1. Login here: " + authUrl);

        Scanner scanner = new Scanner(System.in);
        System.out.print("\n👉 PASTE AUTH CODE: ");
        String authCode = scanner.nextLine().trim();

        String appHash = sha256(appId + ":" + secretKey);
        OkHttpClient client = new OkHttpClient();
        Gson gson = new Gson();

        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("grant_type", "authorization_code");
        jsonBody.addProperty("appIdHash", appHash);
        jsonBody.addProperty("code", authCode);

        Request request = new Request.Builder()
                .url("https://api-t1.fyers.in/api/v3/validate-authcode")
                .post(RequestBody.create(jsonBody.toString(), MediaType.get("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String resStr = response.body() != null ? response.body().string() : "";
            JsonObject json = gson.fromJson(resStr, JsonObject.class);

            if (json != null && json.has("access_token")) {
                String token = json.get("access_token").getAsString();
                System.out.println("\n✅ SUCCESS! Copy the token below and set FYERS_ACCESS_TOKEN:");
                System.out.println(token);
            } else {
                System.out.println("❌ ERROR: " + resStr);
            }
        }
    }

    private static String requiredEnv(String key) {
        return Optional.ofNullable(System.getenv(key))
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException("Missing required environment variable: " + key));
    }

    private static String sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
