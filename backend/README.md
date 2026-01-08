# Apex Trading Bot Backend

## Requirements
- Java 21
- Docker (optional, for Postgres)

## Local Development

1. Start Postgres (optional):
   ```bash
   docker compose up -d
   ```
2. Run the backend:
   ```bash
   ./gradlew bootRun
   ```

The application listens on `http://localhost:8080` by default.

## Configuration

Set environment variables to override defaults:

### Database
- `DB_HOST` (default: `localhost`)
- `DB_PORT` (default: `5433`)
- `DB_NAME` (default: `apex_trading_db`)
- `DB_USER` (default: `postgres`)
- `DB_PASSWORD` (default: `postgres`)

### JWT
- `JWT_SECRET` (required)

### Fyers
- `FYERS_API_APP_ID` (required for Fyers integration)
- `FYERS_API_SECRET_KEY` (required for Fyers integration)
- `FYERS_REDIRECT_URI` (default: `http://localhost:4200/auth/fyers/callback`)
- `FYERS_ACCESS_TOKEN` (optional; used for system-level market data calls when a user token is not available)

## Migrations

Flyway migrations run automatically on startup. Schema changes should be applied via new migration files in:

```
src/main/resources/db/migration
```
