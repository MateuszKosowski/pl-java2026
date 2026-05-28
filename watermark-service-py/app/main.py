import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse

from app.config import Settings, get_settings
from app.eureka import deregister, register
from app.routes import router as watermark_router


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings: Settings = app.state.settings
    await register(settings)
    try:
        yield
    finally:
        await deregister()


def create_app() -> FastAPI:
    # Configure logging before get_settings so the config-server bootstrap is visible.
    # Re-applied after settings load in case LOG_LEVEL differs from INFO.
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    settings = get_settings()
    logging.getLogger().setLevel(getattr(logging, settings.log_level.upper(), logging.INFO))
    app = FastAPI(
        title="watermark-service",
        version="0.2.0-py",
        lifespan=lifespan,
    )
    app.state.settings = settings

    @app.exception_handler(HTTPException)
    async def _http_exception_handler(request: Request, exc: HTTPException):
        if isinstance(exc.detail, dict):
            return JSONResponse(status_code=exc.status_code, content=exc.detail)
        return JSONResponse(status_code=exc.status_code, content={"detail": exc.detail})

    app.include_router(watermark_router)

    @app.get("/health")
    def health():
        return {"status": "UP"}

    return app


app = create_app()
