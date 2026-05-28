import io

import numpy as np
import pytest
from PIL import Image

from app.watermark import (
    DetectionResult,
    WatermarkResult,
    capacity_report,
    detect_text,
    embed_text,
    select_length_bits,
    visualize,
)

APP_KEY = "test-app-key-deterministic"


def _random_png(width: int, height: int | None = None, seed: int = 42) -> bytes:
    if height is None:
        height = width
    rng = np.random.RandomState(seed)
    arr = rng.randint(0, 256, (height, width, 3), dtype=np.uint8)
    buf = io.BytesIO()
    Image.fromarray(arr).save(buf, format="PNG")
    return buf.getvalue()


@pytest.fixture
def big_image() -> bytes:
    """1600x1600 — picks the 1024-bit tier (largest capacity)."""
    return _random_png(1600)


@pytest.fixture
def fhd_landscape() -> bytes:
    """1920x1080 FHD screenshot — short=1080, long=1920 → 1024-bit tier."""
    return _random_png(1920, 1080)


@pytest.fixture
def medium_image() -> bytes:
    """1200x1200 — too narrow for 1024-bit tier (needs long ≥ 1600), falls to 768-bit.

    Note: 1024-bit tier rule is short≥1080 AND long≥1600 — 1200×1200 satisfies the
    short floor but not the long one, so it picks the 768-bit tier.
    """
    return _random_png(1200)


def test_embed_then_detect_big_image(big_image):
    result = embed_text(big_image, "hello world", owner="user-42", app_key=APP_KEY)
    assert isinstance(result, WatermarkResult)
    detection = detect_text(result.png_bytes, app_key=APP_KEY)
    assert isinstance(detection, DetectionResult)
    assert detection.watermarked is True
    assert detection.owner_identity == "user-42"
    assert detection.text == "hello world"
    assert detection.length_bits == 1024


def test_embed_then_detect_medium_image(medium_image):
    result = embed_text(medium_image, "midsize", owner="u-1", app_key=APP_KEY)
    detection = detect_text(result.png_bytes, app_key=APP_KEY)
    assert detection.watermarked is True
    assert detection.owner_identity == "u-1"
    assert detection.text == "midsize"
    assert detection.length_bits == 768


def test_embed_then_detect_fhd_landscape(fhd_landscape):
    """1920x1080 should use the largest (1024-bit) tier."""
    result = embed_text(fhd_landscape, "fhd shot", owner="u-1", app_key=APP_KEY)
    detection = detect_text(result.png_bytes, app_key=APP_KEY)
    assert detection.watermarked is True
    assert detection.text == "fhd shot"
    assert detection.length_bits == 1024


def test_embed_then_detect_fhd_portrait():
    """1080x1920 (portrait phone screenshot) should also use the largest tier."""
    img = _random_png(1080, 1920)
    result = embed_text(img, "portrait", owner="u-1", app_key=APP_KEY)
    detection = detect_text(result.png_bytes, app_key=APP_KEY)
    assert detection.watermarked is True
    assert detection.length_bits == 1024


def test_detect_with_wrong_key_returns_not_watermarked(big_image):
    result = embed_text(big_image, "secret", owner="user-1", app_key=APP_KEY)
    detection = detect_text(result.png_bytes, app_key="totally-different-key")
    assert detection.watermarked is False
    assert detection.owner_identity is None
    assert detection.length_bits is None


def test_detect_plain_image_returns_not_watermarked(big_image):
    detection = detect_text(big_image, app_key=APP_KEY)
    assert detection.watermarked is False


def test_embed_rejects_image_below_smallest_tier():
    tiny = _random_png(800)
    with pytest.raises(ValueError, match="too small"):
        embed_text(tiny, "hello", owner="u-1", app_key=APP_KEY)


def test_select_length_bits_picks_largest_tier_that_fits():
    # tier 1024-bit: short>=1080, long>=1600
    assert select_length_bits(2000, 2000) == 1024
    assert select_length_bits(1920, 1080) == 1024
    assert select_length_bits(1080, 1920) == 1024
    assert select_length_bits(1600, 1080) == 1024
    # tier 768-bit: short>=1024, long>=1024
    assert select_length_bits(1024, 1024) == 768
    assert select_length_bits(1200, 1200) == 768
    assert select_length_bits(1500, 1500) == 768
    # too small for either tier
    with pytest.raises(ValueError):
        select_length_bits(1023, 1023)
    with pytest.raises(ValueError):
        select_length_bits(1280, 720)  # short side too narrow


def test_capacity_report_for_big_image(big_image):
    report = capacity_report(big_image, owner="alice-7")
    assert report.image_ok is True
    assert report.length_bits == 1024
    # 128 bytes total - 41 envelope - 8 owner = ~79 bytes
    assert 70 < report.max_text_bytes < 90


def test_capacity_report_for_medium_image(medium_image):
    report = capacity_report(medium_image, owner="alice-7")
    assert report.image_ok is True
    assert report.length_bits == 768
    # 96 bytes total - 49 envelope - 8 owner = ~39 bytes
    assert 30 < report.max_text_bytes < 50


def test_capacity_report_for_fhd_landscape(fhd_landscape):
    report = capacity_report(fhd_landscape, owner="alice-7")
    assert report.image_ok is True
    assert report.length_bits == 1024
    assert 60 < report.max_text_bytes < 90


def test_capacity_report_for_too_small_image():
    tiny = _random_png(800)
    report = capacity_report(tiny, owner="alice-7")
    assert report.image_ok is False
    assert report.length_bits == 0
    assert report.max_text_bytes == 0


def test_visualize_returns_png(big_image):
    heatmap_png = visualize(big_image, app_key=APP_KEY)
    assert heatmap_png[:8] == b"\x89PNG\r\n\x1a\n"
