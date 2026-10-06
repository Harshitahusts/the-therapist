"""Ingest knowledge sources into the vector store.

    python -m scripts.ingest_knowledge [--dir PATH]
"""

import argparse
import json
from pathlib import Path

from app.ai import build_provider
from app.config import get_settings
from app.db import Database
from app.knowledge import ingest_directory

DEFAULT_DIR = Path(__file__).resolve().parents[2] / "knowledge" / "sources"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dir", type=Path, default=DEFAULT_DIR)
    args = parser.parse_args()
    settings = get_settings()
    db = Database(settings.database_url)
    with db.SessionLocal() as session:
        stats = ingest_directory(session, build_provider(settings), args.dir)
    print(json.dumps(stats))


if __name__ == "__main__":
    main()
