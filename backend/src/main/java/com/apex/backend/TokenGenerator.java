package com.apex.backend;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import java.awt.Desktop;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Scanner;

public class TokenGenerator {

    // ✅ VERIFY YOUR KEYS
    private static final String APP_ID = "0LJ7WSSBAY-100";
    private static final String SECRET_ID = "R3PAS21VYM";
    private static final String REDIRECT_URI = "https://trade.fyers.in/api-login/redirect-uri/index.html";

    public static void main(String[] args) throws Exception {
        System.out.println("🚀 GENERATING NEW TOKEN...");

        String authUrl = "https://api-t1.fyers.in/api/v3/generate-authcode?" +
                "client_id=" + APP_ID + "&redirect_uri=" + REDIRECT_URI + "&response_type=code&state=sample";

        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(new URI(authUrl));
        System.out.println("1. Login here: " + authUrl);

        Scanner scanner = new Scanner(System.in);
        System.out.print("\n👉 PASTE AUTH CODE: ");
        String authCode = scanner.nextLine().trim();

        String appHash = sha256(APP_ID + ":" + SECRET_ID);
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
            String resStr = response.body().string();
            JsonObject json = gson.fromJson(resStr, JsonObject.class);

            if (json.has("access_token")) {
                String token = json.get("access_token").getAsString();
                System.out.println("\n✅ SUCCESS! Copy the token below and set FYERS_ACCESS_TOKEN:");
                System.out.println(token);
            } else {
                System.out.println("❌ ERROR: " + resStr);
            }
        }
    }

    private static String sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }
}
