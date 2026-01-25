# Apex Trading Bot Backend

Apex is a Spring Boot backend for scanning equity universes, generating strategy signals, and managing a paper/live trading workflow with FYERS OAuth + JWT security. It provides a REST API, WebSocket broadcasts, and a structured audit trail for trading activity.

## Features
- **Scanner (on-demand)**: Run scans against a watchlist, ad‑hoc symbols, or configured index universes with detailed diagnostics.
- **Watchlist (DB-backed)**: Default watchlist per user, up to 100 symbols, with CRUD endpoints.
- **Strategy + Signals**: Signal generation, scoring, and signal history storage.
- **Paper trading**: Simulated fills with slippage controls and paper portfolio tracking.
- **Live trading**: FYERS integration for real order placement when configured.
- **Order auditing**: Order audit trail and idempotent order submission.
- **Observability**: Actuator health/metrics, request correlation IDs, structured error responses.
- **OpenAPI/Swagger**: API documentation at `/swagger-ui/index.html`.

## Architecture (high level)
```
UI (React/Angular)
   | REST (JWT) / WS
   v
API Controllers
   |-- Auth (FYERS OAuth + JWT)
   |-- Scanner (on-demand)
   |-- Watchlist
   |-- Orders & Trades
   v
Services
   |-- WatchlistService -> PostgreSQL
   |-- ScannerRunService -> ManualScanService -> Strategy Pipeline
   |-- OrderExecutionService -> Paper/FYERS
   v
Postgres + Flyway
```

## Quickstart (local)
**Prereqs:** Java 21 (Gradle toolchain will request a JDK 21 install if missing).
1. Start Postgres (optional if you already have one running):
   ```bash
   docker compose up -d
   ```
2. Run the backend:
   ```bash
   ./gradlew bootRun
   ```

The API runs on `http://localhost:8080` by default.

## Quickstart (Docker)
```bash
docker build -t apex-backend .
docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/apex_trading_db \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  -e JWT_SECRET=change-me \
  apex-backend
```

## Environment Variables (single source of truth)
> **No hardcoding policy**: base URLs, secrets, CORS origins, scanner universes, and time windows must be supplied via configuration or DB.

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `SPRING_DATASOURCE_URL` | ✅ | — | JDBC URL for Postgres |
| `SPRING_DATASOURCE_USERNAME` | ✅ | — | DB username |
| `SPRING_DATASOURCE_PASSWORD` | ✅ | — | DB password |
| `JWT_SECRET` | ✅ | — | JWT signing secret |
| `PORT` | ❌ | `8080` | Server port |
| `APEX_ALLOWED_ORIGINS` | ❌ | (empty) | Comma‑separated CORS origins |
| `APEX_PUBLIC_HEALTH_ENDPOINT` | ❌ | `true` | Make `/actuator/health` public |
| `FYERS_API_APP_ID` | ✅ (live) | — | FYERS app id |
| `FYERS_API_SECRET_KEY` | ✅ (live) | — | FYERS secret |
| `FYERS_REDIRECT_URI` | ❌ | `http://localhost:4200/auth/fyers/callback` | FYERS OAuth redirect |
| `FYERS_ACCESS_TOKEN` | ❌ | (empty) | Fallback token for system market data |
| `FYERS_API_BASE_URL` | ❌ | `https://api-t1.fyers.in/api/v3` | FYERS API base URL |
| `FYERS_DATA_BASE_URL` | ❌ | `https://api-t1.fyers.in/data` | FYERS data base URL |
| `APEX_SCANNER_ENABLED` | ❌ | `true` | Enable manual scanner |
| `APEX_SCANNER_NIFTY50` | ❌ | (empty) | Comma‑separated NIFTY50 symbols |
| `APEX_SCANNER_NIFTY200` | ❌ | (empty) | Comma‑separated NIFTY200 symbols |
| `APEX_SCANNER_DEFAULT_TIMEFRAME` | ❌ | `5` | Default timeframe for scans |
| `APEX_SCANNER_DEFAULT_REGIME` | ❌ | `AUTO` | Default market regime |
| `APEX_SCANNER_MARKET_OPEN` | ❌ | `09:15` | Market open window for scheduler |
| `APEX_SCANNER_MARKET_CLOSE` | ❌ | `15:30` | Market close window for scheduler |
| `APEX_SCANNER_SCHEDULER_ENABLED` | ❌ | `false` | Enable scheduler |

