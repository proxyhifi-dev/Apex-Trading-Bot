"""FYERS API v3 adapter with automated TOTP authentication.

This module provides a production-ready integration layer for FYERS API v3,
including secure token persistence, token validation, automated TOTP-based login,
retry logic with exponential backoff, and convenience methods for common trading
operations.

Required environment variables:
    - FYERS_CLIENT_ID
    - FYERS_SECRET_KEY
    - FYERS_REDIRECT_URI
    - FYERS_USERNAME
    - FYERS_PIN
    - FYERS_TOTP_KEY

Dependencies:
    pip install requests pyotp fyers-apiv3

Example:
    from execution.fyers_adapter import FyersAdapter

    adapter = FyersAdapter()
    profile = adapter.get_profile()
    ltp = adapter.get_ltp("NSE:SBIN-EQ")
"""

from __future__ import annotations

import base64
import json
import logging
import os
import stat
import threading
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Optional
from urllib.parse import parse_qs, urlparse

import pyotp
import requests
from fyers_apiv3 import fyersModel
from requests import Response

RETRYABLE_STATUS_CODES = {429, 500, 502, 503, 504}
BACKOFF_SCHEDULE_SECONDS = [0.5, 1, 2, 4, 8]
MAX_RETRIES = 5
LOGIN_FAIL_THRESHOLD = 3
LOGIN_BLOCK_SECONDS = 60

LOGGER = logging.getLogger(__name__)


class FyersAdapterError(Exception):
    """Base exception for adapter errors."""


class FyersAuthenticationError(FyersAdapterError):
    """Raised when FYERS authentication fails."""


class FyersHttpError(FyersAdapterError):
    """Raised when an FYERS HTTP request fails."""


@dataclass(frozen=True)
class FyersCredentials:
    """Container for FYERS auth and API credentials loaded from environment."""

    client_id: str
    secret_key: str
    redirect_uri: str
    username: str
    pin: str
    totp_key: str


