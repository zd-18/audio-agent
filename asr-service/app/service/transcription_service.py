from __future__ import annotations

import asyncio
from pathlib import Path

from app.provider.base import BaseAsrProvider
from app.schemas import TranscriptionResponse


class TranscriptionTimeoutError(RuntimeError):
    pass


class TranscriptionService:
    def __init__(
        self, provider: BaseAsrProvider, timeout_seconds: int
    ) -> None:
        self._provider = provider
        self._timeout_seconds = timeout_seconds

    async def transcribe(
        self,
        audio_path: Path,
        language: str,
        enable_speaker_diarization: bool,
    ) -> TranscriptionResponse:
        try:
            return await asyncio.wait_for(
                asyncio.to_thread(
                    self._provider.transcribe,
                    audio_path,
                    language,
                    enable_speaker_diarization,
                ),
                timeout=self._timeout_seconds,
            )
        except TimeoutError as exc:
            raise TranscriptionTimeoutError(
                "Transcription exceeded the configured timeout"
            ) from exc
