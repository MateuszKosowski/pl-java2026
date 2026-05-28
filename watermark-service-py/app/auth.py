from __future__ import annotations

import base64
import binascii
import json
import logging
import re

import httpx
from fastapi import Depends, HTTPException, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.config import Settings

logger = logging.getLogger(__name__)

_bearer = HTTPBearer(auto_error=False)

# A principal flows verbatim into the watermark envelope as the owner identity.
# Reject anything that could break the `owner|text` framing, log injection,
# or balloon an embedded payload. ASCII identifier characters only.
_PRINCIPAL_PATTERN = re.compile(r"^[A-Za-z0-9._@-]{1,64}$")
_DEFAULT_PRINCIPAL = "User"


def _401() -> HTTPException:
    return HTTPException(status_code=401, detail={"error": "Invalid or expired token"})


def _503() -> HTTPException:
    return HTTPException(
        status_code=503,
        detail={"error": "Authentication service is currently unavailable"},
    )


def _decode_principal(token: str) -> str:
    """Mirror Java extractPrincipalFromToken: '{sub}-{userId}', fallback 'User'.

    The auth-server already validated the signature; we only re-parse the body
    to extract the identifier. Whatever we return goes into the watermark
    envelope, so it MUST pass `_PRINCIPAL_PATTERN` — anything else falls back
    to the default principal rather than being embedded verbatim.
    """
    try:
        parts = token.split(".")
        if len(parts) < 2:
            return _DEFAULT_PRINCIPAL
        payload_segment = parts[1] + "=" * (-len(parts[1]) % 4)
        payload = json.loads(base64.urlsafe_b64decode(payload_segment))
        sub = payload.get("sub")
        user_id = payload.get("userId")
        if isinstance(sub, str) and user_id is not None:
            candidate = f"{sub}-{user_id}"
            if _PRINCIPAL_PATTERN.fullmatch(candidate):
                return candidate
            logger.warning("JWT principal failed sanitization: %r", candidate)
    except (ValueError, json.JSONDecodeError, binascii.Error) as exc:
        logger.debug("Could not parse JWT payload: %s", exc)
    return _DEFAULT_PRINCIPAL


def require_principal(
    request: Request,
    credentials: HTTPAuthorizationCredentials | None = Depends(_bearer),
) -> str:
    if credentials is None or not credentials.credentials:
        raise _401()

    settings: Settings = request.app.state.settings
    token = credentials.credentials

    try:
        with httpx.Client(timeout=httpx.Timeout(connect=2.0, read=5.0, write=5.0, pool=2.0)) as client:
            response = client.post(
                f"{settings.auth_server_url}/auth/validate",
                params={"token": token},
            )
    except httpx.HTTPError as exc:
        logger.error("auth-server unreachable: %s", exc)
        raise _503()

    if response.status_code != 200:
        raise _401()
    try:
        body = response.json()
    except ValueError:
        raise _401()
    # Strict identity — anything other than literal JSON `true` is a rejection,
    # so a future auth-server schema change (e.g. `{"valid": true}`) fails closed
    # instead of silently accepting unrelated truthy bodies.
    if body is not True:
        raise _401()

    return _decode_principal(token)
