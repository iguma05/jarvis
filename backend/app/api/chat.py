from fastapi import APIRouter, HTTPException

from app.schemas import ChatRequest, ChatResponse
from app.services.llm import chat_turn

router = APIRouter(prefix="/v1", tags=["chat"])


@router.post("/chat", response_model=ChatResponse)
def chat(body: ChatRequest) -> ChatResponse:
    try:
        session_id, reply = chat_turn(body.message, body.session_id)
    except RuntimeError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001 — surface provider failures
        raise HTTPException(status_code=502, detail=f"Ошибка LLM: {exc}") from exc
    return ChatResponse(session_id=session_id, reply=reply)
