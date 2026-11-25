from __future__ import annotations

from typing import Any, Protocol

from app.config import Settings, get_settings
from app.services.openai_client import get_openai_client
from app.services.sessions import SessionStore, session_store


class ChatCompleter(Protocol):
    def complete(self, messages: list[dict[str, Any]]) -> str: ...


class OpenAIChatCompleter:
    def complete(self, messages: list[dict[str, Any]]) -> str:
        settings = get_settings()
        client = get_openai_client()
        response = client.chat.completions.create(
            model=settings.openai_model,
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
    if settings is None:
        settings = get_settings()
    if store is None:
        store = session_store
    if completer is None:
        completer = OpenAIChatCompleter()

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
