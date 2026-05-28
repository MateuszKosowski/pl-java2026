import httpx
import pytest
import respx
from httpx import Response

from app.ai_client import ClassificationResult, classify_or_fallback
from app.config import Settings


SETTINGS = Settings(ai_service_url="http://ai-service:8084")


@respx.mock
@pytest.mark.asyncio
async def test_classify_returns_result_on_success():
    respx.post("http://ai-service:8084/api/classify").mock(
        return_value=Response(
            200,
            json={
                "category": "dog",
                "label": "golden retriever",
                "confidence": 0.05,
                "categoryConfidence": 0.89,
                "top3": [],
            },
        )
    )
    result = await classify_or_fallback(
        SETTINGS,
        image_bytes=b"fake-png",
        filename="img.png",
        content_type="image/png",
        bearer_token="abc",
    )
    assert result == ClassificationResult(
        category="dog",
        label="golden retriever",
        confidence=0.05,
        category_confidence=0.89,
    )


@respx.mock
@pytest.mark.asyncio
async def test_classify_returns_unknown_fallback_on_error():
    respx.post("http://ai-service:8084/api/classify").mock(return_value=Response(500))
    result = await classify_or_fallback(
        SETTINGS,
        image_bytes=b"fake-png",
        filename="img.png",
        content_type="image/png",
        bearer_token="abc",
    )
    assert result == ClassificationResult(
        category="unknown", label="unknown", confidence=0.0, category_confidence=0.0
    )


@respx.mock
@pytest.mark.asyncio
async def test_classify_returns_unknown_when_network_fails():
    respx.post("http://ai-service:8084/api/classify").mock(side_effect=httpx.ConnectError("boom"))
    result = await classify_or_fallback(
        SETTINGS,
        image_bytes=b"fake-png",
        filename="img.png",
        content_type="image/png",
        bearer_token="abc",
    )
    assert result.category == "unknown"
    assert result.label == "unknown"
    assert result.confidence == 0.0
