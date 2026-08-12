from __future__ import annotations

from abc import ABC, abstractmethod
from pathlib import Path

from app.schemas import TranscriptionResponse


class AsrProviderError(RuntimeError):
    """A provider failure safe to translate into an internal API error."""


class InvalidAudioError(AsrProviderError):
    """The supplied file is not a supported standardized audio file."""


class NoSpeechDetectedError(AsrProviderError):
    """The real model completed but did not recognize speech."""


class ModelNotLoadedError(AsrProviderError):
    """The provider cannot transcribe because its model failed to load."""


class SpeakerDiarizationNotConfiguredError(AsrProviderError):
    """Speaker diarization was requested without a speaker model."""


class BaseAsrProvider(ABC):
    @property
    @abstractmethod
    def name(self) -> str:
        raise NotImplementedError

    @property
    @abstractmethod
    def model_loaded(self) -> bool:
        raise NotImplementedError

    @property
    @abstractmethod
    def load_error(self) -> str | None:
        raise NotImplementedError

    @abstractmethod
    def load(self) -> None:
        raise NotImplementedError

    @abstractmethod
    def transcribe(
        self,
        audio_path: Path,
        language: str,
        enable_speaker_diarization: bool,
    ) -> TranscriptionResponse:
        raise NotImplementedError
