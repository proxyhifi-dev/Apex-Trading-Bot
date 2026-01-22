# API Guide

Base URL: `http://localhost:8080`

## Authentication
- **Public endpoints**: `/api/ui/config`, `/api/auth/fyers/auth-url`, `/api/auth/fyers/callback`, `/actuator/health` (configurable)
- **Protected**: all other `/api/**` endpoints require `Authorization: Bearer <JWT>`

## Watchlist
### Get watchlist
```bash
curl -H "Authorization: Bearer <token>" \
  http://localhost:8080/api/watchlist
```

### Add symbols (batch)
```bash
curl -X POST http://localhost:8080/api/watchlist/items \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"symbols":["NSE:RELIANCE-EQ","NSE:TCS-EQ"]}'
```

### Remove symbol
```bash
curl -X DELETE http://localhost:8080/api/watchlist/items/NSE:RELIANCE-EQ \
  -H "Authorization: Bearer <token>"
```

### Replace watchlist
```bash
curl -X PUT http://localhost:8080/api/watchlist \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"symbols":["NSE:INFY-EQ","NSE:HDFCBANK-EQ"]}'
```

## Scanner (on-demand)
### Run scan
```bash
curl -X POST http://localhost:8080/api/scanner/run \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: run-123" \
  -d '{"universeType":"WATCHLIST","dryRun":true,"mode":"PAPER"}'
```

### Run scan for ad-hoc symbols
```bash
curl -X POST http://localhost:8080/api/scanner/run \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"universeType":"SYMBOLS","symbols":["NSE:RELIANCE-EQ"],"dryRun":true}'
```

### Get run status
```bash
curl -H "Authorization: Bearer <token>" \
  http://localhost:8080/api/scanner/runs/{runId}
```

### Get run results
```bash
curl -H "Authorization: Bearer <token>" \
  http://localhost:8080/api/scanner/runs/{runId}/results
```

### Cancel run
```bash
curl -X POST http://localhost:8080/api/scanner/runs/{runId}/cancel \
  -H "Authorization: Bearer <token>"
```

## Orders (existing)
- `POST /api/orders` (place)
- `PUT /api/orders/{orderId}` (modify)
- `DELETE /api/orders/{orderId}` (cancel)
- `POST /api/positions/{symbol}/close`
- `POST /api/orders/validate`

## Errors
All errors use a consistent structure:
```json
{
  "timestamp": "2025-01-01T00:00:00Z",
  "status": 401,
  "error": "UNAUTHORIZED",
  "message": "JWT missing/expired",
  "requestId": "...",
  "correlationId": "..."
}
```
