"""Per-launch owner health HTTP is completely separated from signed phone API."""
from fastapi.testclient import TestClient
import pytest

from laptopwatch.owner_health import create_owner_health_app


TOKEN = "b7" * 32


def test_secret_is_required_and_never_returned():
    with pytest.raises(ValueError):
        create_owner_health_app("development")
    with TestClient(create_owner_health_app(TOKEN), client=('127.0.0.1', 52977)) as client:
        assert client.get("/health").status_code == 401
        assert client.get("/health", headers={"Authorization": "Bearer " + "a" * 64}).status_code == 401
        challenge = "ab" * 16
        result = client.get("/health", headers={
            "Authorization": "Bearer " + TOKEN,
            "X-LaptopWatch-Challenge": challenge,
        })
        assert result.status_code == 200
        assert result.json() == {"healthy": True}
        assert TOKEN not in result.text
        assert result.headers["cache-control"] == "no-store"
        import hashlib
        import hmac
        proof = hmac.new(TOKEN.encode("ascii"), challenge.encode("ascii"),
                         hashlib.sha256).hexdigest()
        assert result.headers["x-laptopwatch-health-proof"] == proof
        assert client.get("/health", headers={"Authorization": "Bearer " + TOKEN}).status_code == 400
        assert client.get("/health", headers={
            "Authorization": "Bearer " + TOKEN,
            "X-LaptopWatch-Challenge": "z" * 32,
        }).status_code == 400


def test_owner_health_channel_has_zero_controls():
    with TestClient(create_owner_health_app(TOKEN), client=('127.0.0.1', 52977)) as client:
        headers = {"Authorization": "Bearer " + TOKEN}
        for path in ("/api/lock", "/api/camera/frame", "/docs", "/openapi.json", "/health?x=1"):
            assert client.get(path, headers=headers).status_code == 404
        assert client.post("/health", headers=headers).status_code == 404

def test_external_network_cannot_use_bearer():
    with TestClient(create_owner_health_app(TOKEN), client=('192.0.2.15', 50123)) as client:
        result = client.get('/health', headers={'Authorization': 'Bearer ' + TOKEN})
        assert result.status_code == 403
        assert TOKEN not in result.text
