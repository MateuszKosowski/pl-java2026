from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Any

import httpx
from fastapi import HTTPException

from app.config import Settings

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class TokenReservation:
    reservation_id: str
    operation: str
    tokens: int


def operation_for_length_bits(length_bits: int) -> str:
    if length_bits == 768:
        return "EMBED_768"
    if length_bits == 1024:
        return "EMBED_1024"
    raise ValueError(f"Unsupported watermark length_bits: {length_bits}")


async def reserve_tokens(
    settings: Settings,
    *,
    operation: str,
    bearer_token: str | None,
    external_operation_id: str | None = None,
) -> TokenReservation:
    headers = _auth_headers(bearer_token)
    body: dict[str, Any] = {"operation": operation}
    if external_operation_id:
        body["externalOperationId"] = external_operation_id

    response = await _post(settings, "/api/tokens/reservations", headers=headers, json=body)
    if response.status_code == 201:
        payload = response.json()
        return TokenReservation(
            reservation_id=str(payload["reservationId"]),
            operation=str(payload["operation"]),
            tokens=int(payload["tokens"]),
        )
    _raise_reservation_error(response)


async def consume_reservation(settings: Settings, *, reservation_id: str, bearer_token: str | None) -> None:
    response = await _post(
        settings,
        f"/api/tokens/reservations/{reservation_id}/consume",
        headers=_auth_headers(bearer_token),
        json=None,
    )
    if response.status_code >= 400:
        logger.warning("subscription-service consume failed: status=%s body=%s", response.status_code, response.text[:300])


async def release_reservation(settings: Settings, *, reservation_id: str, bearer_token: str | None) -> None:
    response = await _post(
        settings,
        f"/api/tokens/reservations/{reservation_id}/release",
        headers=_auth_headers(bearer_token),
        json=None,
    )
    if response.status_code >= 400:
        logger.warning("subscription-service release failed: status=%s body=%s", response.status_code, response.text[:300])


async def charge_operation(settings: Settings, *, operation: str, bearer_token: str | None) -> TokenReservation:
    reservation = await reserve_tokens(settings, operation=operation, bearer_token=bearer_token)
    await consume_reservation(settings, reservation_id=reservation.reservation_id, bearer_token=bearer_token)
    return reservation


def _auth_headers(bearer_token: str | None) -> dict[str, str]:
    if not bearer_token:
        return {}
    return {"Authorization": f"Bearer {bearer_token}"}


async def _post(
    settings: Settings,
    path: str,
    *,
    headers: dict[str, str],
    json: dict[str, Any] | None,
) -> httpx.Response:
    timeout = httpx.Timeout(connect=2.0, read=10.0, write=10.0, pool=2.0)
    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            return await client.post(
                f"{settings.subscription_service_url}{path}",
                headers=headers,
                json=json,
            )
    except httpx.HTTPError as exc:
        logger.error("subscription-service request failed: %s", exc)
        raise HTTPException(503, detail="Subscription service is currently unavailable") from exc


def _raise_reservation_error(response: httpx.Response) -> None:
    try:
        body = response.json()
    except ValueError:
        body = {}
    code = body.get("code", "TOKEN_RESERVATION_FAILED")
    message = body.get("message", "Could not reserve tokens for this operation")
    if response.status_code == 401:
        raise HTTPException(401, detail=message)
    if response.status_code == 403:
        raise HTTPException(403, detail=message)
    if response.status_code == 409:
        raise HTTPException(409, detail={"code": code, "message": message})
    raise HTTPException(response.status_code, detail=message)
