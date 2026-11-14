FROM python:3.13-slim

ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1

RUN groupadd -r laptopwatch && useradd --no-log-init -r -g laptopwatch laptopwatch
WORKDIR /app
COPY desktop/src-python/pyproject.toml /app/desktop-pyproject.toml
COPY desktop/src-python/laptopwatch/__init__.py /app/laptopwatch/__init__.py
COPY desktop/src-python/laptopwatch/security.py /app/laptopwatch/security.py
COPY services/location-relay/laptopwatch_relay/ /app/laptopwatch_relay/

RUN pip install --no-cache-dir "fastapi>=0.115,<1" "uvicorn>=0.30,<1" \
    "cryptography>=44,<47" "pydantic>=2,<3"

RUN mkdir /srv/laptopwatch && chown laptopwatch:laptopwatch /srv/laptopwatch
USER laptopwatch
EXPOSE 8080
CMD ["python","-m","laptopwatch_relay.cli","serve"]
