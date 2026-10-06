from alembic import context

from app.config import get_settings
from app.db import make_engine

config = context.config


def run_migrations_online() -> None:
    engine = make_engine(get_settings().database_url)
    with engine.connect() as connection:
        context.configure(connection=connection, target_metadata=None)
        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    raise SystemExit("Offline migrations are not supported; set DATABASE_URL and run `alembic upgrade head`.")
run_migrations_online()
