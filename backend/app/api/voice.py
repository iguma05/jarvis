from __future__ import annotations

from fastapi import APIRouter, File, Form, HTTPException, UploadFile

from app.config import get_settings
from app.schemas import VoiceResponse
from app.services.llm import chat_turn
from app.services.stt import transcribe_audio
from app.services.tts import synthesize_speech

router = APIRouter(prefix="/v1", tags=["voice"])

ALLOWED_AUDIO_TYPES = {
    "audio/wav",
    "audio/x-wav",
    "audio/wave",
    "audio/mpeg",
    "audio/mp3",
    "audio/mp4",
    "audio/m4a",
    "audio/webm",
    "application/octet-stream",
}


@router.post("/voice", response_model=VoiceResponse)
async def voice(
    audio: UploadFile = File(...),
    session_id: str | None = Form(default=None),
) -> VoiceResponse:
    settings = get_settings()
    content_type = (audio.content_type or "application/octet-stream").split(";")[0]
    if content_type not in ALLOWED_AUDIO_TYPES:
        raise HTTPException(
            status_code=400,
            detail=f"Неподдерживаемый тип аудио: {content_type}",
        )

    audio_bytes = await audio.read()
    if not audio_bytes:
        raise HTTPException(status_code=400, detail="Пустой аудиофайл")
    if len(audio_bytes) > settings.max_audio_bytes:
        raise HTTPException(status_code=400, detail="Файл слишком большой")

    filename = audio.filename or "audio.wav"

    try:
        transcript = transcribe_audio(audio_bytes, filename)
        if not transcript:
            raise HTTPException(status_code=400, detail="Не удалось распознать речь")
        sid, reply = chat_turn(transcript, session_id)
        audio_b64, audio_mime = synthesize_speech(reply)
    except HTTPException:
        raise
    except RuntimeError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(
            status_code=502, detail=f"Ошибка голосового пайплайна: {exc}"
        ) from exc


    return VoiceResponse(
        session_id=sid,
        transcript=transcript,
        reply=reply,
        audio_base64=audio_b64,
        audio_mime=audio_mime,
    )
