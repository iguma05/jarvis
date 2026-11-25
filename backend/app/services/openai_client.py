from functools import lru_cache

from openai import OpenAI

from app.config import get_settings


@lru_cache
def get_openai_client() -> OpenAI:
    settings = get_settings()
    if not settings.openai_api_key:
        raise RuntimeError("Не задан OPENAI_API_KEY")
    return OpenAI(api_key=settings.openai_api_key)
