from __future__ import annotations

import io
from typing import Protocol

from app.services.openai_client import get_openai_client


class Transcriber(Protocol):
    def transcribe(self, audio_bytes: bytes, filename: str) -> str: ...


class OpenAITranscriber:
    def transcribe(self, audio_bytes: bytes, filename: str) -> str:
        client = get_openai_client()
        buffer = io.BytesIO(audio_bytes)
        buffer.name = filename
        result = client.audio.transcriptions.create(
            model="whisper-1",
            file=buffer,
        )
        return (result.text or "").strip()


def transcribe_audio(
    audio_bytes: bytes,
    filename: str = "audio.wav",
    *,
    transcriber: Transcriber | None = None,
) -> str:
    if transcriber is None:
        transcriber = OpenAITranscriber()
    return transcriber.transcribe(audio_bytes, filename)
