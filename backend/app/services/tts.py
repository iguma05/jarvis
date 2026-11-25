from __future__ import annotations

import base64
from typing import Protocol

from app.config import Settings, get_settings
from app.services.openai_client import get_openai_client


class SpeechSynthesizer(Protocol):
    def synthesize(self, text: str) -> bytes: ...


class OpenAISpeechSynthesizer:
    def __init__(self, settings: Settings | None = None) -> None:
        self._settings = settings or get_settings()

    def synthesize(self, text: str) -> bytes:
        client = get_openai_client()
        response = client.audio.speech.create(
            model=self._settings.openai_tts_model,
            voice=self._settings.openai_tts_voice,
            input=text,
        )
        return response.content


def synthesize_speech(
    text: str,
    *,
    synthesizer: SpeechSynthesizer | None = None,
) -> tuple[str, str]:
    synthesizer = synthesizer or OpenAISpeechSynthesizer()
    audio_bytes = synthesizer.synthesize(text)
    return base64.b64encode(audio_bytes).decode("ascii"), "audio/mpeg"
