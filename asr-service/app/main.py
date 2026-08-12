from __future__ import annotations

import asyncio
import logging
import os
import tempfile
from contextlib import asynccontextmanager
from pathlib import Path
from typing import AsyncIterator

from fastapi import FastAPI, File, Form, HTTPException, UploadFile, status

from app.config import Settings
from app.provider.base import (
    AsrProviderError,
    InvalidAudioError,
    ModelNotLoadedError,
    NoSpeechDetectedError,
    SpeakerDiarizationNotConfiguredError,
)
from app.provider.funasr_provider import FunAsrProvider
from app.schemas import HealthResponse, TranscriptionResponse
from app.service.transcription_service import (
    TranscriptionService,
    TranscriptionTimeoutError,
)

logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO").upper(),
    format="%(asctime)s %(levelname)s %(name)s - %(message)s",
)
LOGGER = logging.getLogger(__name__)
SETTINGS = Settings.from_environment()
PROVIDER = FunAsrProvider(SETTINGS)
SERVICE = TranscriptionService(
    PROVIDER, SETTINGS.transcription_timeout_seconds
)


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    SETTINGS.temp_directory.mkdir(parents=True, exist_ok=True)
    LOGGER.info("Starting internal ASR service provider=%s", PROVIDER.name)
    await asyncio.to_thread(PROVIDER.load)
    if PROVIDER.model_loaded:
        LOGGER.info("Internal ASR service is ready")
    else:
        LOGGER.error(
            "Internal ASR service is running without a loaded model load_error=%s",
            PROVIDER.load_error,
        )
    yield
    LOGGER.info("Internal ASR service stopped")


app = FastAPI(
    title="AudioAgent Internal ASR",
    docs_url=None,
    redoc_url=None,
    openapi_url=None,
    lifespan=lifespan,
)


@app.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    return HealthResponse(
        status=(
            "UP"
            if PROVIDER.model_loaded
            else "DOWN"
            if PROVIDER.load_error
            else "STARTING"
        ),
        provider=PROVIDER.name,
        modelLoaded=PROVIDER.model_loaded,
        loadError=PROVIDER.load_error,
    )


@app.post(
    "/internal/asr/transcribe",
    response_model=TranscriptionResponse,
)
async def transcribe(
    file: UploadFile = File(...),
    language: str = Form("zh"),
    enable_speaker_diarization: bool = Form(
        False, alias="enableSpeakerDiarization"
    ),
) -> TranscriptionResponse:
    normalized_language = language.strip().lower().replace("_", "-")
    if not normalized_language or len(normalized_language) > 32:
        raise HTTPException(
            status_code=422,
            detail="Invalid language code",
        )

    temp_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="wb",
            suffix=".wav",
            prefix="asr-",
            dir=SETTINGS.temp_directory,
            delete=False,
        ) as target:
            temp_path = Path(target.name)
            total = 0
            while chunk := await file.read(1024 * 1024):
                total += len(chunk)
                if total > SETTINGS.max_upload_bytes:
                    raise HTTPException(
                        status_code=413,
                        detail="Audio file exceeds the configured size limit",
                    )
                target.write(chunk)
        if total == 0:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Audio file is empty",
            )
        LOGGER.info(
            "ASR request accepted bytes=%s language=%s diarization=%s",
            total,
            normalized_language,
            enable_speaker_diarization,
        )
        result = await SERVICE.transcribe(
            temp_path,
            normalized_language,
            enable_speaker_diarization,
        )
        LOGGER.info(
            "ASR request completed durationMs=%s segmentCount=%s",
            result.duration_ms,
            len(result.segments),
        )
        return result
    except HTTPException:
        raise
    except InvalidAudioError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc
    except NoSpeechDetectedError as exc:
        raise HTTPException(
            status_code=422,
            detail="No recognizable speech was found in the audio",
        ) from exc
    except SpeakerDiarizationNotConfiguredError as exc:
        raise HTTPException(
            status_code=422,
            detail=str(exc),
        ) from exc
    except ModelNotLoadedError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=str(exc),
        ) from exc
    except TranscriptionTimeoutError as exc:
        raise HTTPException(
            status_code=status.HTTP_408_REQUEST_TIMEOUT,
            detail="Transcription timed out; try a shorter audio file",
        ) from exc
    except AsrProviderError as exc:
        LOGGER.error("ASR provider request failed: %s", type(exc).__name__)
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="ASR model could not complete the transcription",
        ) from exc
    finally:
        await file.close()
        if temp_path is not None:
            try:
                temp_path.unlink(missing_ok=True)
            except OSError:
                LOGGER.error("Failed to remove an ASR temporary file")
