# Джарвис v1

Домашняя прослойка: **Docker (FastAPI на Alpine)** + **Android (голос)**.  
Микрофон → STT (Whisper) → LLM → TTS → ответ на телефон. Ключ OpenAI только на сервере.

## Что нужно

1. [Docker Desktop](https://www.docker.com/products/docker-desktop/) на домашнем ПК (Windows/macOS/Linux)
2. Ключ **OpenAI API** (это не подписка ChatGPT Plus — отдельный billing на platform.openai.com)
3. Android Studio (для сборки приложения) и телефон в той же Wi‑Fi / Tailscale

## Быстрый старт бэкенда

```bash
cp .env.example .env
# впишите OPENAI_API_KEY=sk-...

docker compose up --build
```

Образ бэкенда: `python:3.14-alpine` ([`backend/Dockerfile`](backend/Dockerfile)).

Проверка:

```bash
curl http://localhost:5050/health
# {"status":"ok"}

curl -X POST http://localhost:5050/v1/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"привет"}'
```

Голос (WAV/MP3 файл):

```bash
curl -X POST http://localhost:5050/v1/voice \
  -F 'audio=@sample.wav;type=audio/wav'
```

API слушает `0.0.0.0:5050`.

### Переменные окружения

| Переменная | Описание |
|---|---|
| `OPENAI_API_KEY` | обязательный ключ |
| `OPENAI_MODEL` | по умолчанию `gpt-4o-mini` |
| `OPENAI_TTS_MODEL` | по умолчанию `tts-1` |
| `OPENAI_TTS_VOICE` | по умолчанию `alloy` |
| `JARVIS_SYSTEM_PROMPT` | характер ассистента (по умолчанию — Джарвис, ответы по-русски) |
| `CORS_ORIGINS` | `*` или список через запятую |
| `MAX_AUDIO_BYTES` | лимит размера аудио для `/v1/voice` (по умолчанию 10 МБ) |

## Android

1. Откройте папку [`android/`](android/) в Android Studio.
2. Sync Gradle, Run на устройство/эмулятор.
3. В поле **Адрес сервера** укажите IP вашего ПК, например `http://192.168.1.42:5050`.
4. Нажмите **Проверить связь** — индикатор должен стать «в сети».
5. Удерживайте микрофон → отпустите → дождитесь ответа голосом.
6. Текстовое поле внизу — запасной канал (`/v1/chat`).

### IP на Windows

```powershell
ipconfig
```

Ищите IPv4 адаптера Wi‑Fi / Ethernet (не `127.0.0.1`).

### Доступ не из дома

Не пробрасывайте порт 5050 в открытый интернет. Поднимите [Tailscale](https://tailscale.com/) на ПК и телефоне и используйте Tailscale-IP в поле **Адрес сервера**.

## Локальные тесты бэкенда (без Docker)

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
pytest -q
uvicorn app.main:app --host 0.0.0.0 --port 5050
```

## CI (GitHub Actions)

После push / PR в `main` или `master` запускается [`.github/workflows/ci.yml`](.github/workflows/ci.yml):

- **backend** — Python 3.14, `pytest`
- **android** — JDK 21, `./gradlew :app:assembleDebug` (Gradle; Maven для Android не используется)

Локально то же самое: `pytest` в `backend/`, сборка APK из `android/` в Android Studio или через `./gradlew :app:assembleDebug`.

## Структура

```
backend/     FastAPI на Alpine: /health, /v1/chat, /v1/voice (Python 3.14)
android/     Kotlin + Compose + Gradle (AGP 9.5), push-to-talk
.github/     GitHub Actions CI
docker-compose.yml
```

## Вне v1

Wake word, локальный Whisper/Ollama/Piper, стриминг WebSocket, Home Assistant, iOS.
