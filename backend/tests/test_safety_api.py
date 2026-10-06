from app.ai import ModerationResult

from .conftest import auth

A = auth("user-a", "a@example.com")


def conv(client, region="IN"):
    client.put("/v1/me/settings", json={"crisis_region": region}, headers=A)
    return client.post("/v1/realtime/session", headers=A).json()["conversation_id"]


def turn(client, cid, text):
    r = client.post(f"/v1/conversations/{cid}/turns", json={"text": text}, headers=A)
    assert r.status_code == 200
    return r.json()


def test_low_risk_turn(client):
    cid = conv(client)
    body = turn(client, cid, "Work was stressful today and I'm tired.")
    assert body == {"level": "LOW", "categories": [], "guidance": None, "interrupt": False, "resources": None}


def test_high_risk_turn_interrupts_and_shows_regional_resources(client):
    cid = conv(client, "IN")
    body = turn(client, cid, "Honestly I want to kill myself")
    assert body["level"] == "HIGH"
    assert body["interrupt"] is True
    assert body["resources"]["emergency"] == "112"
    assert body["resources"]["lines"][0]["phone"] == "14416"
    assert "14416" in body["guidance"]
    assert "promise" in body["guidance"]  # instructs the model NOT to ask for promises


def test_immediate_risk(client):
    cid = conv(client, "US")
    body = turn(client, cid, "I took all my pills an hour ago")
    assert body["level"] == "IMMEDIATE"
    assert "911" in body["guidance"]


def test_moderate_turn_gets_guidance_without_interrupt(client):
    cid = conv(client)
    body = turn(client, cid, "Sometimes I wish I could just disappear")
    assert body["level"] == "MODERATE"
    assert body["interrupt"] is False
    assert body["resources"] is None
    assert body["guidance"]


def test_moderation_can_raise_but_not_lower(client, fake_ai):
    cid = conv(client)
    fake_ai.moderation = ModerationResult(flagged=True, categories={"self-harm/intent": True})
    assert turn(client, cid, "i just want it to stop for good")["level"] == "HIGH"


def test_safety_events_store_level_not_text(client):
    cid = conv(client)
    turn(client, cid, "I want to end my life")
    events = client.get("/v1/me/export", headers=A).json()["safety_events"]
    assert events[0]["level"] == "HIGH"
    assert "end my life" not in str(events)
