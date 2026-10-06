from .conftest import auth

A = auth("user-a", "a@example.com")
B = auth("user-b", "b@example.com")


def start(client, headers=A):
    r = client.post("/v1/realtime/session", headers=headers)
    assert r.status_code == 200, r.text
    return r.json()


def onboard(client, headers=A, memory=True):
    client.post("/v1/me/onboarding", json={"preferred_name": "Harshit", "memory_enabled": memory,
                                            "timezone": "Asia/Kolkata", "crisis_region": "IN"}, headers=headers)


def test_session_returns_ephemeral_secret_and_never_the_api_key(client, fake_ai):
    onboard(client)
    body = start(client)
    assert body["client_secret"] == "ek_test"
    assert body["calls_url"].endswith("/realtime/calls")
    assert "conversation_id" in body
    session = fake_ai.sessions[-1]
    assert session["type"] == "realtime"
    assert {t["name"] for t in session["tools"]} >= {
        "get_relevant_memories", "save_memory", "update_memory", "delete_memory",
        "retrieve_knowledge", "get_user_profile", "get_recent_conversation_summaries",
    }
    assert session["audio"]["input"]["turn_detection"]["interrupt_response"] is True


def test_instructions_include_name_memories_and_safety_positioning(client, fake_ai):
    onboard(client)
    client.post("/v1/memories", json={"content": "User is starting a new PM role.", "category": "life_event"}, headers=A)
    start(client)
    instructions = fake_ai.sessions[-1]["instructions"]
    assert "Harshit" in instructions
    assert "User is starting a new PM role." in instructions
    assert "not a therapist" in instructions
    assert "Memory is ON" in instructions


def test_instructions_exclude_memories_when_disabled(client, fake_ai):
    onboard(client)
    client.post("/v1/memories", json={"content": "User has a cat called Miso."}, headers=A)
    client.put("/v1/me/settings", json={"memory_enabled": False}, headers=A)
    start(client)
    instructions = fake_ai.sessions[-1]["instructions"]
    assert "Miso" not in instructions
    assert "Memory is OFF" in instructions


def test_voice_service_failure_is_a_clean_503(client, fake_ai):
    fake_ai.fail = True
    r = client.post("/v1/realtime/session", headers=A)
    assert r.status_code == 503
    assert "unavailable" in r.json()["detail"]


def test_rate_limit(client):
    for _ in range(5):
        start(client)
    assert client.post("/v1/realtime/session", headers=A).status_code == 429
    # Another user is unaffected.
    assert client.post("/v1/realtime/session", headers=B).status_code == 200


def test_tool_memory_roundtrip(client):
    onboard(client)
    cid = start(client)["conversation_id"]
    r = client.post(f"/v1/conversations/{cid}/tools/save_memory",
                    json={"arguments": {"category": "life_event", "content": "User is starting a new product management role."}},
                    headers=A)
    assert r.json() == {"saved": True, "updated_existing": False}

    r = client.post(f"/v1/conversations/{cid}/tools/get_relevant_memories",
                    json={"arguments": {"query": "new product management role"}}, headers=A)
    assert r.json()["memories"][0]["content"] == "User is starting a new product management role."

    r = client.post(f"/v1/conversations/{cid}/tools/update_memory",
                    json={"arguments": {"about": "product management role", "new_content": "User started a new product management role in October."}},
                    headers=A)
    assert r.json() == {"ok": True}

    r = client.post(f"/v1/conversations/{cid}/tools/delete_memory",
                    json={"arguments": {"about": "new product management role"}}, headers=A)
    assert r.json()["ok"] is True
    assert client.get("/v1/memories", headers=A).json() == []


def test_tool_delete_does_not_guess(client):
    onboard(client)
    cid = start(client)["conversation_id"]
    client.post(f"/v1/conversations/{cid}/tools/save_memory",
                json={"arguments": {"category": "goal", "content": "User wants to run a half marathon."}}, headers=A)
    r = client.post(f"/v1/conversations/{cid}/tools/delete_memory",
                    json={"arguments": {"about": "favourite pizza topping"}}, headers=A)
    assert r.json()["ok"] is False
    assert len(client.get("/v1/memories", headers=A).json()) == 1


def test_tool_save_respects_memory_off_and_sensitive(client):
    onboard(client, memory=False)
    cid = start(client)["conversation_id"]
    r = client.post(f"/v1/conversations/{cid}/tools/save_memory",
                    json={"arguments": {"category": "goal", "content": "User wants to learn guitar."}}, headers=A)
    assert r.json()["saved"] is False
    client.put("/v1/me/settings", json={"memory_enabled": True}, headers=A)
    r = client.post(f"/v1/conversations/{cid}/tools/save_memory",
                    json={"arguments": {"category": "other", "content": "User's bank password is abc123"}}, headers=A)
    assert r.json()["saved"] is False


def test_profile_tool_and_unknown_tool(client):
    onboard(client)
    cid = start(client)["conversation_id"]
    r = client.post(f"/v1/conversations/{cid}/tools/get_user_profile", json={}, headers=A)
    assert r.json()["preferred_name"] == "Harshit"
    assert client.post(f"/v1/conversations/{cid}/tools/drop_tables", json={}, headers=A).status_code == 404


