from __future__ import annotations

import logging
from dataclasses import dataclass

import httpx

from app.config import Settings

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class ClassificationResult:
    category: str
    label: str
    confidence: float
    category_confidence: float


_UNKNOWN = ClassificationResult(
    category="unknown", label="unknown", confidence=0.0, category_confidence=0.0
)


async def classify_or_fallback(
    settings: Settings,
    *,
    image_bytes: bytes,
    filename: str | None,
    content_type: str | None,
    bearer_token: str | None,
) -> ClassificationResult:
    """Call ai-service for classification; on any failure return _UNKNOWN.

    Async + split connect/read timeouts so a slow ai-service can't park the
    event loop. The watermark embed continues regardless of classification.
    """
    headers: dict[str, str] = {}
    if bearer_token:
        headers["Authorization"] = f"Bearer {bearer_token}"
    files = {
        "file": (filename or "image", image_bytes, content_type or "application/octet-stream"),
    }
    timeout = httpx.Timeout(connect=2.0, read=15.0, write=15.0, pool=2.0)
    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            response = await client.post(
                f"{settings.ai_service_url}/api/classify",
                files=files,
                headers=headers,
            )
        if response.status_code != 200:
            logger.warning(
                "ai-service returned %s body=%s",
                response.status_code, response.text[:300],
            )
            return _UNKNOWN
        body = response.json()
        return ClassificationResult(
            category=str(body.get("category", "unknown")),
            label=str(body.get("label", "unknown")),
            confidence=float(body.get("confidence", 0.0)),
            category_confidence=float(body.get("categoryConfidence", 0.0)),
        )
    except (httpx.HTTPError, ValueError, KeyError, TypeError) as exc:
        logger.warning("ai-service classification failed: %s", exc)
        return _UNKNOWN
