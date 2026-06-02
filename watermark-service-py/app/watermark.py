from __future__ import annotations

import logging
from dataclasses import dataclass

import cv2
import numpy as np
from imwatermark import WatermarkDecoder, WatermarkEncoder

from app import crypto

logger = logging.getLogger(__name__)

# Adaptive tiers: pick the largest watermark capacity the image can carry reliably.
# Empirically calibrated against random-pixel images (worst-case for dwtDctSvd).
# Real photographs typically tolerate equal or smaller dimensions.
#
# Each tier requires (min_short_side, min_long_side) — so a 1920×1080 landscape
# screenshot picks the same tier as 1080×1920 portrait. The asymmetric thresholds
# reflect the empirical finding that dwtDctSvd's bit error rate depends roughly on
# pixel-per-bit density rather than a square minimum.
#
# Capacity after AES-GCM envelope + Reed-Solomon parity:
#   1024 bits → ~71 chars of text (after owner like "alice-7|" ~8 bytes)
#   768 bits  → ~39 chars
#
# Embed verifies the round-trip and falls back to the next smaller tier if the
# library's bit-flips overwhelm even Reed-Solomon, so what we promise here is the
# *typical* outcome; the actual tier used is reported back in response headers.
TIERS: tuple[tuple[int, int, int], ...] = (
    # (min_short_side, min_long_side, length_bits)
    (1080, 1600, 1024),  # FHD landscape/portrait, 1600×1600+, modern phone photos
    (1024, 1024, 768),   # 1024×1024+ (square OK), photos that didn't qualify for tier A
)

TIERS = (
    (1024, 1024, 1024),  # Above FHD pixel count uses the larger token tier.
    (1024, 1024, 768),   # Basic tier: 1024x1024 up to and including FHD.
)

KNOWN_LENGTHS_BITS: tuple[int, ...] = tuple(bits for _, _, bits in TIERS)
MIN_SHORT_SIDE: int = TIERS[-1][0]
MIN_LONG_SIDE: int = TIERS[-1][1]
FULL_HD_PIXELS: int = 1920 * 1080


@dataclass(frozen=True)
class WatermarkResult:
    png_bytes: bytes


@dataclass(frozen=True)
class CapacityReport:
    max_text_bytes: int
    min_image_width: int
    min_image_height: int
    image_ok: bool
    image_width: int
    image_height: int
    length_bits: int  # 0 when image is below the minimum tier


def _decode_png(image_bytes: bytes) -> np.ndarray:
    """Decode image bytes to a BGR ndarray, honoring EXIF orientation.

    cv2.imdecode ignores EXIF rotation; a portrait JPEG with an orientation tag
    decodes as landscape pixels, which throws off our tier selector (which
    looks at width vs height). Pre-apply Pillow's `exif_transpose` so the
    pixel data matches what the user sees.
    """
    from io import BytesIO

    from PIL import Image, ImageOps

    try:
        with Image.open(BytesIO(image_bytes)) as pil_img:
            oriented = ImageOps.exif_transpose(pil_img).convert("RGB")
    except Exception as exc:  # PIL.UnidentifiedImageError, OSError, etc.
        raise ValueError(f"Could not decode image bytes: {exc}") from exc
    rgb_array = np.asarray(oriented)
    return cv2.cvtColor(rgb_array, cv2.COLOR_RGB2BGR)


def _encode_png(bgr: np.ndarray) -> bytes:
    ok, buf = cv2.imencode(".png", bgr, [cv2.IMWRITE_PNG_COMPRESSION, 9])
    if not ok:
        raise RuntimeError("Could not encode PNG")
    return buf.tobytes()


def _pack(payload: bytes, length_bits: int) -> bytes:
    target_bytes = length_bits // 8
    if len(payload) > target_bytes:
        raise ValueError(
            f"Encrypted payload too long: {len(payload)} bytes, capacity {target_bytes} bytes "
            f"(at {length_bits}-bit watermark)"
        )
    return payload.ljust(target_bytes, b"\x00")


def _unpack(raw: bytes) -> bytes:
    return raw.rstrip(b"\x00")


def _tiers_for_image(width: int, height: int) -> list[int]:
    """Return all tiers an image qualifies for, largest-capacity first.

    Empty list ⇒ image too small for any tier.
    """
    short = min(width, height)
    long_side = max(width, height)
    if short < MIN_SHORT_SIDE or long_side < MIN_LONG_SIDE:
        return []

    if width * height > FULL_HD_PIXELS:
        return [1024, 768]
    return [768]


def select_length_bits(width: int, height: int) -> int:
    """Pick the largest watermark capacity this image can carry. Raises if too small."""
    tiers = _tiers_for_image(width, height)
    if not tiers:
        raise ValueError(
            f"Image too small: {width}x{height}, need at least "
            f"{MIN_SHORT_SIDE}x{MIN_LONG_SIDE} (short × long side) for any watermark capacity"
        )
    return tiers[0]