### Render + local required variables
**Render (production)** should set at minimum:
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET`
- `FYERS_API_APP_ID`
- `FYERS_API_SECRET_KEY`
- `FYERS_ACCESS_TOKEN` (for market data when OAuth tokens are not present)
- `APEX_SCANNER_ENABLED` (keep `true` for manual scans)
- `APEX_SCANNER_SCHEDULER_ENABLED=false` (explicitly disable background scans)

**Local development** minimum:
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET`
- `APEX_SCANNER_ENABLED=true`

## Auth flow (FYERS OAuth + JWT)
1. UI calls `GET /api/auth/fyers/auth-url` to get the FYERS login URL.
2. FYERS redirects to `FYERS_REDIRECT_URI`, UI posts callback to `POST /api/auth/fyers/callback`.
3. Backend returns JWT access/refresh tokens to the UI.

Public endpoints (no JWT):
- `GET /api/ui/config`
- `GET /api/auth/fyers/auth-url`
- `POST /api/auth/fyers/callback`
- `GET /actuator/health` (configurable)

All other `/api/**` endpoints require Bearer JWT.

## Watchlist management (max 100 symbols)
- `GET /api/watchlist`
- `POST /api/watchlist/items` (batch add)
- `DELETE /api/watchlist/items/{symbol}`
- `PUT /api/watchlist` (replace all)

Symbols are uppercased and validated (`A-Z0-9:._-`).

## Manual-only scanning (required)
The system scans **only** when the user clicks **Scan** (manual trigger). No background scans, startup scans, or scheduler-driven runs are allowed by default. Scanner runs are created in `PENDING` and move to `RUNNING` only when the API-triggered executor starts. Diagnostics always populate even when there are zero final signals or an empty universe.

## On-demand scanner
Run the scanner manually when requested:
```bash
curl -X POST http://localhost:8080/api/scanner/run \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"universeType":"WATCHLIST","strategyId":1,"dryRun":true,"mode":"PAPER"}'
```
Check status:
```bash
curl -H "Authorization: Bearer <token>" \
  http://localhost:8080/api/scanner/runs/{runId}
```
Fetch results:
```bash
curl -H "Authorization: Bearer <token>" \
  http://localhost:8080/api/scanner/runs/{runId}/results
```

If you omit `strategyId`, the backend falls back to the user's default watchlist (`watchlist_items`).

## Scanner Verification
### Run a scan + poll status (bash/macOS/Linux)
```bash
curl -X POST http://localhost:8080/api/scanner/run \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"universeType":"WATCHLIST","strategyId":1,"dryRun":true,"mode":"PAPER"}'

curl -H "Authorization: Bearer <token>" \
  http://localhost:8080/api/scanner/runs/{runId}

curl -H "Authorization: Bearer <token>" \
  http://localhost:8080/api/scanner/runs/{runId}/results
```

### Run a scan + poll status (Windows cmd)
```cmd
curl -X POST http://localhost:8080/api/scanner/run ^
  -H "Authorization: Bearer <token>" ^
  -H "Content-Type: application/json" ^
  -d "{\"universeType\":\"WATCHLIST\",\"strategyId\":1,\"dryRun\":true,\"mode\":\"PAPER\"}"

curl -H "Authorization: Bearer <token>" ^
  http://localhost:8080/api/scanner/runs/{runId}

curl -H "Authorization: Bearer <token>" ^
  http://localhost:8080/api/scanner/runs/{runId}/results
```

