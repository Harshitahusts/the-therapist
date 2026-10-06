from pathlib import Path

import pytest

from app.knowledge import chunk_markdown, ingest_directory, parse_source, retrieve
from app.models import KnowledgeChunk, KnowledgeDocument

from .conftest import auth

SOURCES = Path(__file__).resolve().parents[2] / "knowledge" / "sources"


def test_every_source_has_licence_metadata():
    files = list(SOURCES.glob("*.md"))
    assert files
    for f in files:
        doc = parse_source(f)
        assert doc.meta["license"]
        assert doc.meta.get("redistribution_allowed") in ("true", "false")


def test_missing_licence_is_rejected(tmp_path):
    (tmp_path / "x.md").write_text("---\ntitle: X\nauthor: Y\n---\nBody")
    with pytest.raises(ValueError):
        parse_source(tmp_path / "x.md")


def test_chunking_respects_headings_and_size():
    body = "# A\n\n" + "\n\n".join(["word " * 60] * 10) + "\n\n## B\n\nshort"
    chunks = chunk_markdown(body, max_chars=700)
    assert all(len(text) <= 700 for _, text in chunks)
    assert chunks[-1] == ("B", "short")
    assert {sec for sec, _ in chunks} == {"A", "B"}


def test_ingest_is_idempotent_and_retrieval_returns_sources(app, fake_ai):
    with app.state.db.SessionLocal() as db:
        first = ingest_directory(db, fake_ai, SOURCES)
        assert first["documents"] == len(list(SOURCES.glob("*.md")))
        assert first["chunks"] > first["documents"]
        second = ingest_directory(db, fake_ai, SOURCES)
        assert second == {"documents": 0, "skipped": first["documents"], "chunks": 0}
        assert db.query(KnowledgeChunk).count() == first["chunks"]
        assert db.query(KnowledgeDocument).filter_by(redistribution_allowed=False).count() == 0

        hits = retrieve(db, fake_ai, "slow breathing 5-4-3-2-1 grounding for a panic attack", k=3, min_score=0.0)
        assert hits
        assert hits[0]["source"]["title"] == "Grounding and breathing for anxiety and panic"
        assert hits[0]["source"]["license"] == "CC BY 4.0"


def test_retrieve_knowledge_tool(app, client, fake_ai):
    with app.state.db.SessionLocal() as db:
        ingest_directory(db, fake_ai, SOURCES)
    cid = client.post("/v1/realtime/session", headers=auth()).json()["conversation_id"]
    r = client.post(f"/v1/conversations/{cid}/tools/retrieve_knowledge",
                    json={"arguments": {"query": "can't sleep, lying awake at night with a racing mind"}}, headers=auth())
    passages = r.json()["passages"]
    assert passages and passages[0]["source"]["title"] == "Sleep habits"


def test_every_source_is_listed_in_licenses_md():
    licenses = (SOURCES.parents[1] / "LICENSES.md").read_text()
    for f in SOURCES.glob("*.md"):
        assert f"`{f.name}`" in licenses, f"{f.name} missing from LICENSES.md"
