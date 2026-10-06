FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /srv/backend

COPY backend/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY backend/ .
COPY knowledge/ /srv/knowledge/

RUN useradd --create-home app && chown -R app /srv
USER app

ENV ENVIRONMENT=production PORT=8000
EXPOSE 8000
# Apply migrations, then serve. Ingestion is a separate, explicit step (see docs/DEPLOYMENT.md).
CMD alembic upgrade head && uvicorn app.main:app_factory --factory --host 0.0.0.0 --port ${PORT} --proxy-headers
