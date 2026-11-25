from fastapi.testclient import TestClient

from app.main import create_app
from app.services import llm as llm_module
from app.services.sessions import SessionStore


class FakeCompleter:
    def complete(self, messages: list[dict]) -> str:
        user = next(m["content"] for m in reversed(messages) if m["role"] == "user")
        return f"echo:{user}"


def test_chat_returns_reply(monkeypatch) -> None:
    store = SessionStore()

    def fake_chat_turn(message, session_id=None, **kwargs):
        return llm_module.chat_turn(
            message,
            session_id,
            store=store,
            completer=FakeCompleter(),
        )

    monkeypatch.setattr("app.api.chat.chat_turn", fake_chat_turn)

    client = TestClient(create_app())
    response = client.post("/v1/chat", json={"message": "привет"})
    assert response.status_code == 200
    data = response.json()
    assert data["reply"] == "echo:привет"
    assert data["session_id"]

    sid = data["session_id"]
    response2 = client.post(
        "/v1/chat", json={"session_id": sid, "message": "ещё"}
    )
    assert response2.status_code == 200
    assert response2.json()["session_id"] == sid
    assert response2.json()["reply"] == "echo:ещё"
