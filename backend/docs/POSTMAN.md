# Postman (No-UI) Collection

This backend ships with a Postman collection so you can exercise the API without building or running the UI.

## Files
- Collection: `docs/postman/Apex-Trading-Bot.postman_collection.json`

## Quickstart (Newman CLI)
1. Install Newman (once):
   ```bash
   npm install -g newman
   ```
2. Run the collection with inline variables:
   ```bash
   newman run docs/postman/Apex-Trading-Bot.postman_collection.json \
     --env-var baseUrl=http://localhost:8080 \
     --env-var authCode=<FYERS_AUTH_CODE> \
     --env-var state=<FYERS_STATE>
   ```

> The collection stores `accessToken`, `refreshToken`, and `runId` as collection variables after the relevant requests run.

## How to use in Postman (optional UI)
1. Import the collection JSON.
2. Set `baseUrl` if your server is not on `http://localhost:8080`.
3. Use **Register** or **Login** to populate `accessToken`/`refreshToken` variables.
4. Call **Run Scan (Watchlist)** → it stores `runId` for the status/results requests.

## FYERS OAuth flow notes
- Use **Get FYERS Auth URL** to obtain the login URL and `state` value.
- After completing the FYERS login, copy the `auth_code` value into the `authCode` variable.
- Run **FYERS Callback (POST)** to exchange the auth code for tokens.

## Tips
- Watchlist endpoints require a valid JWT (`accessToken`).
- Scanner endpoints require `accessToken` and an existing watchlist if you use `WATCHLIST`.
