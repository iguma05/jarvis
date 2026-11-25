from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    openai_api_key: str = ""
    openai_model: str = "gpt-4o-mini"
    openai_tts_model: str = "tts-1"
    openai_tts_voice: str = "alloy"
    jarvis_system_prompt: str = (
        "Ты — Джарвис, краткий и полезный домашний помощник. "
        "Отвечай по-русски, коротко, если не просят подробнее."
    )
    cors_origins: str = "*"
    max_audio_bytes: int = 10 * 1024 * 1024


@lru_cache
def get_settings() -> Settings:
    return Settings()
