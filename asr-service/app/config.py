from __future__ import annotations

import os
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Optional


def optional_model_name(value: str | None) -> str | None:
    if value is None:
        return None

    value = value.strip()
    if not value or value.lower() in {
        "none",
        "null",
        "disabled",
        "off",
        "false",
    }:
        return None

    return value


def _env_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _env_int(name: str, default: int, minimum: int) -> int:
    value = int(os.getenv(name, str(default)))
    if value < minimum:
        raise ValueError(f"{name} must be greater than or equal to {minimum}")
    return value


@dataclass(frozen=True, slots=True)
class Settings:
    provider: str
    model_name: str
    vad_model_name: Optional[str]
    punctuation_model_name: Optional[str]
    speaker_model_name: Optional[str]
    device: str
    disable_update: bool
    batch_size_seconds: int
    transcription_timeout_seconds: int
    max_upload_bytes: int
    temp_directory: Path

    @classmethod
    def from_environment(cls) -> "Settings":
        provider = os.getenv("ASR_PROVIDER", "funasr").strip().lower()
        if provider != "funasr":
            raise ValueError("ASR_PROVIDER currently supports only 'funasr'")
        temp_directory = Path(
            os.getenv(
                "ASR_TEMP_DIR",
                str(Path(tempfile.gettempdir()) / "audio-agent-asr"),
            )
        ).expanduser().resolve()
        return cls(
            provider=provider,
            model_name=os.getenv(
                "ASR_MODEL", "iic/SenseVoiceSmall"
            ).strip(),
            vad_model_name=optional_model_name(
                os.getenv("ASR_VAD_MODEL", "fsmn-vad")
            ),
            punctuation_model_name=optional_model_name(
                os.getenv("ASR_PUNC_MODEL")
            ),
            speaker_model_name=optional_model_name(
                os.getenv("ASR_SPEAKER_MODEL")
            ),
            device=os.getenv("ASR_DEVICE", "cpu").strip(),
            disable_update=_env_bool("ASR_DISABLE_UPDATE", True),
            batch_size_seconds=_env_int(
                "ASR_BATCH_SIZE_SECONDS", 20, 1
            ),
            transcription_timeout_seconds=_env_int(
                "ASR_TRANSCRIPTION_TIMEOUT_SECONDS", 600, 1
            ),
            max_upload_bytes=_env_int(
                "ASR_MAX_UPLOAD_BYTES", 1_073_741_824, 1
            ),
            temp_directory=temp_directory,
        )
