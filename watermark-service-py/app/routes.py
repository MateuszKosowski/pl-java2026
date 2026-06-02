from __future__ import annotations

import logging
from typing import Annotated

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import Response

from app.ai_client import ClassificationResult, classify_or_fallback
from app.auth import require_principal
from app.config import Settings
from app.subscription_client import (
    consume_reservation,
    operation_for_length_bits,
    release_reservation,
    reserve_tokens,
)
from app.watermark import (
    capacity_report,
    detect_text,
    embed_text,
    visualize,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/watermark", tags=["Watermark"])

_PNG_MAGIC = b"\x89PNG\r\n\x1a\n"
# 25 MB matches the multipart limit in the config-server-served application.yaml.
_MAX_IMAGE_BYTES = 25 * 1024 * 1024
_MAX_TEXT_BYTES = 4096
_UNKNOWN_CLASSIFICATION = ClassificationResult(
    category="unknown",
    label="unknown",
    confidence=0.0,
    category_confidence=0.0,
)


def _settings(request: Request) -> Settings:
    return request.app.state.settings


def _bearer_from_request(request: Request) -> str | None:
    header = request.headers.get("authorization", "")
    if header.lower().startswith("bearer "):
        return header[7:]
    return None


def _read_png(image_bytes: bytes) -> None:
    """Reject non-PNG uploads on the embed path so the GUI's PNG-only contract
    is enforced at the server boundary, not just by the file-picker filter."""
    if len(image_bytes) > _MAX_IMAGE_BYTES:
        raise HTTPException(413, detail=f"Image too large (max {_MAX_IMAGE_BYTES} bytes)")
    if not image_bytes.startswith(_PNG_MAGIC):
        raise HTTPException(
            400,
            detail="Embed accepts PNG only. JPG/WebP destroy the watermark on the very first re-encode.",
        )


async def _classify_when_entitled(
    settings: Settings,
    *,
    image_bytes: bytes,
    filename: str | None,
    content_type: str | None,
    bearer_token: str | None,
) -> ClassificationResult:
    try:
        reservation = await reserve_tokens(
            settings,
            operation="AI_CLASSIFICATION",
            bearer_token=bearer_token,
            external_operation_id=f"classify:{filename or 'image'}",
        )
    except HTTPException as exc:
        if exc.status_code in (403, 409):
            logger.info("Skipping AI classification: %s", exc.detail)
            return _UNKNOWN_CLASSIFICATION
        raise

    try:
        classification = await classify_or_fallback(
            settings,
            image_bytes=image_bytes,
            filename=filename,
            content_type=content_type,
            bearer_token=bearer_token,
        )
    except Exception:
        await release_reservation(settings, reservation_id=reservation.reservation_id, bearer_token=bearer_token)
        raise

    await consume_reservation(settings, reservation_id=reservation.reservation_id, bearer_token=bearer_token)
    return classification


@router.post("/embed")
async def embed(
    request: Request,
    image: Annotated[UploadFile, File()],
    text: Annotated[str, Form()],
    principal: Annotated[str, Depends(require_principal)],
):
    if not text or not text.strip():
        raise HTTPException(400, detail="text must not be blank")
    if len(text.encode("utf-8")) > _MAX_TEXT_BYTES:
        raise HTTPException(413, detail=f"Text too large (max {_MAX_TEXT_BYTES} bytes)")

    settings = _settings(request)
    image_bytes = await image.read()
    _read_png(image_bytes)

    report = capacity_report(image_bytes, owner=principal)
    if not report.image_ok:
        raise HTTPException(
            400,
            detail=(
                f"Image too small ({report.image_width}x{report.image_height}). "
                f"Minimum {report.min_image_width}x{report.min_image_height} for the smallest watermark tier."
            ),
        )
    if len(text.encode("utf-8")) > report.max_text_bytes:
        raise HTTPException(
            400,
            detail=f"Text too long: max {report.max_text_bytes} bytes for this image and owner.",
        )

    bearer_token = _bearer_from_request(request)
    reservation = await reserve_tokens(
        settings,
        operation=operation_for_length_bits(report.length_bits),
        bearer_token=bearer_token,
        external_operation_id=f"embed:{principal}:{image.filename or 'image'}",
    )

    try:
        classification = await _classify_when_entitled(
            settings,
            image_bytes=image_bytes,
            filename=image.filename,
            content_type=image.content_type,
            bearer_token=bearer_token,
        )
        result = embed_text(
            image_bytes,
            text,
            owner=principal,
            app_key=settings.watermark_app_key,
        )
    except ValueError as exc:
        await release_reservation(settings, reservation_id=reservation.reservation_id, bearer_token=bearer_token)
        raise HTTPException(400, detail=str(exc))
    except RuntimeError as exc:
        # embed verification failed at every tier — image content is
        # pathological for the watermark; user can try a larger image.
        logger.warning("Embed verification failed for principal=%s: %s", principal, exc)
        await release_reservation(settings, reservation_id=reservation.reservation_id, bearer_token=bearer_token)
        raise HTTPException(422, detail=str(exc))
    except Exception:
        await release_reservation(settings, reservation_id=reservation.reservation_id, bearer_token=bearer_token)
        raise

    await consume_reservation(settings, reservation_id=reservation.reservation_id, bearer_token=bearer_token)

    return Response(
        content=result.png_bytes,
        media_type="image/png",
        headers={
            "X-Image-Category": classification.category,
            "X-Image-Label": classification.label,
            "X-Image-Confidence": str(classification.confidence),
            "X-Image-Category-Confidence": str(classification.category_confidence),
            "X-Max-Text-Bytes": str(report.max_text_bytes),
            "X-Watermark-Length-Bits": str(report.length_bits),
        },
    )


@router.post("/detect")
async def detect(
    request: Request,
    image: Annotated[UploadFile, File()],
    principal: Annotated[str, Depends(require_principal)],
):
    settings = _settings(request)
    image_bytes = await image.read()
    if len(image_bytes) > _MAX_IMAGE_BYTES:
        raise HTTPException(413, detail=f"Image too large (max {_MAX_IMAGE_BYTES} bytes)")
    bearer_token = _bearer_from_request(request)
    reservation = await reserve_tokens(settings, operation="DETECT", bearer_token=bearer_token)
    try:
        detection = detect_text(image_bytes, app_key=settings.watermark_app_key)
    except Exception:
        await release_reservation(settings, reservation_id=reservation.reservation_id, bearer_token=bearer_token)
        raise
    await consume_reservation(settings, reservation_id=reservation.reservation_id, bearer_token=bearer_token)
    return {
        "watermarked": detection.watermarked,
        "ownerIdentity": detection.owner_identity,
        "version": 1 if detection.watermarked else None,
        "lengthBits": detection.length_bits,
    }


@router.post("/extract")
async def extract(
    request: Request,
    image: Annotated[UploadFile, File()],
    principal: Annotated[str, Depends(require_principal)],
):
    settings = _settings(request)
    image_bytes = await image.read()
    if len(image_bytes) > _MAX_IMAGE_BYTES:
        raise HTTPException(413, detail=f"Image too large (max {_MAX_IMAGE_BYTES} bytes)")
    bearer_token = _bearer_from_request(request)
    reservation = await reserve_tokens(settings, operation="EXTRACT", bearer_token=bearer_token)
    try:
        detection = detect_text(image_bytes, app_key=settings.watermark_app_key)
        if not detection.watermarked:
            await release_reservation(settings, reservation_id=reservation.reservation_id, bearer_token=bearer_token)
            raise HTTPException(400, detail="No watermark found in this image")
        if detection.owner_identity != principal:
            await release_reservation(settings, reservation_id=reservation.reservation_id, bearer_token=bearer_token)
            raise HTTPException(403, detail="Requester is not allowed to read this watermark")
    except HTTPException:
        raise
    except Exception:
        await release_reservation(settings, reservation_id=reservation.reservation_id, bearer_token=bearer_token)
        raise
    await consume_reservation(settings, reservation_id=reservation.reservation_id, bearer_token=bearer_token)
    return {"ownerIdentity": detection.owner_identity, "text": detection.text}


@router.post("/visualize")
async def visualize_endpoint(
    request: Request,
    image: Annotated[UploadFile, File()],
    principal: Annotated[str, Depends(require_principal)],
):
    settings = _settings(request)
    image_bytes = await image.read()
    if len(image_bytes) > _MAX_IMAGE_BYTES:
        raise HTTPException(413, detail=f"Image too large (max {_MAX_IMAGE_BYTES} bytes)")
    bearer_token = _bearer_from_request(request)
    reservation = await reserve_tokens(settings, operation="VISUALIZE", bearer_token=bearer_token)
    try:
        heatmap = visualize(image_bytes, app_key=settings.watermark_app_key)
    except ValueError as exc:
        await release_reservation(settings, reservation_id=reservation.reservation_id, bearer_token=bearer_token)
        raise HTTPException(400, detail=str(exc))
    except Exception:
        await release_reservation(settings, reservation_id=reservation.reservation_id, bearer_token=bearer_token)
        raise
    await consume_reservation(settings, reservation_id=reservation.reservation_id, bearer_token=bearer_token)
    return Response(content=heatmap, media_type="image/png")


@router.post("/capacity")
async def capacity(
    request: Request,
    image: Annotated[UploadFile, File()],
    principal: Annotated[str, Depends(require_principal)],
):
    image_bytes = await image.read()
    if len(image_bytes) > _MAX_IMAGE_BYTES:
        raise HTTPException(413, detail=f"Image too large (max {_MAX_IMAGE_BYTES} bytes)")
    report = capacity_report(image_bytes, owner=principal)
    return {
        "maxTextBytes": report.max_text_bytes,
        "minImageWidth": report.min_image_width,
        "minImageHeight": report.min_image_height,
        "imageWidth": report.image_width,
        "imageHeight": report.image_height,
        "imageOk": report.image_ok,
        "lengthBits": report.length_bits,
    }
