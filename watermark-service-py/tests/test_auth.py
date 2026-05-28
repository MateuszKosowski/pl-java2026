import base64
import json

import httpx
import pytest
import respx
from fastapi import Depends, FastAPI
from fastapi.testclient import TestClient
from httpx import Response

from app.auth import require_principal
from app.config import Settings


@pytest.fixture
def auth_app() -> FastAPI:
    from app.main import create_app
    app = create_app()
    app.state.settings = Settings(auth_server_url="http://auth-server:8081")

    @app.get("/_protected")
    def protected(principal: str = Depends(require_principal)):
        return {"principal": principal}

    return app


def _jwt_with(sub: str, user_id: int) -> str:
    header = base64.urlsafe_b64encode(b'{"alg":"HS256","typ":"JWT"}').rstrip(b"=").decode()
    payload = base64.urlsafe_b64encode(
        json.dumps({"sub": sub, "userId": user_id}).encode()
    ).rstrip(b"=").decode()
    return f"{header}.{payload}.signature-placeholder"


@respx.mock
def test_missing_authorization_header_returns_401(auth_app):
    client = TestClient(auth_app)
    response = client.get("/_protected")
    assert response.status_code == 401
    assert response.json() == {"error": "Invalid or expired token"}


@respx.mock
def test_invalid_token_returns_401(auth_app):
    respx.post("http://auth-server:8081/auth/validate").mock(
        return_value=Response(200, json=False)
    )
    client = TestClient(auth_app)
    response = client.get("/_protected", headers={"Authorization": "Bearer bad-token"})
    assert response.status_code == 401
    assert response.json() == {"error": "Invalid or expired token"}


@respx.mock
def test_valid_token_extracts_principal(auth_app):
    respx.post("http://auth-server:8081/auth/validate").mock(
        return_value=Response(200, json=True)
    )
    token = _jwt_with("alice", 42)
    client = TestClient(auth_app)
    response = client.get("/_protected", headers={"Authorization": f"Bearer {token}"})
    assert response.status_code == 200
    assert response.json() == {"principal": "alice-42"}


@respx.mock
def test_auth_server_down_returns_503(auth_app):
    respx.post("http://auth-server:8081/auth/validate").mock(
        side_effect=httpx.ConnectError("connection refused")
    )
    token = _jwt_with("alice", 42)
    client = TestClient(auth_app)
    response = client.get("/_protected", headers={"Authorization": f"Bearer {token}"})
    assert response.status_code == 503
    assert response.json() == {"error": "Authentication service is currently unavailable"}
