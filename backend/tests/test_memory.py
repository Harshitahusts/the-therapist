from .conftest import auth

A = auth("user-a", "a@example.com")
B = auth("user-b", "b@example.com")


def add(client, headers, content, category="goal"):
    return client.post("/v1/memories", json={"content": content, "category": category}, headers=headers)


def test_create_list_update_delete(client):
    r = add(client, A, "User is building personal AI projects.", "project")
    assert r.status_code == 201
    mid = r.json()["id"]
    assert r.json()["category"] == "project"
    assert [m["id"] for m in client.get("/v1/memories", headers=A).json()] == [mid]

    r = client.put(f"/v1/memories/{mid}", json={"content": "User is building an Android voice app."}, headers=A)
    assert r.status_code == 200 and r.json()["content"] == "User is building an Android voice app."

    assert client.delete(f"/v1/memories/{mid}", headers=A).status_code == 204
    assert client.get("/v1/memories", headers=A).json() == []
    assert client.delete(f"/v1/memories/{mid}", headers=A).status_code == 404


def test_unknown_category_becomes_other(client):
    assert add(client, A, "User likes walking by the sea.", "hobbies").json()["category"] == "other"


def test_near_duplicate_updates_instead_of_inserting(client):
    add(client, A, "User prefers concise practical advice.", "preference")
    add(client, A, "User prefers concise, practical advice", "preference")
    assert len(client.get("/v1/memories", headers=A).json()) == 1


def test_sensitive_content_is_never_stored(client):
    for content in [
        "User's password is hunter2",
        "User's card number is 4111 1111 1111 1111",
        "User's API key is sk-abcdefghijklmnopqrstuvwxyz",
    ]:
        assert add(client, A, content).status_code == 422
    assert client.get("/v1/memories", headers=A).json() == []


def test_memory_isolation_between_users(client):
    mid = add(client, A, "User is preparing for a marathon.").json()["id"]
    assert client.get("/v1/memories", headers=B).json() == []
    assert client.put(f"/v1/memories/{mid}", json={"content": "hijacked"}, headers=B).status_code == 404
    assert client.delete(f"/v1/memories/{mid}", headers=B).status_code == 404
    # B clearing their memories must not touch A's.
    assert client.delete("/v1/memories", headers=B).status_code == 204
    assert len(client.get("/v1/memories", headers=A).json()) == 1


def test_forget_everything(client):
    add(client, A, "User is learning to cook.")
    add(client, A, "User wants to move to Bangalore next year.", "plan")
    assert client.delete("/v1/memories", headers=A).status_code == 204
    assert client.get("/v1/memories", headers=A).json() == []


def test_cannot_add_when_memory_disabled(client):
    client.put("/v1/me/settings", json={"memory_enabled": False}, headers=A)
    assert add(client, A, "User likes tea.").status_code == 409


def test_memory_saved_even_if_embeddings_down(client, fake_ai):
    fake_ai.fail = True
    r = add(client, A, "User is training for a 10k run.")
    assert r.status_code == 201