### Database checks (psql)
```sql
select id, status, error_message, created_at, started_at, completed_at,
       total_symbols, passed_stage1, passed_stage2, final_signals
from scanner_runs
order by id desc
limit 5;

select id, strategy_id, symbol, active
from watchlist_stocks
order by id desc
limit 10;

select run_id, symbol, score, grade
from scanner_run_results
order by run_id desc, score desc
limit 10;
```

### Notes
- Scanner runs remain in `PENDING` until the manual API trigger executes.
- Diagnostics are always returned; empty universes populate `rejectedStage1ReasonCounts` with `EMPTY_UNIVERSE`, and counts are zeroed when no signals are produced.

### Required environment variables
Minimum for scanner verification:
- `APEX_SCANNER_ENABLED` (set `true` to allow runs)
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET`
- `FYERS_API_APP_ID`
- `FYERS_API_SECRET_KEY`
- `FYERS_ACCESS_TOKEN` (needed for market data if you are not using OAuth)

Scanning happens **only on-demand** when `/api/scanner/run` is called—there is no continuous background scanning unless the scheduler is explicitly enabled.

## Scanner lifecycle + diagnostics
Scanner runs always transition through:

`PENDING → RUNNING → COMPLETED/FAILED`

Diagnostics are always persisted, even when `total_symbols = 0` or `final_signals = 0`:
- `total_symbols`: total instruments scanned.
- `passed_stage1`: passed trend/volume/breakout stage.
- `passed_stage2`: passed momentum/ADX/RSI/ATR/squeeze stage.
- `final_signals`: count of tradable signals.
- `rejected_stage*_reason_counts`: reasons for rejections, e.g.:
  - `DATA_MISSING`
  - `LIQUIDITY_FILTER`
  - `ADX_FILTER`
  - `RSI_FILTER`
  - `MACD_FILTER`
  - `VOLATILITY_FILTER`

## Instruments master (symbol normalization)
Market data requests are normalized through the `instruments` table. The backend resolves a user symbol (e.g., `INFY`) to its trading symbol (e.g., `NSE:INFY-EQ`) before calling FYERS. Missing instruments are logged, and the request is skipped or rejected.

## Verification queries (DB-only health)
Recent runs and diagnostics:
```sql
select id, status, total_symbols, passed_stage1,
       passed_stage2, final_signals,
       rejected_stage1_reason_counts
from scanner_runs
order by id desc
limit 3;
```

Manual-only verification:
```sql
select max(id) from scanner_runs;
```
- Refresh UI → ID must NOT change
- Click Scan → ID must increase

Verify WATCHLIST universe symbols (strategy-scoped):
```sql
select strategy_id, symbol, active
from watchlist_stocks
where active = true
order by strategy_id, symbol;
```

## Common failure reasons checklist
- **DATA_MISSING**: market data returned no candles for the symbol.
- **LIQUIDITY_FILTER**: liquidity gate rejected the symbol (low volume/spread).
- **ADX_FILTER**: trend strength below configured ADX threshold.
- **RSI_FILTER**: RSI outside configured goldilocks range.
- **MACD_FILTER**: momentum score or crossover invalid.
- **VOLATILITY_FILTER**: ATR% outside configured bounds.

## OpenAPI / Swagger
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI spec: `http://localhost:8080/v3/api-docs`

## Troubleshooting
- **401/403**: Ensure you pass `Authorization: Bearer <JWT>` and that the JWT secret matches the server config.
- **CORS blocked**: Set `APEX_ALLOWED_ORIGINS` and restart the server.
- **Empty scan results**: Check diagnostics in `/api/scanner/runs/{runId}` and verify your universe/watchlist is populated.
- **Market closed**: Scheduler will skip scans outside `APEX_SCANNER_MARKET_OPEN/CLOSE`.

## Docs
- API details: `docs/API.md`
- Architecture: `docs/ARCHITECTURE.md`
- Configuration: `docs/CONFIGURATION.md`