class FyersAdapter:
    """FYERS API v3 integration adapter with auto-auth and resiliency controls."""

    LOGIN_BASE_URL = "https://api-t2.fyers.in/vagator/v2"
    API_BASE_URL = "https://api.fyers.in"
    DATA_BASE_URL = "https://api-t1.fyers.in"

    def __init__(self, token_path: str = ".secrets/fyers_token.json", timeout: int = 15) -> None:
        self._credentials = self._load_credentials()
        self._timeout = timeout
        self._token_path = Path(token_path)
        self._session = requests.Session()

        self._circuit_lock = threading.Lock()
        self._consecutive_login_failures = 0
        self._login_blocked_until = 0.0

        self._access_token: Optional[str] = self._load_saved_token()
        if not self._is_token_valid(self._access_token):
            self._log_event("token.invalid_or_missing", reason="startup_validation_failed")
            self._access_token = self._auto_login()
            self._save_token(self._access_token)

    def get_profile(self) -> Dict[str, Any]:
        """Fetch account profile from /api/v3/profile."""
        return self._api_request("GET", f"{self.API_BASE_URL}/api/v3/profile")

    def get_ltp(self, symbol: str) -> Dict[str, Any]:
        """Fetch last traded price for a symbol via /data/quotes."""
        params = {"symbols": symbol}
        return self._api_request("GET", f"{self.DATA_BASE_URL}/data/quotes", params=params)

    def get_history(
        self,
        symbol: str,
        resolution: str,
        range_from: str,
        range_to: str,
    ) -> Dict[str, Any]:
        """Fetch historical data via /data/history.

        Args:
            symbol: Exchange-qualified symbol, e.g. NSE:SBIN-EQ.
            resolution: Candle resolution (e.g. 1, 5, 15, D).
            range_from: Start date/time as required by FYERS API.
            range_to: End date/time as required by FYERS API.
        """
        params = {
            "symbol": symbol,
            "resolution": resolution,
            "date_format": "1",
            "range_from": range_from,
            "range_to": range_to,
            "cont_flag": "1",
        }
        return self._api_request("GET", f"{self.DATA_BASE_URL}/data/history", params=params)

    def place_order(self, order: Dict[str, Any]) -> Dict[str, Any]:
        """Place an order via /api/v3/orders."""
        return self._api_request("POST", f"{self.API_BASE_URL}/api/v3/orders", json_data=order)

    def get_positions(self) -> Dict[str, Any]:
        """Fetch open/closed positions via /api/v3/positions."""
        return self._api_request("GET", f"{self.API_BASE_URL}/api/v3/positions")

    def _load_credentials(self) -> FyersCredentials:
        env_map = {
            "client_id": "FYERS_CLIENT_ID",
            "secret_key": "FYERS_SECRET_KEY",
            "redirect_uri": "FYERS_REDIRECT_URI",
            "username": "FYERS_USERNAME",
            "pin": "FYERS_PIN",
            "totp_key": "FYERS_TOTP_KEY",
        }
        values: Dict[str, str] = {}
        missing = []

        for key, env_var in env_map.items():
            value = os.getenv(env_var, "").strip()
            if not value:
                missing.append(env_var)
            else:
                values[key] = value

        if missing:
            raise FyersAdapterError(
                "Missing required environment variables: " + ", ".join(sorted(missing))
            )

        if not values["pin"].isdigit() or len(values["pin"]) != 4:
            raise FyersAdapterError("FYERS_PIN must be a 4-digit numeric value")

        return FyersCredentials(**values)

    def _load_saved_token(self) -> Optional[str]:
        if not self._token_path.exists():
            self._log_event("token.file_missing", path=str(self._token_path))
            return None

        try:
            payload = json.loads(self._token_path.read_text(encoding="utf-8"))
            token = payload.get("access_token")
            if isinstance(token, str) and token.strip():
                self._log_event("token.file_loaded", path=str(self._token_path))
                return token.strip()
        except (json.JSONDecodeError, OSError) as exc:
            self._log_event("token.file_load_failed", error=str(exc), path=str(self._token_path))

        return None

    def _save_token(self, access_token: str) -> None:
        self._token_path.parent.mkdir(parents=True, exist_ok=True)
        payload = {"access_token": access_token, "updated_at": int(time.time())}
        self._token_path.write_text(json.dumps(payload, indent=2), encoding="utf-8")
        os.chmod(self._token_path, stat.S_IRUSR | stat.S_IWUSR)
        self._log_event("token.saved", path=str(self._token_path), mode="600")

    def _is_token_valid(self, token: Optional[str]) -> bool:
        if not token:
            return False

        headers = {"Authorization": f"{self._credentials.client_id}:{token}"}
        try:
            response = self._retry_request(
                method="GET",
                url=f"{self.API_BASE_URL}/api/v3/profile",
                headers=headers,
                auth_required=False,
            )
        except FyersHttpError:
            return False

        is_valid = response.status_code == 200
        self._log_event("token.validation", valid=is_valid, status_code=response.status_code)
        return is_valid

    def _auto_login(self) -> str:
        self._guard_circuit_breaker()
        self._log_event("auth.auto_login_started")

        try:
            request_key = self._send_login_otp()
            request_key = self._verify_totp(request_key)
            temp_bearer = self._verify_pin(request_key)
            auth_code = self._generate_auth_code(temp_bearer)
            access_token = self._exchange_auth_code_for_token(auth_code)
        except Exception as exc:  # noqa: BLE001
            self._record_login_failure()
            raise FyersAuthenticationError(f"Automated FYERS login failed: {exc}") from exc

        self._reset_login_failures()
        self._log_event("auth.auto_login_completed")
        return access_token

    def _send_login_otp(self) -> str:
        payload = {
            "fy_id": self._b64(self._credentials.username),
            "app_id": "2",
        }
        response = self._retry_request(
            method="POST",
            url=f"{self.LOGIN_BASE_URL}/send_login_otp_v2",
            json_data=payload,
            auth_required=False,
        )
        body = self._safe_json(response)
        request_key = body.get("request_key")
        if not request_key:
            raise FyersAuthenticationError("OTP request_key missing from send_login_otp_v2 response")
        return str(request_key)

    def _verify_totp(self, request_key: str) -> str:
        payload = {
            "request_key": request_key,
            "otp": self._generate_totp(),
        }
        response = self._retry_request(
            method="POST",
            url=f"{self.LOGIN_BASE_URL}/verify_otp",
            json_data=payload,
            auth_required=False,
        )
        body = self._safe_json(response)
        next_request_key = body.get("request_key")
        if not next_request_key:
            raise FyersAuthenticationError("request_key missing from verify_otp response")
        return str(next_request_key)

    def _verify_pin(self, request_key: str) -> str:
        payload = {
            "request_key": request_key,
            "identity_type": "pin",
            "identifier": self._b64(self._credentials.pin),
        }
        response = self._retry_request(
            method="POST",
            url=f"{self.LOGIN_BASE_URL}/verify_pin_v2",
            json_data=payload,
            auth_required=False,
        )
        body = self._safe_json(response)
        data = body.get("data") or {}
        access_token = data.get("access_token")
        if not access_token:
            raise FyersAuthenticationError("Temporary bearer token missing from verify_pin_v2 response")
        return str(access_token)

    def _generate_auth_code(self, temp_bearer: str) -> str:
        app_id = self._credentials.client_id.split("-")[0]
        payload = {
            "fyers_id": self._credentials.username,
            "app_id": app_id,
            "redirect_uri": self._credentials.redirect_uri,
            "appType": "100",
            "code_challenge": "",
            "state": "state",
            "scope": "",
            "nonce": "",
            "response_type": "code",
            "create_cookie": True,
        }
        headers = {"Authorization": f"Bearer {temp_bearer}"}
        response = self._retry_request(
            method="POST",
            url=f"{self.API_BASE_URL}/api/v2/token",
            json_data=payload,
            headers=headers,
            auth_required=False,
        )
        body = self._safe_json(response)

        redirect_url = body.get("Url") or body.get("url")
        if not redirect_url:
            raise FyersAuthenticationError("Redirect URL missing from /api/v2/token response")

        auth_code = self._extract_auth_code(str(redirect_url))
        if not auth_code:
            raise FyersAuthenticationError("auth_code missing in redirect URL")
        return auth_code

    def _exchange_auth_code_for_token(self, auth_code: str) -> str:
        session = fyersModel.SessionModel(
            client_id=self._credentials.client_id,
            secret_key=self._credentials.secret_key,
            redirect_uri=self._credentials.redirect_uri,
            response_type="code",
            grant_type="authorization_code",
        )
        session.set_token(auth_code)
        token_response = session.generate_token()

        if not isinstance(token_response, dict):
            raise FyersAuthenticationError("Unexpected token exchange response format")

        access_token = token_response.get("access_token")
        if not access_token:
            message = token_response.get("message") or "No access_token in SessionModel response"
            raise FyersAuthenticationError(str(message))
        return str(access_token)

    def _api_request(
        self,
        method: str,
        url: str,
        params: Optional[Dict[str, Any]] = None,
        json_data: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        if not self._access_token:
            self._access_token = self._auto_login()
            self._save_token(self._access_token)

        headers = {"Authorization": f"{self._credentials.client_id}:{self._access_token}"}
        response = self._retry_request(
            method=method,
            url=url,
            params=params,
            json_data=json_data,
            headers=headers,
            auth_required=False,
        )

        if response.status_code in (401, 403):
            self._log_event("token.expired", status_code=response.status_code)
            self._access_token = self._auto_login()
            self._save_token(self._access_token)
            headers["Authorization"] = f"{self._credentials.client_id}:{self._access_token}"
            response = self._retry_request(
                method=method,
                url=url,
                params=params,
                json_data=json_data,
                headers=headers,
                auth_required=False,
            )

        if response.status_code >= 400:
            raise FyersHttpError(
                f"FYERS API request failed ({response.status_code}): {self._truncate(response.text)}"
            )

        return self._safe_json(response)

    def _retry_request(
        self,
        method: str,
        url: str,
        params: Optional[Dict[str, Any]] = None,
        json_data: Optional[Dict[str, Any]] = None,
        headers: Optional[Dict[str, str]] = None,
        auth_required: bool = True,
    ) -> Response:
        request_headers = {"Content-Type": "application/json"}
        if headers:
            request_headers.update(headers)

        if auth_required and self._access_token:
            request_headers.setdefault(
                "Authorization", f"{self._credentials.client_id}:{self._access_token}"
            )

        for attempt in range(1, MAX_RETRIES + 1):
            try:
                response = self._session.request(
                    method=method,
                    url=url,
                    params=params,
                    json=json_data,
                    headers=request_headers,
                    timeout=self._timeout,
                )
            except requests.RequestException as exc:
                if attempt >= MAX_RETRIES:
                    raise FyersHttpError(f"HTTP request failed after retries: {exc}") from exc
                wait_time = BACKOFF_SCHEDULE_SECONDS[attempt - 1]
                self._log_event(
                    "http.retry_exception",
                    method=method,
                    url=url,
                    attempt=attempt,
                    wait_seconds=wait_time,
                    error=str(exc),
                )
                time.sleep(wait_time)
                continue

            if response.status_code in RETRYABLE_STATUS_CODES and attempt < MAX_RETRIES:
                wait_time = BACKOFF_SCHEDULE_SECONDS[attempt - 1]
                self._log_event(
                    "http.retry_status",
                    method=method,
                    url=url,
                    status_code=response.status_code,
                    attempt=attempt,
                    wait_seconds=wait_time,
                )
                time.sleep(wait_time)
                continue

            return response

        raise FyersHttpError("Request retry loop exited unexpectedly")

    def _generate_totp(self) -> str:
        return pyotp.TOTP(self._credentials.totp_key).now()

    def _extract_auth_code(self, redirect_url: str) -> Optional[str]:
        parsed = urlparse(redirect_url)
        query = parse_qs(parsed.query)
        auth_code = query.get("auth_code", [None])[0]
        if auth_code:
            return auth_code

        fragment = parse_qs(parsed.fragment)
        return fragment.get("auth_code", [None])[0]

    def _guard_circuit_breaker(self) -> None:
        with self._circuit_lock:
            now = time.time()
            if self._login_blocked_until > now:
                wait_for = int(self._login_blocked_until - now)
                raise FyersAuthenticationError(
                    f"Login temporarily blocked by circuit breaker for {wait_for}s"
                )

    def _record_login_failure(self) -> None:
        with self._circuit_lock:
            self._consecutive_login_failures += 1
            self._log_event(
                "auth.login_failure",
                consecutive_failures=self._consecutive_login_failures,
                threshold=LOGIN_FAIL_THRESHOLD,
            )
            if self._consecutive_login_failures >= LOGIN_FAIL_THRESHOLD:
                self._login_blocked_until = time.time() + LOGIN_BLOCK_SECONDS
                self._log_event(
                    "auth.circuit_breaker_open",
                    blocked_seconds=LOGIN_BLOCK_SECONDS,
                )

    def _reset_login_failures(self) -> None:
        with self._circuit_lock:
            self._consecutive_login_failures = 0
            self._login_blocked_until = 0.0

    @staticmethod
    def _b64(value: str) -> str:
        return base64.b64encode(value.encode("utf-8")).decode("utf-8")

    @staticmethod
    def _safe_json(response: Response) -> Dict[str, Any]:
        try:
            payload = response.json()
        except ValueError as exc:
            raise FyersHttpError(f"Invalid JSON response: {response.text}") from exc

        if not isinstance(payload, dict):
            raise FyersHttpError("Unexpected non-dict JSON response")
        return payload

    @staticmethod
    def _truncate(value: str, limit: int = 300) -> str:
        if len(value) <= limit:
            return value
        return f"{value[:limit]}..."

    def _log_event(self, event: str, **details: Any) -> None:
        sanitized: Dict[str, Any] = {}
        for key, value in details.items():
            if "token" in key.lower() or "secret" in key.lower() or "pin" in key.lower():
                sanitized[key] = "***"
            else:
                sanitized[key] = value
        LOGGER.info(json.dumps({"event": event, "module": __name__, **sanitized}))


__all__ = ["FyersAdapter", "FyersAdapterError", "FyersAuthenticationError", "FyersHttpError"]
