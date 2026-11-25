from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    message: str = Field(min_length=1)
    session_id: str | None = None


class ChatResponse(BaseModel):
    session_id: str
    reply: str


class VoiceResponse(BaseModel):
    session_id: str
    transcript: str
    reply: str
    audio_base64: str
    audio_mime: str = "audio/mpeg"


class HealthResponse(BaseModel):
    status: str
