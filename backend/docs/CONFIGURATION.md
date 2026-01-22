# Configuration Reference

## Application properties
Key properties from `application.yml`:

### Server
- `server.port` (default `8080`)

### Database
- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`

### JWT
- `jwt.secret` (required)
- `jwt.expiration` (ms)
- `jwt.refresh-expiration` (ms)

### FYERS
- `fyers.api.app-id`
- `fyers.api.secret-key`
- `fyers.api.base-url`
- `fyers.data.base-url`
- `fyers.redirect-uri`

### Scanner
- `apex.scanner.enabled` (default `false`)
- `apex.scanner.scheduler-enabled` (default `false`)
- `apex.scanner.mode` (`MANUAL` or `SCHEDULED`)
- `apex.scanner.default-timeframe` (default `5`)
- `apex.scanner.default-regime` (default `AUTO`)
- `apex.scanner.market-open` / `apex.scanner.market-close`
- `apex.scanner.universes.nifty50` / `nifty200`

### CORS
- `apex.security.cors.allowed-origins`
- `apex.security.public-health-endpoint`

## Environment profiles
- **local/dev**: uses defaults, allows local CORS
- **prod**: set `APEX_ALLOWED_ORIGINS`, FYERS credentials, and DB secrets

## Docker
Ensure DB settings are injected via environment variables or `docker-compose.yml`.
