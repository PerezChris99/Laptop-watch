FROM python:3.13-slim

ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1 PIP_DISABLE_PIP_VERSION_CHECK=1
RUN groupadd -r laptopwatch && useradd --no-log-init -r -g laptopwatch laptopwatch
WORKDIR /app

# Keep the same dependency specification as local pip editable installs.
COPY services/location-relay/pyproject.toml /app/pyproject.toml
COPY services/location-relay/laptopwatch_relay/ /app/laptopwatch_relay/
# The shared signed-control verifier is the only desktop package surface
# consumed by this purpose-built one-owner relay container.
COPY desktop/src-python/laptopwatch/__init__.py /app/laptopwatch/__init__.py
COPY desktop/src-python/laptopwatch/security.py /app/laptopwatch/security.py
RUN python -m pip install --no-cache-dir '.[fcm]'

RUN mkdir /srv/laptopwatch && chown laptopwatch:laptopwatch /srv/laptopwatch
USER laptopwatch
EXPOSE 8080
CMD ["python","-m","laptopwatch_relay.cli","serve"]