def capacity_report(image_bytes: bytes, *, owner: str) -> CapacityReport:
    bgr = _decode_png(image_bytes)
    height, width = bgr.shape[:2]
    tiers = _tiers_for_image(width, height)
    if not tiers:
        return CapacityReport(
            max_text_bytes=0,
            min_image_width=MIN_SHORT_SIDE,
            min_image_height=MIN_LONG_SIDE,
            image_ok=False,
            image_width=width,
            image_height=height,
            length_bits=0,
        )
    length_bits = tiers[0]
    return CapacityReport(
        max_text_bytes=crypto.max_text_bytes(length_bits, owner),
        min_image_width=MIN_SHORT_SIDE,
        min_image_height=MIN_LONG_SIDE,
        image_ok=True,
        image_width=width,
        image_height=height,
        length_bits=length_bits,
    )


def _embed_at_tier(bgr: np.ndarray, sealed: bytes, length_bits: int) -> bytes:
    payload = _pack(sealed, length_bits)
    encoder = WatermarkEncoder()
    encoder.set_watermark("bytes", payload)
    watermarked = encoder.encode(bgr, "dwtDctSvd")
    return _encode_png(watermarked)


def _verify_roundtrip(png_bytes: bytes, length_bits: int, expected: crypto.DecodedEnvelope, *, app_key: str) -> bool:
    """Decode + decrypt the embedded image to confirm the watermark survives PNG round-trip
    AND ECC+GCM produce the original payload. Catches the (rare) cases where even
    Reed-Solomon can't fix the library's bit-flips.

    Any exception during decode (imwatermark internals can raise IndexError on
    short reads, etc.) counts as verification failure so the caller falls back
    to the next tier instead of bubbling a 500."""
    try:
        bgr = _decode_png(png_bytes)
        decoder = WatermarkDecoder("bytes", length_bits)
        raw = _unpack(decoder.decode(bgr, "dwtDctSvd"))
        decoded = crypto.unseal(raw, app_key=app_key)
    except (crypto.CryptoError, Exception) as exc:  # noqa: BLE001 — intentional broad catch
        if not isinstance(exc, crypto.CryptoError):
            logger.debug("Roundtrip verification raised %s: %s", type(exc).__name__, exc)
        return False
    return decoded.owner == expected.owner and decoded.text == expected.text


def embed_text(
    image_bytes: bytes,
    text: str,
    *,
    owner: str,
    app_key: str,
) -> WatermarkResult:
    bgr = _decode_png(image_bytes)
    height, width = bgr.shape[:2]
    candidate_tiers = _tiers_for_image(width, height)
    if not candidate_tiers:
        raise ValueError(
            f"Image too small: {width}x{height}, need at least "
            f"{MIN_SHORT_SIDE}x{MIN_LONG_SIDE} (short × long side)"
        )

    sealed = crypto.seal(owner, text, app_key=app_key)
    expected = crypto.DecodedEnvelope(owner=owner, text=text)

    last_error: Exception | None = None
    for length_bits in candidate_tiers:
        try:
            png_bytes = _embed_at_tier(bgr, sealed, length_bits)
        except ValueError as exc:
            # text too long for THIS tier — try the next (smaller) one, but
            # remember the error in case all tiers are too small for the text
            last_error = exc
            continue
        if _verify_roundtrip(png_bytes, length_bits, expected, app_key=app_key):
            return WatermarkResult(png_bytes=png_bytes)
        # round-trip verification failed — fall back to next smaller tier

    if last_error is not None:
        raise last_error
    raise RuntimeError(
        "Embed verification failed at every tier. The image may have unusual content; "
        "try a larger or differently-shaped image."
    )


@dataclass(frozen=True)
class DetectionResult:
    watermarked: bool
    owner_identity: str | None
    text: str | None
    length_bits: int | None


def detect_text(image_bytes: bytes, *, app_key: str) -> DetectionResult:
    """Try each known length until one decrypts. Wrong length → GCM tag rejects."""
    bgr = _decode_png(image_bytes)
    for length_bits in KNOWN_LENGTHS_BITS:
        decoder = WatermarkDecoder("bytes", length_bits)
        raw = _unpack(decoder.decode(bgr, "dwtDctSvd"))
        try:
            envelope = crypto.unseal(raw, app_key=app_key)
        except crypto.CryptoError:
            continue
        return DetectionResult(
            watermarked=True,
            owner_identity=envelope.owner,
            text=envelope.text,
            length_bits=length_bits,
        )
    return DetectionResult(watermarked=False, owner_identity=None, text=None, length_bits=None)


_SENTINEL_TEXT = "VISUALIZE"


def visualize(image_bytes: bytes, *, app_key: str) -> bytes:
    """Heatmap of |watermarked - input| per pixel as pseudocolor PNG.

    The library may pad the watermarked output to a DCT-aligned size; crop back to source.
    """
    bgr = _decode_png(image_bytes)
    sentinel = embed_text(
        image_bytes,
        _SENTINEL_TEXT,
        owner="visualize",
        app_key=app_key,
    )
    watermarked = _decode_png(sentinel.png_bytes)

    if watermarked.shape != bgr.shape:
        height, width = bgr.shape[:2]
        watermarked = watermarked[:height, :width]

    diff = cv2.absdiff(watermarked, bgr)
    diff_max = diff.max(axis=2).astype(np.uint8)

    peak = int(diff_max.max())
    if peak > 0:
        normalized = ((diff_max.astype(np.float32) / peak) * 255.0).astype(np.uint8)
    else:
        normalized = diff_max

    heatmap = cv2.applyColorMap(normalized, cv2.COLORMAP_JET)
    return _encode_png(heatmap)
