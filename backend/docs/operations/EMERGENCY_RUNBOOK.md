# Emergency Operations Runbook

## Trigger a Global Panic (Hard Kill Switch)
Use this when you need to cancel all open orders, flatten all positions, revoke broker tokens, and halt new trading.

### API Call
```
POST /api/guard/emergency/trigger
X-Admin-Token: <GUARD_ADMIN_TOKEN>
```

Expected response:
* `emergencyMode: true`
* `emergencyReason: "MANUAL_TRIGGER"` (or the provided reason)

## Confirm System State
```
GET /api/guard/state
```
Check:
* `safeMode` or `emergencyMode` = `true`
* `emergencyStartedAt` set

## Recover Safely After an Emergency
1. Verify all positions are flat at the broker and `trades` are CLOSED in the database.
2. Check exit retry queue for unresolved entries (`exit_retry_requests` table).
3. Review DLQ entries for failed exits (`failed_operations` table).
4. Reconnect broker tokens (re-authenticate users).
5. Clear the emergency lock:
```
POST /api/guard/emergency/clear
X-Admin-Token: <GUARD_ADMIN_TOKEN>
```

## Risk Limit Auto-Trigger Behavior
* Daily loss breach triggers the panic button automatically.
* Portfolio heat breaches cancel pending orders and enter safe mode (blocks new trading).
