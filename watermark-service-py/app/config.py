from __future__ import annotations

import logging
import os
import time

import httpx
from pydantic_settings import BaseSettings, SettingsConfigDict

logger = logging.getLogger(__name__)

APPLICATION_NAME = "watermark-service"

# Public-knowledge fallback used in dev/compose. Refusing to boot prod with this
# value keeps a misconfigured deploy from producing forgeable watermarks.
DEFAULT_DEV_APP_KEY = "local-dev-watermark-secret"


class InsecureDefaultAppKeyError(RuntimeError):
    """Raised when WATERMARK_APP_KEY is the public dev default and dev-mode is off."""


class Settings(BaseSettings):
    """Runtime configuration.

    Precedence (highest first):
      1. Process environment variables
      2. Properties merged from Spring Cloud Config Server (application + watermark-service)
      3. Defaults declared below
    """

    config_server_url: str = "http://config-server:8888"
    eureka_url: str = "http://eureka-server:8761/eureka/"
    auth_server_url: str = "http://auth-server:8081"
    ai_service_url: str = "http://ai-service:8084"
    subscription_service_url: str = "http://subscription-service:8085"
    watermark_app_key: str = DEFAULT_DEV_APP_KEY
    log_level: str = "INFO"
    instance_hostname: str = "watermark-service"

    model_config = SettingsConfigDict(env_file=None, case_sensitive=False)


# Spring's flat property keys → our snake_case Settings field names.
_PROPERTY_KEY_MAP: dict[str, str] = {
    "eureka.client.serviceurl.defaultzone": "eureka_url",
    "watermark.app-key": "watermark_app_key",
    "auth-server.url": "auth_server_url",
    "ai-service.url": "ai_service_url",
    "subscription-service.url": "subscription_service_url",
    "logging.level.root": "log_level",
}

_RETRY_DELAYS_S: tuple[float, ...] = (1.0, 2.0, 4.0, 8.0, 16.0)


def _fetch_property_sources(config_server_url: str, profile: str = "default") -> dict[str, str]:
    """Pull merged properties for `application` + this service from config-server.

    Returns flattened {dotted.key: stringified_value}. On any failure returns {} —
    we want the service to keep booting on legacy defaults rather than crash-loop
    when config-server is reachable-but-broken or genuinely down.
    """
    base = config_server_url.rstrip("/")
    paths = [f"/application/{profile}", f"/{APPLICATION_NAME}/{profile}"]

    merged: dict[str, str] = {}
    for path in paths:
        url = f"{base}{path}"
        body = _fetch_with_retry(url)
        if body is None:
            # One profile being unreachable shouldn't lose properties from the
            # other — keep merging what we got.
            continue
        for source in reversed(body.get("propertySources", [])):
            for key, value in (source.get("source") or {}).items():
                merged[key.lower()] = str(value)
    return merged


def _fetch_with_retry(url: str) -> dict | None:
    last_error: Exception | None = None
    for delay in _RETRY_DELAYS_S:
        try:
            with httpx.Client(timeout=5.0) as client:
                response = client.get(url)
            if response.status_code == 200:
                return response.json()
            last_error = RuntimeError(f"HTTP {response.status_code}")
        except httpx.HTTPError as exc:
            last_error = exc
        logger.info("config-server %s not ready (%s); retrying in %.0fs", url, last_error, delay)
        time.sleep(delay)
    logger.warning("Giving up on config-server %s: %s", url, last_error)
    return None


_MAX_PLACEHOLDER_PASSES = 16


def _resolve_property(raw: str) -> str:
    """Spring config values often contain ${VAR:default} placeholders. Resolve them
    against the process environment so e.g. ${EUREKA_URL:...} respects the same env
    var the docker-compose file sets.

    Cap iteration count to defang cyclic placeholders (${A} where A=${A}).
    """
    if "${" not in raw:
        return raw
    result = raw
    for _ in range(_MAX_PLACEHOLDER_PASSES):
        if "${" not in result:
            return result
        start = result.index("${")
        end = result.find("}", start)
        if end == -1:
            break
        token = result[start + 2 : end]
        name, _, default = token.partition(":")
        value = os.environ.get(name, default)
        result = result[:start] + value + result[end + 1 :]
    logger.warning("Placeholder resolution gave up after %d passes: %r", _MAX_PLACEHOLDER_PASSES, raw)
    return result


def _apply_config_overrides(properties: dict[str, str]) -> None:
    """Project relevant Spring properties onto our env BEFORE Settings reads them,
    so env-var precedence (set by the operator) naturally wins."""
    for spring_key, settings_field in _PROPERTY_KEY_MAP.items():
        if spring_key not in properties:
            continue
        env_name = settings_field.upper()
        if env_name in os.environ:
            continue  # operator-provided env wins over config-server
        os.environ[env_name] = _resolve_property(properties[spring_key])


def get_settings() -> Settings:
    """Bootstrap: fetch from config-server (if configured) then build Settings."""
    config_server_url = os.environ.get("CONFIG_SERVER_URL", "http://config-server:8888")
    if config_server_url:
        properties = _fetch_property_sources(config_server_url)
        if properties:
            _apply_config_overrides(properties)
            logger.info(
                "Loaded %d properties from config-server %s",
                len(properties), config_server_url,
            )
    settings = Settings()
    _enforce_app_key_safety(settings)
    return settings


def _enforce_app_key_safety(settings: Settings) -> None:
    if settings.watermark_app_key != DEFAULT_DEV_APP_KEY:
        return
    if os.environ.get("WATERMARK_DEV_MODE", "").lower() == "true":
        logger.warning(
            "WATERMARK_APP_KEY is the public dev default. "
            "Running because WATERMARK_DEV_MODE=true — do NOT use in production."
        )
        return
    raise InsecureDefaultAppKeyError(
        "WATERMARK_APP_KEY is set to the public dev default. "
        "Set a real secret via env or config-server, or set WATERMARK_DEV_MODE=true "
        "to acknowledge the risk in a development environment."
    )
