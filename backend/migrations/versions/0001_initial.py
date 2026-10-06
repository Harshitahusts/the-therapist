"""Initial schema.

Revision ID: 0001
Revises:
Create Date: 2026-10-06
"""
import sqlalchemy as sa
from alembic import op

from app.config import get_settings
from app.db import Embedding

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None

DIM = get_settings().embedding_dim
TS = sa.DateTime(timezone=True)


def upgrade() -> None:
    is_pg = op.get_bind().dialect.name == "postgresql"
    if is_pg:
        op.execute("CREATE EXTENSION IF NOT EXISTS vector")

    op.create_table(
        "users",
        sa.Column("id", sa.String(64), primary_key=True),
        sa.Column("email", sa.String(320)),
        sa.Column("created_at", TS, nullable=False, server_default=sa.func.now()),
    )
    op.create_table(
        "user_profiles",
        sa.Column("user_id", sa.String(64), sa.ForeignKey("users.id", ondelete="CASCADE"), primary_key=True),
        sa.Column("name", sa.String(120)),
        sa.Column("preferred_name", sa.String(120)),
        sa.Column("timezone", sa.String(64), nullable=False, server_default="UTC"),
        sa.Column("language", sa.String(16), nullable=False, server_default="en"),
        sa.Column("preferences", sa.JSON, nullable=False),
        sa.Column("updated_at", TS, nullable=False, server_default=sa.func.now()),
    )
    op.create_table(
        "user_settings",
        sa.Column("user_id", sa.String(64), sa.ForeignKey("users.id", ondelete="CASCADE"), primary_key=True),
        sa.Column("memory_enabled", sa.Boolean, nullable=False, server_default=sa.true()),
        sa.Column("crisis_region", sa.String(8)),
        sa.Column("onboarding_completed_at", TS),
        sa.Column("updated_at", TS, nullable=False, server_default=sa.func.now()),
    )
    op.create_table(
        "conversations",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("user_id", sa.String(64), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("started_at", TS, nullable=False, server_default=sa.func.now()),
        sa.Column("ended_at", TS),
        sa.Column("duration_seconds", sa.Integer),
    )
    op.create_index("ix_conversations_user_id", "conversations", ["user_id"])
    op.create_table(
        "conversation_summaries",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("conversation_id", sa.String(36), sa.ForeignKey("conversations.id", ondelete="CASCADE"),
                  nullable=False, unique=True),
        sa.Column("user_id", sa.String(64), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("summary", sa.Text, nullable=False),
        sa.Column("important_points", sa.JSON, nullable=False),
        sa.Column("emotional_context", sa.String(200)),
        sa.Column("follow_up", sa.Text),
        sa.Column("created_at", TS, nullable=False, server_default=sa.func.now()),
    )
    op.create_index("ix_conversation_summaries_user_id", "conversation_summaries", ["user_id"])
    op.create_table(
        "memories",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("user_id", sa.String(64), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("conversation_id", sa.String(36), sa.ForeignKey("conversations.id", ondelete="SET NULL")),
        sa.Column("category", sa.String(32), nullable=False),
        sa.Column("content", sa.Text, nullable=False),
        sa.Column("confidence", sa.Float, nullable=False),
        sa.Column("source", sa.String(32), nullable=False),
        sa.Column("created_at", TS, nullable=False, server_default=sa.func.now()),
        sa.Column("updated_at", TS, nullable=False, server_default=sa.func.now()),
    )
    op.create_index("ix_memories_user_id", "memories", ["user_id"])
    op.create_table(
        "memory_embeddings",
        sa.Column("memory_id", sa.String(36), sa.ForeignKey("memories.id", ondelete="CASCADE"), primary_key=True),
        sa.Column("user_id", sa.String(64), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("model", sa.String(64), nullable=False),
        sa.Column("embedding", Embedding(DIM), nullable=False),
    )
    op.create_index("ix_memory_embeddings_user_id", "memory_embeddings", ["user_id"])
    op.create_table(
        "safety_events",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("user_id", sa.String(64), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("conversation_id", sa.String(36), sa.ForeignKey("conversations.id", ondelete="SET NULL")),
        sa.Column("level", sa.String(16), nullable=False),
        sa.Column("categories", sa.JSON, nullable=False),
        sa.Column("created_at", TS, nullable=False, server_default=sa.func.now()),
    )
    op.create_index("ix_safety_events_user_id", "safety_events", ["user_id"])
    op.create_table(
        "knowledge_documents",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("slug", sa.String(200), nullable=False, unique=True),
        sa.Column("title", sa.String(300), nullable=False),
        sa.Column("author", sa.String(300), nullable=False),
        sa.Column("publication", sa.String(300)),
        sa.Column("license", sa.String(200), nullable=False),
        sa.Column("source_url", sa.String(500)),
        sa.Column("redistribution_allowed", sa.Boolean, nullable=False, server_default=sa.false()),
        sa.Column("approaches", sa.JSON, nullable=False),
        sa.Column("content_hash", sa.String(64), nullable=False),
        sa.Column("created_at", TS, nullable=False, server_default=sa.func.now()),
    )
    op.create_table(
        "knowledge_chunks",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("document_id", sa.String(36), sa.ForeignKey("knowledge_documents.id", ondelete="CASCADE"),
                  nullable=False),
        sa.Column("chunk_index", sa.Integer, nullable=False),
        sa.Column("section", sa.String(300)),
        sa.Column("content", sa.Text, nullable=False),
        sa.Column("embedding", Embedding(DIM), nullable=False),
        sa.UniqueConstraint("document_id", "chunk_index"),
    )
    op.create_index("ix_knowledge_chunks_document_id", "knowledge_chunks", ["document_id"])

    if is_pg:
        # HNSW indexes support up to 2000 dimensions, which covers the default 1536.
        if DIM <= 2000:
            op.execute("CREATE INDEX ix_memory_embeddings_hnsw ON memory_embeddings "
                       "USING hnsw (embedding vector_cosine_ops)")
            op.execute("CREATE INDEX ix_knowledge_chunks_hnsw ON knowledge_chunks "
                       "USING hnsw (embedding vector_cosine_ops)")
        # Defence in depth for Supabase: the backend connects as the table owner,
        # but nothing should be readable through Supabase's public REST API.
        for table in ("users", "user_profiles", "user_settings", "conversations", "conversation_summaries",
                      "memories", "memory_embeddings", "safety_events", "knowledge_documents", "knowledge_chunks"):
            op.execute(f"ALTER TABLE {table} ENABLE ROW LEVEL SECURITY")


def downgrade() -> None:
    for table in ("knowledge_chunks", "knowledge_documents", "safety_events", "memory_embeddings", "memories",
                  "conversation_summaries", "conversations", "user_settings", "user_profiles", "users"):
        op.drop_table(table)
