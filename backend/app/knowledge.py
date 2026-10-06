"""Retrieval-augmented psychoeducation.

Source documents live in /knowledge/sources as Markdown with a small front-matter
header recording provenance and licence. Only material we have the right to use
goes in there; see LICENSES.md.
"""

from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass
from pathlib import Path

from sqlalchemy import Float, bindparam, delete, select
from sqlalchemy.orm import Session

from .ai import AIProvider, cosine
from .db import Embedding
from .models import KnowledgeChunk, KnowledgeDocument

REQUIRED_META = ("title", "author", "license")


@dataclass
class SourceDoc:
    slug: str
    meta: dict
    body: str

    @property
    def content_hash(self) -> str:
        return hashlib.sha256((repr(sorted(self.meta.items())) + self.body).encode()).hexdigest()


def parse_source(path: Path) -> SourceDoc:
    text = path.read_text(encoding="utf-8")
    meta: dict = {}
    body = text
    if text.startswith("---"):
        _, header, body = text.split("---", 2)
        for line in header.strip().splitlines():
            if ":" in line:
                k, v = line.split(":", 1)
                meta[k.strip()] = v.strip().strip('"')
    missing = [k for k in REQUIRED_META if not meta.get(k)]
    if missing:
        raise ValueError(f"{path.name}: missing front-matter fields {missing}")
    return SourceDoc(slug=path.stem, meta=meta, body=body.strip())


def chunk_markdown(body: str, max_chars: int = 1200) -> list[tuple[str | None, str]]:
    """Split on headings, then pack paragraphs into chunks of at most max_chars."""
    sections: list[tuple[str | None, list[str]]] = [(None, [])]
    for block in re.split(r"\n\s*\n", body):
        block = block.strip()
        if not block:
            continue
        heading = re.match(r"^#{1,6}\s+(.*)$", block.splitlines()[0])
        if heading:
            sections.append((heading.group(1).strip(), []))
            rest = "\n".join(block.splitlines()[1:]).strip()
            if rest:
                sections[-1][1].append(rest)
        else:
            sections[-1][1].append(block)

    chunks: list[tuple[str | None, str]] = []
    for title, paras in sections:
        buf = ""
        for p in paras:
            if len(p) > max_chars:  # very long paragraph: hard split on sentences
                pieces = re.split(r"(?<=[.!?])\s+", p)
            else:
                pieces = [p]
            for piece in pieces:
                if buf and len(buf) + len(piece) + 2 > max_chars:
                    chunks.append((title, buf))
                    buf = ""
                buf = f"{buf}\n\n{piece}" if buf else piece
        if buf:
            chunks.append((title, buf))
    return chunks


def _truthy(v: str | None) -> bool:
    return str(v).strip().lower() in ("true", "yes", "1")


def ingest_directory(db: Session, ai: AIProvider, directory: Path) -> dict:
    stats = {"documents": 0, "skipped": 0, "chunks": 0}
    for path in sorted(directory.glob("*.md")):
        if path.name.lower() == "readme.md":
            continue
        doc = parse_source(path)
        existing = db.scalar(select(KnowledgeDocument).where(KnowledgeDocument.slug == doc.slug))
        if existing is not None and existing.content_hash == doc.content_hash:
            stats["skipped"] += 1
            continue
        chunks = chunk_markdown(doc.body)
        vectors = ai.embed([f"{doc.meta['title']} - {sec or ''}\n{text}" for sec, text in chunks])
        if existing is None:
            existing = KnowledgeDocument(slug=doc.slug)
            db.add(existing)
        else:
            db.execute(delete(KnowledgeChunk).where(KnowledgeChunk.document_id == existing.id))
        existing.title = doc.meta["title"]
        existing.author = doc.meta["author"]
        existing.publication = doc.meta.get("publication")
        existing.license = doc.meta["license"]
        existing.source_url = doc.meta.get("source_url")
        existing.redistribution_allowed = _truthy(doc.meta.get("redistribution_allowed"))
        existing.approaches = [a.strip() for a in doc.meta.get("approaches", "").split(",") if a.strip()]
        existing.content_hash = doc.content_hash
        db.flush()
        for i, ((section, text), vec) in enumerate(zip(chunks, vectors)):
            db.add(KnowledgeChunk(document_id=existing.id, chunk_index=i, section=section, content=text, embedding=vec))
        db.commit()
        stats["documents"] += 1
        stats["chunks"] += len(chunks)
    return stats


def retrieve(db: Session, ai: AIProvider, query: str, k: int = 3, min_score: float = 0.25) -> list[dict]:
    vector = ai.embed([query])[0]
    if db.get_bind().dialect.name == "postgresql":
        q = bindparam("q", vector, type_=Embedding(len(vector)))
        distance = KnowledgeChunk.embedding.op("<=>", return_type=Float)(q)
        rows = [(c, 1.0 - float(d)) for c, d in db.execute(
            select(KnowledgeChunk, distance).order_by(distance).limit(k)
        ).all()]
    else:
        all_rows = db.execute(select(KnowledgeChunk)).scalars().all()
        rows = sorted(((c, cosine(vector, c.embedding)) for c in all_rows), key=lambda r: r[1], reverse=True)[:k]
    out = []
    for chunk, score in rows:
        if score < min_score:
            continue
        doc = chunk.document
        out.append(
            {
                "content": chunk.content,
                "section": chunk.section,
                "score": round(score, 3),
                "source": {"title": doc.title, "author": doc.author, "license": doc.license, "url": doc.source_url},
            }
        )
    return out
