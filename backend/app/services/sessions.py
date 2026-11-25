from __future__ import annotations

import threading
import uuid
from typing import Any


class SessionStore:
    """In-memory chat history keyed by session_id."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._sessions: dict[str, list[dict[str, Any]]] = {}

    def ensure(self, session_id: str | None) -> str:
        with self._lock:
            session_id = session_id or str(uuid.uuid4())
            self._sessions.setdefault(session_id, [])
            return session_id

    def get_messages(self, session_id: str) -> list[dict[str, Any]]:
        with self._lock:
            return list(self._sessions.get(session_id, []))

    def append(self, session_id: str, role: str, content: str) -> None:
        with self._lock:
            self._sessions.setdefault(session_id, []).append(
                {"role": role, "content": content}
            )


session_store = SessionStore()
