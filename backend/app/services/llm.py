from __future__ import annotations

from typing import Any, Protocol

from app.config import Settings, get_settings
from app.services.openai_client import get_openai_client
from app.services.sessions import SessionStore, session_store


class ChatCompleter(Protocol):
    def complete(self, messages: list[dict[str, Any]]) -> str: ...


class OpenAIChatCompleter:
    def __init__(self, settings: Settings | None = None) -> None:
        self._settings = settings or get_settings()

    def complete(self, messages: list[dict[str, Any]]) -> str:
        client = get_openai_client()
        response = client.chat.completions.create(
            model=self._settings.openai_model,
            messages=messages,
        )
        content = response.choices[0].message.content
        return (content or "").strip()


def chat_turn(
    message: str,
    session_id: str | None = None,
    *,
    store: SessionStore | None = None,
    completer: ChatCompleter | None = None,
    settings: Settings | None = None,
) -> tuple[str, str]:
    settings = settings or get_settings()
    store = store or session_store
    completer = completer or OpenAIChatCompleter(settings)

    sid = store.ensure(session_id)
    store.append(sid, "user", message)

    history = store.get_messages(sid)
    messages: list[dict[str, Any]] = [
        {"role": "system", "content": settings.jarvis_system_prompt},
        *history,
    ]
    reply = completer.complete(messages)
    store.append(sid, "assistant", reply)
    return sid, reply
