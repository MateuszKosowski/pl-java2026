from __future__ import annotations

import logging
import socket

from py_eureka_client import eureka_client

from app.config import Settings

logger = logging.getLogger(__name__)

_INSTANCE_PORT = 8082


async def register(settings: Settings) -> None:
    if not settings.eureka_url:
        logger.info("EUREKA_URL empty — skipping Eureka registration")
        return
    host = settings.instance_hostname or socket.gethostname()
    base_url = f"http://{host}:{_INSTANCE_PORT}"
    try:
        await eureka_client.init_async(
            eureka_server=settings.eureka_url,
            app_name="WATERMARK-SERVICE",
            instance_port=_INSTANCE_PORT,
            instance_host=host,
            health_check_url=f"{base_url}/health",
            status_page_url=f"{base_url}/health",
            renewal_interval_in_secs=30,
            duration_in_secs=90,
        )
        logger.info("Registered with Eureka at %s as WATERMARK-SERVICE", settings.eureka_url)
    except Exception as exc:
        logger.error("Eureka registration failed: %s", exc)


async def deregister() -> None:
    try:
        await eureka_client.stop_async()
        logger.info("Deregistered from Eureka")
    except Exception as exc:
        logger.warning("Eureka deregister failed: %s", exc)
