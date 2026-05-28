import base64
import io
import json

import numpy as np
import respx
from PIL import Image
from httpx import Response


def _random_png(width: int, height: int | None = None, seed: int = 0) -> bytes:
    if height is None:
        height = width
    rng = np.random.RandomState(seed)
    arr = rng.randint(0, 256, (height, width, 3), dtype=np.uint8)
    buf = io.BytesIO()
    Image.fromarray(arr).save(buf, format="PNG")
    return buf.getvalue()


def _jwt(sub: str, user_id: int) -> str:
    header = base64.urlsafe_b64encode(b'{"alg":"HS256","typ":"JWT"}').rstrip(b"=").decode()
    payload = base64.urlsafe_b64encode(
        json.dumps({"sub": sub, "userId": user_id}).encode()
    ).rstrip(b"=").decode()
    return f"{header}.{payload}.signature-placeholder"


def _auth_headers(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def test_health_returns_ok(client):
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


@respx.mock
def test_embed_returns_png_with_classification_and_capacity_headers(client):
    respx.post("http://auth-server:8081/auth/validate").mock(return_value=Response(200, json=True))
    respx.post("http://ai-service:8084/api/classify").mock(
        return_value=Response(
            200,
            json={
                "category": "dog",
                "label": "labrador",
                "confidence": 0.05,
                "categoryConfidence": 0.81,
                "top3": [],
            },
        )
    )
    token = _jwt("alice", 7)
    response = client.post(
        "/api/watermark/embed",
        headers=_auth_headers(token),
        files={"image": ("a.png", _random_png(1600), "image/png")},
        data={"text": "hello"},
    )
    assert response.status_code == 200
    assert response.headers["content-type"] == "image/png"
    assert response.headers["x-image-category"] == "dog"
    assert response.headers["x-image-label"] == "labrador"
    assert int(response.headers["x-max-text-bytes"]) > 0
    assert response.headers["x-watermark-length-bits"] == "1024"
    assert response.content[:8] == b"\x89PNG\r\n\x1a\n"


@respx.mock
def test_detect_then_extract_roundtrip(client):
    respx.post("http://auth-server:8081/auth/validate").mock(return_value=Response(200, json=True))
    respx.post("http://ai-service:8084/api/classify").mock(return_value=Response(500))

    token = _jwt("alice", 7)
    embed_response = client.post(
        "/api/watermark/embed",
        headers=_auth_headers(token),
        files={"image": ("a.png", _random_png(1600), "image/png")},
        data={"text": "hello-world"},
    )
    assert embed_response.status_code == 200
    watermarked_png = embed_response.content

    detect_response = client.post(
        "/api/watermark/detect",
        headers=_auth_headers(token),
        files={"image": ("wm.png", watermarked_png, "image/png")},
    )
    assert detect_response.status_code == 200
    body = detect_response.json()
    assert body["watermarked"] is True
    assert body["ownerIdentity"] == "alice-7"
    assert body["version"] == 1
    assert body["lengthBits"] == 1024

    extract_response = client.post(
        "/api/watermark/extract",
        headers=_auth_headers(token),
        files={"image": ("wm.png", watermarked_png, "image/png")},
    )
    assert extract_response.status_code == 200
    assert extract_response.json() == {"ownerIdentity": "alice-7", "text": "hello-world"}


@respx.mock
def test_embed_on_smaller_image_picks_lower_tier(client):
    """A 1024x1024 image only qualifies for the 768-bit tier (1024-bit needs long ≥ 1600)."""
    respx.post("http://auth-server:8081/auth/validate").mock(return_value=Response(200, json=True))
    respx.post("http://ai-service:8084/api/classify").mock(return_value=Response(500))

    token = _jwt("alice", 7)
    embed_response = client.post(
        "/api/watermark/embed",
        headers=_auth_headers(token),
        files={"image": ("a.png", _random_png(1024), "image/png")},
        data={"text": "medium-sized"},
    )
    assert embed_response.status_code == 200
    assert embed_response.headers["x-watermark-length-bits"] == "768"

    detect_response = client.post(
        "/api/watermark/detect",
        headers=_auth_headers(token),
        files={"image": ("wm.png", embed_response.content, "image/png")},
    )
    body = detect_response.json()
    assert body["watermarked"] is True
    assert body["lengthBits"] == 768


@respx.mock
def test_embed_fhd_landscape_uses_largest_tier(client):
    """1920x1080 screenshot should use the 1024-bit tier."""
    respx.post("http://auth-server:8081/auth/validate").mock(return_value=Response(200, json=True))
    respx.post("http://ai-service:8084/api/classify").mock(return_value=Response(500))

    token = _jwt("alice", 7)
    response = client.post(
        "/api/watermark/embed",
        headers=_auth_headers(token),
        files={"image": ("fhd.png", _random_png(1920, 1080), "image/png")},
        data={"text": "fhd screenshot"},
    )
    assert response.status_code == 200
    assert response.headers["x-watermark-length-bits"] == "1024"


@respx.mock
def test_extract_by_wrong_user_returns_403(client):
    respx.post("http://auth-server:8081/auth/validate").mock(return_value=Response(200, json=True))
    respx.post("http://ai-service:8084/api/classify").mock(return_value=Response(500))

    alice = _jwt("alice", 7)
    bob = _jwt("bob", 8)
    embed_response = client.post(
        "/api/watermark/embed",
        headers=_auth_headers(alice),
        files={"image": ("a.png", _random_png(1600), "image/png")},
        data={"text": "owner-data"},
    )
    assert embed_response.status_code == 200
    extract_response = client.post(
        "/api/watermark/extract",
        headers=_auth_headers(bob),
        files={"image": ("wm.png", embed_response.content, "image/png")},
    )
    assert extract_response.status_code == 403


@respx.mock
def test_detect_requires_auth(client):
    response = client.post(
        "/api/watermark/detect",
        files={"image": ("a.png", _random_png(1600), "image/png")},
    )
    assert response.status_code == 401


@respx.mock
def test_visualize_requires_auth(client):
    response = client.post(
        "/api/watermark/visualize",
        files={"image": ("a.png", _random_png(1600), "image/png")},
    )
    assert response.status_code == 401


@respx.mock
def test_capacity_for_big_image(client):
    respx.post("http://auth-server:8081/auth/validate").mock(return_value=Response(200, json=True))
    token = _jwt("alice", 7)
    response = client.post(
        "/api/watermark/capacity",
        headers=_auth_headers(token),
        files={"image": ("a.png", _random_png(1600), "image/png")},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["imageOk"] is True
    assert body["imageWidth"] == 1600
    assert body["lengthBits"] == 1024
    assert 70 < body["maxTextBytes"] < 90


@respx.mock
def test_capacity_for_medium_image_picks_lower_tier(client):
    respx.post("http://auth-server:8081/auth/validate").mock(return_value=Response(200, json=True))
    token = _jwt("alice", 7)
    response = client.post(
        "/api/watermark/capacity",
        headers=_auth_headers(token),
        files={"image": ("a.png", _random_png(1024), "image/png")},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["imageOk"] is True
    assert body["lengthBits"] == 768
    assert 30 < body["maxTextBytes"] < 50


@respx.mock
def test_capacity_for_fhd_landscape(client):
    """1920x1080 reports the 1024-bit tier."""
    respx.post("http://auth-server:8081/auth/validate").mock(return_value=Response(200, json=True))
    token = _jwt("alice", 7)
    response = client.post(
        "/api/watermark/capacity",
        headers=_auth_headers(token),
        files={"image": ("a.png", _random_png(1920, 1080), "image/png")},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["imageOk"] is True
    assert body["lengthBits"] == 1024
    assert body["imageWidth"] == 1920
    assert body["imageHeight"] == 1080


@respx.mock
def test_capacity_reports_too_small(client):
    respx.post("http://auth-server:8081/auth/validate").mock(return_value=Response(200, json=True))
    token = _jwt("alice", 7)
    response = client.post(
        "/api/watermark/capacity",
        headers=_auth_headers(token),
        files={"image": ("a.png", _random_png(800), "image/png")},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["imageOk"] is False
    assert body["lengthBits"] == 0


def test_app_boots_when_eureka_unreachable(monkeypatch):
    monkeypatch.setenv("EUREKA_URL", "http://nonexistent-eureka:9999/eureka/")
    from importlib import reload

    import app.main

    reload(app.main)
    test_app = app.main.create_app()
    from fastapi.testclient import TestClient

    with TestClient(test_app) as test_client:
        assert test_client.get("/health").status_code == 200
