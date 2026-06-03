FROM python:3.12-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
        libgl1 \
        libglib2.0-0 \
        curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements-runtime.txt .
# `--no-deps invisible-watermark` skips the upstream torch dep so we don't pull
# both the CPU-only torch wheel (above) AND torch GPU build. PyWavelets and
# opencv-python-headless (the other transitives) are pinned in requirements-runtime.txt.
RUN pip install --no-cache-dir -r requirements-runtime.txt \
    && pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu torch==2.5.1+cpu \
    && pip install --no-cache-dir --no-deps invisible-watermark==0.2.0

COPY app/ ./app/

# Drop root: uvicorn binds the unprivileged port 8082, so no need for CAP_NET_BIND.
RUN useradd --create-home --uid 10001 app && chown -R app:app /app
USER app

EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -fsS http://localhost:8082/health || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8082"]
