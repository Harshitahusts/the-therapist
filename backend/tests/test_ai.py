import json

import httpx
import pytest

from app.ai import AIProviderError, OpenAIProvider, cosine, hash_embedding
from app.config import Settings


def provider(handler):
    settings = Settings(openai_api_key="sk-test", embedding_dim=8)
    client = httpx.Client(base_url=settings.openai_base_url, transport=httpx.MockTransport(handler),
                          headers={"Authorization": "Bearer sk-test"})
    return OpenAIProvider(settings, client=client)


def test_realtime_secret_request_shape():
    seen = {}

    def handler(request: httpx.Request):
        seen["path"] = request.url.path
        seen["body"] = json.loads(request.content)
        seen["auth"] = request.headers["authorization"]
        return httpx.Response(200, json={"value": "ek_123", "expires_at": 1700000000, "session": {}})

    secret = provider(handler).create_realtime_secret({"type": "realtime", "model": "gpt-realtime"})
    assert seen["path"] == "/v1/realtime/client_secrets"
    assert seen["body"]["session"]["model"] == "gpt-realtime"
    assert seen["body"]["expires_after"]["seconds"] <= 600
    assert seen["auth"] == "Bearer sk-test"
    assert secret.value == "ek_123"


def test_embeddings_are_reordered_by_index():
    def handler(request):
        return httpx.Response(200, json={"data": [{"index": 1, "embedding": [2.0]}, {"index": 0, "embedding": [1.0]}]})

    assert provider(handler).embed(["a", "b"]) == [[1.0], [2.0]]


def test_http_errors_become_provider_errors_without_leaking_content():
    def handler(request):
        return httpx.Response(500, json={"error": "boom"})

    with pytest.raises(AIProviderError) as exc:
        provider(handler).complete_json("sys", "very private user text")
    assert "private" not in str(exc.value)


def test_network_failure_is_provider_error():
    def handler(request):
        raise httpx.ConnectError("no route")

    with pytest.raises(AIProviderError):
        provider(handler).embed(["x"])


def test_moderation_failure_degrades_to_none():
    def handler(request):
        return httpx.Response(503)

    assert provider(handler).moderate("text") is None


def test_hash_embedding_is_normalised_and_similar_for_similar_text():
    a = hash_embedding("anxious about my new job", 256)
    b = hash_embedding("anxious about the new job", 256)
    c = hash_embedding("recipe for banana bread", 256)
    assert abs(sum(x * x for x in a) - 1.0) < 1e-9
    assert cosine(a, b) > cosine(a, c)
