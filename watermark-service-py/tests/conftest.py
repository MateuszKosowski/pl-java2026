import os

# Tests must not hit a real config-server, real auth-server, or honor any of the
# operator's local env. Use unconditional assignment so a stray `CONFIG_SERVER_URL`
# in the developer's shell doesn't make tests reach out to a live instance.
os.environ["CONFIG_SERVER_URL"] = ""
os.environ["WATERMARK_APP_KEY"] = "test-app-key-deterministic"
os.environ["EUREKA_URL"] = ""
os.environ["SUBSCRIPTION_SERVICE_URL"] = "http://subscription-service:8085"

import pytest
from fastapi.testclient import TestClient

from app.main import create_app


@pytest.fixture
def client() -> TestClient:
    app = create_app()
    return TestClient(app)
