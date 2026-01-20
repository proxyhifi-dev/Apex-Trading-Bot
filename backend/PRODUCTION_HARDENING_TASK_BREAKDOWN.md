# Production Hardening Task Breakdown & Estimates

This document tracks the implementation plan for the Apex Trading Bot backend production hardening work. Estimates assume one engineer with domain familiarity and exclude external approvals/deployments.

## Summary Estimates
- **Total P0 effort**: ~6.5–9.5 engineering days
- **Total P1 effort**: ~2.5–4.0 engineering days
- **Config consolidation + rollout + verification**: ~2.0–3.5 engineering days

> **Note**: Estimates include implementation + local validation but exclude long‑running paper trading soak time.

---

## P0 Tasks (Must Complete Before Production)

### 1) Order Lifecycle State Machine
**Scope**: OrderState enum, OrderIntent migration, ExecutionEngine state transitions, state validation.
- Design + implementation: **1.0–1.5 days**
- DB migration + backfill: **0.5 day**
- Unit tests + regression checks: **0.5–1.0 day**

**Estimate**: **2.0–3.0 days**

### 2) Partial Fill Policy
**Scope**: Configurable partial fill behavior, cancel logic, broker cancel API.
- Implementation: **0.5–1.0 day**
- Policy validation tests: **0.5 day**

**Estimate**: **1.0–1.5 days**

### 3) Order Timeouts + Cancel
**Scope**: Entry timeout handling, cancel on timeout, stop‑loss ack timeout.
- Implementation: **0.5–0.75 day**
- Tests: **0.5 day**

**Estimate**: **1.0–1.25 days**

### 4) Protective Stop Guarantee
**Scope**: Guaranteed stop placement post‑fill; flatten on stop failure.
- Implementation: **0.75–1.0 day**
- Failure path tests + alerts: **0.5–0.75 day**

**Estimate**: **1.25–1.75 days**

### 5) Startup + Periodic Reconciliation
**Scope**: Ghost/zombie detection, safe mode toggle, WS events.
- Implementation: **0.75–1.0 day**
- Tests: **0.5–0.75 day**

**Estimate**: **1.25–1.75 days**

### 6) Risk Enforcement as Source of Truth
**Scope**: Structured reject codes, threshold/current values, WS reject events.
- Implementation: **0.5–0.75 day**
- Tests: **0.5 day**

**Estimate**: **1.0–1.25 days**

---

## P1 Tasks (Next Priority)

### 7) Crisis Mode
**Scope**: Market shock detection, global halt, auto‑resume.
- Implementation: **0.5–0.75 day**
- Tests + simulation: **0.5 day**

**Estimate**: **1.0–1.25 days**

### 8) Data Quality Guards
**Scope**: Stale/gap/outlier detection, structured result.
- Implementation: **0.25–0.5 day**
- Tests: **0.25 day**

**Estimate**: **0.5–0.75 day**

---

## Required Refactor

### 9) Config Consolidation
**Scope**: Migrate all properties into `application.yml`, add profile overrides.
- Migration: **0.5–0.75 day**
- Validation pass: **0.5 day**

**Estimate**: **1.0–1.25 days**

---

## Testing Plan (High‑Value)
- Order state transition tests
- Partial fill policy tests
- Timeout + cancel tests
- Stop placement failure tests
- Reconciliation mismatch tests
- Risk rejection detail tests

**Estimate**: **1.0–1.5 days**

---

## Rollout & Verification
- Paper trading soak (1 week) + daily log review
- Production metrics + alerting verification
- Go/No‑Go checklist execution

**Estimate**: **1.0–1.5 days**