def test_conversation_isolation(client):
    cid = start(client, A)["conversation_id"]
    assert client.post(f"/v1/conversations/{cid}/tools/get_user_profile", json={}, headers=B).status_code == 404
    assert client.post(f"/v1/conversations/{cid}/turns", json={"text": "hi"}, headers=B).status_code == 404
    assert client.post(f"/v1/conversations/{cid}/end", json={}, headers=B).status_code == 404


def test_end_creates_summary_and_memories_and_discards_transcript(client, fake_ai):
    onboard(client)
    cid = start(client)["conversation_id"]
    transcript = [
        {"role": "assistant", "text": "Hey Harshit. How are you feeling today?"},
        {"role": "user", "text": "Nervous. I start my new PM job on Monday."},
    ]
    r = client.post(f"/v1/conversations/{cid}/end", json={"transcript": transcript}, headers=A)
    assert r.status_code == 200
    assert r.json()["processing"] is True
    assert "USER: Nervous. I start my new PM job on Monday." in fake_ai.summary_calls[-1]

    export = client.get("/v1/me/export", headers=A).json()
    assert export["conversation_summaries"][0]["summary"].startswith("User talked about feeling anxious")
    assert export["conversation_summaries"][0]["follow_up"] == "Ask how the first week went."
    assert [m["content"] for m in export["memories"]] == ["User is starting a new product management role."]
    assert export["memories"][0]["source"] == "summary_extraction"
    assert export["conversations"][0]["ended_at"] is not None
    # The raw transcript is not stored anywhere.
    assert "Monday" not in str(export)

    # Next session sees the summary.
    start(client)
    assert "Ask how the first week went." in fake_ai.sessions[-1]["instructions"]

    # Ending twice is harmless; tools are closed after end.
    assert client.post(f"/v1/conversations/{cid}/end", json={"transcript": transcript}, headers=A).json()["processing"] is False
    assert client.post(f"/v1/conversations/{cid}/tools/get_user_profile", json={}, headers=A).status_code == 409


def test_end_with_memory_off_stores_nothing(client, fake_ai):
    onboard(client, memory=False)
    cid = start(client)["conversation_id"]
    r = client.post(f"/v1/conversations/{cid}/end",
                    json={"transcript": [{"role": "user", "text": "I adopted a dog."}]}, headers=A)
    assert r.json()["processing"] is False
    assert fake_ai.summary_calls == []
    export = client.get("/v1/me/export", headers=A).json()
    assert export["memories"] == [] and export["conversation_summaries"] == []


def test_summary_failure_does_not_break_ending(client, fake_ai):
    onboard(client)
    cid = start(client)["conversation_id"]
    fake_ai.fail = True
    r = client.post(f"/v1/conversations/{cid}/end",
                    json={"transcript": [{"role": "user", "text": "Long day."}]}, headers=A)
    assert r.status_code == 200


def test_sensitive_extracted_memories_are_dropped(client, fake_ai):
    onboard(client)
    fake_ai.summary_response = {
        "summary": "User talked about work.",
        "memories": [
            {"category": "other", "content": "User's password is swordfish", "confidence": 0.9},
            {"category": "goal", "content": "User wants a calmer morning routine.", "confidence": 0.9},
            {"category": "other", "content": "User might like jazz.", "confidence": 0.2},
        ],
    }
    cid = start(client)["conversation_id"]
    client.post(f"/v1/conversations/{cid}/end", json={"transcript": [{"role": "user", "text": "work stuff"}]}, headers=A)
    assert [m["content"] for m in client.get("/v1/memories", headers=A).json()] == ["User wants a calmer morning routine."]


def test_delete_all_data_keeps_login_but_wipes_everything(client):
    onboard(client)
    cid = start(client)["conversation_id"]
    client.post("/v1/memories", json={"content": "User likes hiking."}, headers=A)
    client.post(f"/v1/conversations/{cid}/turns", json={"text": "I want to kill myself"}, headers=A)
    client.post(f"/v1/conversations/{cid}/end", json={"transcript": [{"role": "user", "text": "hi"}]}, headers=A)
    client.post("/v1/memories", json={"content": "User B likes chess."}, headers=B)

    assert client.delete("/v1/me/data", headers=A).status_code == 204
    export = client.get("/v1/me/export", headers=A).json()
    assert export["memories"] == []
    assert export["conversation_summaries"] == []
    assert export["conversations"] == []
    assert export["safety_events"] == []
    assert export["profile"]["preferred_name"] is None
    me = client.get("/v1/me", headers=A).json()
    assert me["settings"]["onboarding_completed"] is False
    # Other users untouched.
    assert len(client.get("/v1/memories", headers=B).json()) == 1


def test_delete_account(client):
    onboard(client)
    client.post("/v1/memories", json={"content": "User likes hiking."}, headers=A)
    assert client.delete("/v1/me", headers=A).status_code == 204
    # A fresh, empty user would be created on next login.
    assert client.get("/v1/memories", headers=A).json() == []
