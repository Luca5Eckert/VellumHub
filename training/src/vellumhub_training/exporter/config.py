"""Credentials stay in memory and are never included in errors or manifests."""
from dataclasses import dataclass, field
import os


@dataclass(frozen=True)
class DatabaseConfig:
    url: str = field(repr=False)
    user: str | None = field(default=None, repr=False)
    password: str | None = field(default=None, repr=False)

    @classmethod
    def from_env(cls, prefix):
        url = os.environ.get(f"{prefix}_DB_URL")
        if not url:
            raise ValueError(f"Missing {prefix}_DB_URL")
        if url.startswith("jdbc:"):
            raise ValueError(f"{prefix}_DB_URL must use a PostgreSQL URI, not JDBC")
        return cls(url, os.environ.get(f"{prefix}_DB_USER"),
                   os.environ.get(f"{prefix}_DB_PASSWORD"))

    def connect(self):
        import psycopg
        from psycopg.rows import dict_row
        kwargs = {"row_factory": dict_row, "connect_timeout": 10}
        if self.user is not None:
            kwargs["user"] = self.user
        if self.password is not None:
            kwargs["password"] = self.password
        return psycopg.connect(self.url, **kwargs)
