# Architecture

## Modules & Packages
- `controller`: REST endpoints (scanner, watchlist, auth, orders, signals, etc.)
- `service`: Business logic (scanner, watchlist, trading, FYERS integration)
- `model`: JPA entities for persistence (orders, runs, watchlists, audits)
- `repository`: Spring Data repositories
- `security`: JWT handling and auth filters
- `config`: configuration properties and filters

## Data Flow (high level)
```
UI
  -> /api/ui/config
  -> /api/auth/** (FYERS OAuth)
  -> /api/watchlist (store symbols)
  -> /api/scanner/run (on-demand scan)

Scanner flow:
UI -> ScannerRunController -> ScannerRunService
   -> ManualScanService -> TradeDecisionPipelineService
   -> StockScreeningService (signals) / TradeExecutionService (auto trade)
   -> scanner_runs + scanner_run_results

Scanner mode:
- Manual-only by default (no startup or scheduled scans).
- Scheduler requires `apex.scanner.scheduler-enabled=true` and `apex.scanner.mode=SCHEDULED`.

Order flow:
UI -> OrderExecutionController -> OrderExecutionService
   -> PaperOrderExecutionService or FyersService
   -> OrderAudit + OrderIntent updates
```

## Storage
- **Postgres** via Flyway migrations
- Core tables: `watchlists`, `watchlist_items`, `scanner_runs`, `scanner_run_results`, `order_intents`, `order_audit`
- Instruments master: `instruments` stores symbol → trading symbol normalization for FYERS requests

## Observability
- Request correlation IDs: `X-Request-Id`, `X-Correlation-Id`
- Actuator endpoints: `/actuator/health`, `/actuator/metrics`
