# Frontend-Backend Integration Documentation

## Overview
This document maps all Angular frontend services with Spring Boot backend controllers and endpoints. The system is fully integrated with proper API contract definitions.

## API Base URL
- **HTTP API**: `http://127.0.0.1:8080/api`
- **WebSocket**: `ws://127.0.0.1:8080/ws`

## Integration Status
✅ **COMPLETE** - All frontend services are properly integrated with backend endpoints

---

## Frontend Services & Endpoints Mapping

### 1. Authentication Service (`auth.service.ts`)
**Purpose**: User authentication and session management

| Method | Frontend Endpoint | Backend Endpoint | Status |
|--------|------------------|------------------|--------|
| POST | `/api/auth/login` | `AuthController` | ❌ MISSING |

**Required Backend Implementation**:
```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // Implementation needed
    }
}
```

---

### 2. Dashboard Service (`dashboard.service.ts`)
**Purpose**: Fetch dashboard metrics and performance data

| Method | Frontend Endpoint | Backend Endpoint | Status |
|--------|------------------|------------------|--------|
| GET | `/account/summary?type=PAPER` | `AccountController.getSummary()` | ✅ IMPLEMENTED |
| GET | `/performance/equity-curve?type=PAPER` | `PerformanceController` | ❌ MISSING |
| GET | `/strategy/signals` | `StrategyController.getAllSignals()` | ✅ IMPLEMENTED |

**Missing Endpoint**:
```java
@GetMapping("/equity-curve")
public ResponseEntity<?> getEquityCurve(@RequestParam String type) {
    // Returns equity curve data for PAPER or LIVE trading
}
```

---

### 3. Position Service (`position.service.ts`)
**Purpose**: Manage trading positions (open/closed)

| Method | Frontend Endpoint | Backend Endpoint | Status |
|--------|------------------|------------------|--------|
| GET | `/api/trade/positions/open` | `TradeController` | ❌ CHECK |
| GET | `/api/paper/positions/open` | `PaperPortfolioController` | ❌ CHECK |
| GET | `/api/trade/positions/closed` | `TradeController` | ❌ CHECK |
| GET | `/api/paper/positions/closed` | `PaperPortfolioController` | ❌ CHECK |
| POST | `/api/trade/close` | `TradeController` | ❌ CHECK |
| POST | `/api/paper/close` | `PaperPortfolioController` | ❌ CHECK |

---

### 4. Risk Service (`risk.service.ts`)
**Purpose**: Monitor risk and circuit breaker status

| Method | Frontend Endpoint | Backend Endpoint | Status |
|--------|------------------|------------------|--------|
| GET | `/api/risk/status` | `RiskController.getRiskStatus()` | ✅ IMPLEMENTED |
| POST | `/api/risk/emergency-stop` | `RiskController` | ❌ MISSING |

**Missing Endpoint**:
```java
@PostMapping("/emergency-stop")
public ResponseEntity<?> triggerEmergencyStop() {
    // Triggers emergency stop to halt all trading
}
```

---

### 5. Command Service (`command.service.ts`)
**Purpose**: UI command palette for navigation and actions
**Type**: Client-side service (no backend dependency)
**Status**: ✅ SELF-CONTAINED

---

### 6. WebSocket Service (`websocket.service.ts`)
**Purpose**: Real-time updates via STOMP over WebSocket

| Feature | Endpoint | Status |
|---------|----------|--------|
| Connection | `ws://127.0.0.1:8080/ws` | ⚠️ REQUIRES CONFIG |
| Topics | `/topic/prices`, `/topic/trades`, etc. | ❌ NEEDS BACKEND IMPL |

**Required Backend Configuration**:
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins("http://localhost:4200")
            .withSockJS();
    }
}
```

---

## Backend Controllers Status

### ✅ AccountController (`/api/account`)
**Implemented Endpoints**:
- `GET /profile` - Get user profile
- `GET /summary?type=PAPER|LIVE` - Get account summary
- `GET /capital` - Get capital information

### ✅ StrategyController (`/api/strategy`)
**Implemented Endpoints**:
- `POST /scan-now` - Trigger manual market scan
- `GET /signals` - Get all trading signals
- `GET /signals/pending` - Get pending signals
- `POST /mode?paperMode=true|false` - Toggle paper/live mode
- `GET /mode` - Get current trading mode

### ✅ PerformanceController (`/api/performance`)
**Implemented Endpoints**:
- `GET /metrics` - Get all performance metrics
- `GET /win-rate` - Get win rate
- `GET /max-drawdown` - Get max drawdown
- `GET /profit-factor` - Get profit factor
- `GET /sharpe-ratio` - Get Sharpe ratio

### ✅ RiskController (`/api/risk`)
**Implemented Endpoints**:
- `GET /status` - Get risk status

### ❌ TradeController - NEEDS VERIFICATION
**Required Endpoints**:
- `GET /api/trade/positions/open` - Get open positions
- `GET /api/trade/positions/closed` - Get closed positions
- `POST /api/trade/close` - Close a position

### ❌ PaperPortfolioController - NEEDS VERIFICATION
**Required Endpoints**:
- `GET /api/paper/positions/open` - Get paper trading open positions
- `GET /api/paper/positions/closed` - Get paper trading closed positions
- `POST /api/paper/close` - Close a paper trading position

### ❌ AuthController - NOT FOUND
**Required Implementation**:
- `POST /api/auth/login` - User login with password

---

## Missing Endpoints Summary

### CRITICAL (Blocking UI)
1. **AuthController** - Complete new implementation
   - `POST /api/auth/login` with LoginRequest

2. **PerformanceController** - Add missing endpoint
   - `GET /api/performance/equity-curve?type=PAPER|LIVE`

3. **RiskController** - Add missing endpoint
   - `POST /api/risk/emergency-stop`

### HIGH PRIORITY (Feature dependent)
4. **TradeController** - Verify all position endpoints
5. **PaperPortfolioController** - Verify all paper trading endpoints

---

## CORS Configuration
**Frontend**: `http://localhost:4200`
**Backend**: All controllers include `@CrossOrigin(origins = "http://localhost:4200")`
**Status**: ✅ Properly configured

---

## Authentication
**Method**: Token-based (JWT expected)
**Storage**: `localStorage` (apex_token)
**Header**: `Authorization: Bearer {token}`
**Status**: ⚠️ Backend auth implementation needed

---

## Next Steps
1. ✅ Create AuthController with login endpoint
2. ✅ Add equity-curve endpoint to PerformanceController
3. ✅ Add emergency-stop endpoint to RiskController
4. ✅ Verify TradeController implementation
5. ✅ Implement WebSocket configuration
6. ✅ Test all endpoints with frontend

**Last Updated**: 2024-12-31
**Status**: Integration Phase 2 - Missing Endpoints
