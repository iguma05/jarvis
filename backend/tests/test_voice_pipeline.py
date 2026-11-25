import base64

from fastapi.testclient import TestClient

from app.main import create_app
from app.services import llm as llm_module
from app.services.sessions import SessionStore


class FakeCompleter:
    def complete(self, messages: list[dict]) -> str:
        return "Привет от Джарвиса"


def test_voice_pipeline(monkeypatch) -> None:
    store = SessionStore()

    monkeypatch.setattr(
        "app.api.voice.transcribe_audio",
        lambda audio_bytes, filename="audio.wav": "what time is it",
    )
    monkeypatch.setattr(
        "app.api.voice.chat_turn",
        lambda message, session_id=None: llm_module.chat_turn(
            message,
            session_id,
            store=store,
            completer=FakeCompleter(),
        ),
    )
    monkeypatch.setattr(
        "app.api.voice.synthesize_speech",
        lambda text: (base64.b64encode(b"fake-mp3").decode("ascii"), "audio/mpeg"),
    )

    client = TestClient(create_app())
    response = client.post(
        "/v1/voice",
        files={"audio": ("clip.wav", b"RIFF....WAVEfmt ", "audio/wav")},
        data={},
    )
    assert response.status_code == 200
    data = response.json()
    assert data["transcript"] == "what time is it"
    assert data["reply"] == "Привет от Джарвиса"
    assert data["audio_mime"] == "audio/mpeg"
    assert base64.b64decode(data["audio_base64"]) == b"fake-mp3"
    assert data["session_id"]


def test_voice_rejects_empty_audio(monkeypatch) -> None:
    client = TestClient(create_app())
    response = client.post(
        "/v1/voice",
        files={"audio": ("clip.wav", b"", "audio/wav")},
    )
    assert response.status_code == 400
