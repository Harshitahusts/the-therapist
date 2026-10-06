from .conftest import auth, make_token


def test_health_needs_no_auth(client):
    assert client.get("/health").json() == {"ok": True}


def test_missing_token_rejected(client):
    assert client.get("/v1/me").status_code == 401


def test_bad_signature_rejected(client):
    token = make_token("user-a", secret="some-other-secret-that-is-long-enough")
    assert client.get("/v1/me", headers={"Authorization": f"Bearer {token}"}).status_code == 401


def test_expired_token_rejected(client):
    token = make_token("user-a", exp_in=-10)
    assert client.get("/v1/me", headers={"Authorization": f"Bearer {token}"}).status_code == 401


def test_wrong_audience_rejected(client):
    token = make_token("user-a", aud="something-else")
    assert client.get("/v1/me", headers={"Authorization": f"Bearer {token}"}).status_code == 401


def test_service_role_token_rejected(client):
    token = make_token("user-a", role="service_role")
    assert client.get("/v1/me", headers={"Authorization": f"Bearer {token}"}).status_code == 403


def test_first_request_creates_user_with_defaults(client):
    r = client.get("/v1/me", headers=auth("new-user", "new@example.com"))
    assert r.status_code == 200
    body = r.json()
    assert body["id"] == "new-user"
    assert body["email"] == "new@example.com"
    assert body["settings"]["memory_enabled"] is True
    assert body["settings"]["onboarding_completed"] is False


def test_onboarding_and_profile(client):
    r = client.post(
        "/v1/me/onboarding",
        json={"preferred_name": "Harshit", "memory_enabled": False, "timezone": "Asia/Kolkata", "crisis_region": "in"},
        headers=auth(),
    )
    assert r.status_code == 200
    body = r.json()
    assert body["profile"]["preferred_name"] == "Harshit"
    assert body["profile"]["timezone"] == "Asia/Kolkata"
    assert body["settings"] == {"memory_enabled": False, "crisis_region": "IN", "onboarding_completed": True}

    assert client.put("/v1/me/profile", json={"timezone": "Mars/Olympus"}, headers=auth()).status_code == 422
    assert client.put("/v1/me/settings", json={"crisis_region": "XX"}, headers=auth()).status_code == 422
    r = client.put("/v1/me/profile", json={"preferences": {"style": "concise"}}, headers=auth())
    assert r.json()["profile"]["preferences"] == {"style": "concise"}
