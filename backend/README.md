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

## Paper vs Live Trading

**Paper mode (default)** uses simulated fills and paper portfolio balances. You can run in paper mode by keeping:

```properties
apex.trading.paper-mode=true
```

**Live mode** requires a linked FYERS account and active OAuth tokens. To enable live mode:

```properties
apex.trading.paper-mode=false
```

When running with the `prod` profile, the backend **never** uses the fallback `FYERS_ACCESS_TOKEN`; it relies on user-linked tokens only.

Run profiles:

```bash
./gradlew bootRunDev   # dev profile
./gradlew bootRunProd  # prod profile
```

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
- `FYERS_REFRESH_URL` (optional; default: `https://api-t1.fyers.in/api/v3/refresh-token`)
- `FYERS_API_BASE_URL` (optional; default: `https://api-t1.fyers.in/api/v3`)
- `FYERS_DATA_BASE_URL` (optional; default: `https://api-t1.fyers.in/data`)

## Migrations

Flyway migrations run automatically on startup. Schema changes should be applied via new migration files in:

```
src/main/resources/db/migration
```

## Backtest + Validation

1. Run a backtest:
   ```bash
   curl -X POST http://localhost:8080/api/backtest/run \
     -H "Authorization: Bearer <token>" \
     -H "Content-Type: application/json" \
     -d '{"symbol":"NSE:RELIANCE-EQ","timeframe":"5","bars":200}'
   ```
2. Validate the stored backtest result:
   ```bash
   curl -X POST http://localhost:8080/api/backtest/validate \
     -H "Authorization: Bearer <token>" \
     -H "Content-Type: application/json" \
     -d '{"backtestResultId":1}'
   ```
