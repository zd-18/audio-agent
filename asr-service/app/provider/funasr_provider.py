from __future__ import annotations

import logging
import math
import platform
import re
import threading
import wave
from collections.abc import Mapping, Sequence
from importlib.metadata import PackageNotFoundError, version
from pathlib import Path
from typing import Any

from app.config import Settings
from app.provider.base import (
    AsrProviderError,
    BaseAsrProvider,
    InvalidAudioError,
    ModelNotLoadedError,
    NoSpeechDetectedError,
    SpeakerDiarizationNotConfiguredError,
)
from app.schemas import SegmentResponse, TranscriptionResponse

LOGGER = logging.getLogger(__name__)
SPECIAL_TOKEN_PATTERN = re.compile(r"<\|[^|>]+\|>")
SENTENCE_END_PATTERN = re.compile(r"[。！？!?；;]+[\"'”’）)\]]*$")
ASCII_WORD_PATTERN = re.compile(r"[A-Za-z0-9]")
TIMESTAMP_PAUSE_BOUNDARY_MS = 800

SENTENCE_INFO_SOURCE = "SENTENCE_INFO"
VAD_OR_TIMESTAMP_SOURCE = "VAD_OR_TIMESTAMP"
FULL_TEXT_FALLBACK_SOURCE = "FULL_TEXT_FALLBACK"


class FunAsrProvider(BaseAsrProvider):
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._model: Any | None = None
        self._load_error: str | None = None
        self._inference_lock = threading.Lock()

    @property
    def name(self) -> str:
        return "funasr"

    @property
    def model_loaded(self) -> bool:
        return self._model is not None

    @property
    def load_error(self) -> str | None:
        return self._load_error

    def load(self) -> None:
        if self._model is not None:
            return
        self._load_error = None
        LOGGER.info(
            "Loading FunASR pipeline pythonVersion=%s funasrVersion=%s "
            "modelscopeVersion=%s asrModel=%s vadModel=%s puncModel=%s "
            "speakerModel=%s device=%s disableUpdate=%s "
            "autoModelParameterKeys=%s generateParameters=%s",
            platform.python_version(),
            self._package_version("funasr"),
            self._package_version("modelscope"),
            self._model_log_value(self._settings.model_name),
            self._optional_model_log_value(self._settings.vad_model_name),
            self._optional_model_log_value(
                self._settings.punctuation_model_name
            ),
            self._optional_model_log_value(self._settings.speaker_model_name),
            self._settings.device,
            self._settings.disable_update,
            sorted(self._model_kwargs()),
            self._generate_log_parameters(),
        )
        try:
            from funasr import AutoModel

            model_kwargs = self._model_kwargs()
            self._model = AutoModel(**model_kwargs)
        except Exception as exc:
            self._model = None
            self._load_error = "FunASR model initialization failed"
            LOGGER.error(
                "FunASR pipeline loading failed asrModel=%s vadModel=%s "
                "puncModel=%s speakerModel=%s errorType=%s error=%s",
                self._model_log_value(self._settings.model_name),
                self._optional_model_log_value(
                    self._settings.vad_model_name
                ),
                self._optional_model_log_value(
                    self._settings.punctuation_model_name
                ),
                self._optional_model_log_value(
                    self._settings.speaker_model_name
                ),
                type(exc).__name__,
                self._safe_error_message(exc),
            )
            return
        LOGGER.info("FunASR model pipeline loaded successfully")

    def _model_kwargs(self) -> dict[str, Any]:
        model_kwargs: dict[str, Any] = {
            "model": self._settings.model_name,
            "device": self._settings.device,
            "disable_update": self._settings.disable_update,
        }
        if self._settings.vad_model_name:
            model_kwargs["vad_model"] = self._settings.vad_model_name
        if self._settings.punctuation_model_name:
            model_kwargs["punc_model"] = self._settings.punctuation_model_name
        if self._settings.speaker_model_name:
            model_kwargs["spk_model"] = self._settings.speaker_model_name
        return model_kwargs

    def _model_log_value(self, model_name: str) -> str:
        path = Path(model_name)
        if path.is_absolute() or model_name.startswith(("./", ".\\")):
            if (
                path.name.lower() in {"master", "main"}
                and path.parent.name == "snapshots"
            ):
                return path.parent.parent.name
            return path.name
        return model_name

    def _optional_model_log_value(self, model_name: str | None) -> str:
        return (
            self._model_log_value(model_name)
            if model_name
            else "disabled"
        )

    def _package_version(self, package_name: str) -> str:
        try:
            return version(package_name)
        except PackageNotFoundError:
            return "not-installed"

    def _safe_error_message(self, exc: Exception) -> str:
        return " ".join(str(exc).split())[:300] or "no error message"

    def _generate_kwargs(self, language: str) -> dict[str, Any]:
        return {
            "language": language,
            "batch_size_s": self._settings.batch_size_seconds,
            "use_itn": True,
            "output_timestamp": True,
            "return_raw_text": True,
            "sentence_timestamp": bool(
                self._settings.punctuation_model_name
            ),
        }

    def _generate_log_parameters(self) -> dict[str, Any]:
        return {
            "batch_size_s": self._settings.batch_size_seconds,
            "use_itn": True,
            "output_timestamp": True,
            "return_raw_text": True,
            "sentence_timestamp": bool(
                self._settings.punctuation_model_name
            ),
        }

    def transcribe(
        self,
        audio_path: Path,
        language: str,
        enable_speaker_diarization: bool,
    ) -> TranscriptionResponse:
        if (
            enable_speaker_diarization
            and not self._settings.speaker_model_name
        ):
            raise SpeakerDiarizationNotConfiguredError(
                "Speaker diarization requires ASR_SPEAKER_MODEL to be configured"
            )
        if self._model is None:
            raise ModelNotLoadedError("ASR model is not loaded")
        duration_ms = self._validate_standardized_wav(audio_path)
        try:
            with self._inference_lock:
                raw_results = self._model.generate(
                    input=str(audio_path),
                    **self._generate_kwargs(language),
                )
        except Exception as exc:
            raise AsrProviderError("FunASR inference failed") from exc

        result = self._first_result(raw_results)
        full_text = self._clean_text(result.get("text"))
        self._log_result_diagnostics(raw_results, result, full_text)
        sentence_info = result.get("sentence_info")
        segments = self._sentence_segments(
            sentence_info,
            enable_speaker_diarization,
            duration_ms,
        )
        segment_source = SENTENCE_INFO_SOURCE if segments else None
        if not full_text:
            full_text = self._join_text_parts(
                [segment.text for segment in segments]
            )
        if not full_text:
            full_text = self._timestamp_text(result)
        if not full_text:
            raise NoSpeechDetectedError("No speech was recognized in the audio")
        if not segments:
            segments = self._timestamp_segments(
                result, full_text, duration_ms
            )
            if segments:
                segment_source = VAD_OR_TIMESTAMP_SOURCE
        if not segments:
            segments = [
                SegmentResponse(
                    order=1,
                    startMs=0,
                    endMs=duration_ms,
                    speaker=None,
                    text=full_text,
                    confidence=self._confidence(result),
                )
            ]
            segment_source = FULL_TEXT_FALLBACK_SOURCE
            LOGGER.warning(
                "FunASR produced no usable sentence, VAD, or timestamp "
                "boundaries; emitting one coarse full-text segment "
                "source=%s durationMs=%s fullTextLength=%s",
                segment_source,
                duration_ms,
                len(full_text),
            )

        LOGGER.info(
            "FunASR segmentation completed source=%s fullTextLength=%s "
            "segmentCount=%s",
            segment_source,
            len(full_text),
            len(segments),
        )

        speakers = {
            segment.speaker for segment in segments if segment.speaker is not None
        }
        return TranscriptionResponse(
            language=language,
            durationMs=duration_ms,
            fullText=full_text,
            speakerCount=(len(speakers) if enable_speaker_diarization else None),
            segments=segments,
        )

    def _log_result_diagnostics(
        self,
        raw_results: Any,
        result: Mapping[str, Any],
        full_text: str,
    ) -> None:
        sentence_info = result.get("sentence_info")
        raw_timestamps = result.get(
            "timestamp", result.get("timestamps")
        )
        LOGGER.info(
            "FunASR result diagnostics rawType=%s topLevelKeys=%s "
            "resultCount=%s firstResultKeys=%s sentenceInfoPresent=%s "
            "sentenceInfoType=%s sentenceInfoCount=%s timestampPresent=%s "
            "timestampType=%s timestampCount=%s fullTextLength=%s",
            type(raw_results).__name__,
            sorted(raw_results) if isinstance(raw_results, Mapping) else [],
            self._sequence_count(raw_results),
            sorted(result),
            "sentence_info" in result,
            type(sentence_info).__name__,
            self._sequence_count(sentence_info),
            "timestamp" in result or "timestamps" in result,
            type(raw_timestamps).__name__,
            self._sequence_count(raw_timestamps),
            len(full_text),
        )

    def _sequence_count(self, value: Any) -> int:
        if isinstance(value, Sequence) and not isinstance(
            value, (str, bytes)
        ):
            return len(value)
        return 0

    def _validate_standardized_wav(self, path: Path) -> int:
        try:
            if not path.is_file() or path.stat().st_size <= 44:
                raise InvalidAudioError("Audio file is empty")
            with wave.open(str(path), "rb") as wav:
                channels = wav.getnchannels()
                sample_rate = wav.getframerate()
                sample_width = wav.getsampwidth()
                frame_count = wav.getnframes()
            if channels != 1 or sample_rate != 16_000 or sample_width != 2:
                raise InvalidAudioError(
                    "Audio must be mono 16 kHz 16-bit PCM WAV"
                )
            duration_ms = round(frame_count * 1000 / sample_rate)
            if duration_ms <= 0:
                raise InvalidAudioError("Audio duration is invalid")
            return duration_ms
        except InvalidAudioError:
            raise
        except (OSError, EOFError, wave.Error) as exc:
            raise InvalidAudioError("Uploaded file is not a valid WAV audio") from exc

    def _first_result(self, raw_results: Any) -> Mapping[str, Any]:
        if (
            not isinstance(raw_results, Sequence)
            or isinstance(raw_results, (str, bytes))
            or not raw_results
            or not isinstance(raw_results[0], Mapping)
        ):
            raise AsrProviderError("FunASR returned an invalid response")
        return raw_results[0]

    def _sentence_segments(
        self,
        raw_sentences: Any,
        include_speaker: bool,
        duration_ms: int,
    ) -> list[SegmentResponse]:
        if not isinstance(raw_sentences, Sequence) or isinstance(
            raw_sentences, (str, bytes)
        ):
            return []
        segments: list[SegmentResponse] = []
        previous_start_ms = -1
        for sentence in raw_sentences:
            if not isinstance(sentence, Mapping):
                continue
            text = self._clean_text(
                sentence.get("sentence", sentence.get("text"))
            )
            start_ms = self._non_negative_int(
                sentence.get(
                    "start", sentence.get("start_ms", sentence.get("startMs"))
                )
            )
            end_ms = self._non_negative_int(
                sentence.get(
                    "end", sentence.get("end_ms", sentence.get("endMs"))
                )
            )
            if not text or start_ms is None or end_ms is None or end_ms <= start_ms:
                continue
            if start_ms < previous_start_ms or start_ms >= duration_ms:
                continue
            end_ms = min(end_ms, duration_ms)
            if end_ms <= start_ms:
                continue
            speaker = (
                self._speaker_label(
                    sentence.get("spk", sentence.get("speaker"))
                )
                if include_speaker
                else None
            )
            segments.append(
                SegmentResponse(
                    order=len(segments) + 1,
                    startMs=start_ms,
                    endMs=end_ms,
                    speaker=speaker,
                    text=text,
                    confidence=self._confidence(sentence),
                )
            )
            previous_start_ms = start_ms
        return segments

    def _timestamp_segments(
        self,
        result: Mapping[str, Any],
        full_text: str,
        duration_ms: int,
    ) -> list[SegmentResponse]:
        token_spans = self._timestamp_token_spans(result, duration_ms)
        reference_text = self._clean_text(
            result.get("raw_text")
        ) or full_text
        if token_spans and self._texts_correspond(
            self._join_text_parts([span[0] for span in token_spans]),
            reference_text,
        ):
            groups: list[list[tuple[str, int, int]]] = []
            current: list[tuple[str, int, int]] = []
            for span in token_spans:
                if (
                    current
                    and span[1] - current[-1][2]
                    >= TIMESTAMP_PAUSE_BOUNDARY_MS
                ):
                    groups.append(current)
                    current = []
                current.append(span)
                if SENTENCE_END_PATTERN.search(span[0]):
                    groups.append(current)
                    current = []
            if current:
                groups.append(current)

            confidence = self._confidence(result)
            segments = []
            for group in groups:
                text = self._join_text_parts([span[0] for span in group])
                if not text:
                    continue
                segments.append(
                    SegmentResponse(
                        order=len(segments) + 1,
                        startMs=group[0][1],
                        endMs=group[-1][2],
                        speaker=None,
                        text=text,
                        confidence=confidence,
                    )
                )
            if segments:
                return segments

        bounds = self._timestamp_bounds(result, duration_ms)
        if bounds is None:
            return []
        return [
            SegmentResponse(
                order=1,
                startMs=bounds[0],
                endMs=bounds[1],
                speaker=None,
                text=full_text,
                confidence=self._confidence(result),
            )
        ]

    def _timestamp_token_spans(
        self, result: Mapping[str, Any], duration_ms: int
    ) -> list[tuple[str, int, int]]:
        detailed = result.get("timestamps")
        if isinstance(detailed, Sequence) and not isinstance(
            detailed, (str, bytes)
        ) and detailed and all(isinstance(item, Mapping) for item in detailed):
            spans = self._mapping_timestamp_spans(detailed, duration_ms)
            if spans:
                return spans

        timestamps = result.get("timestamp")
        words = result.get("words")
        if not isinstance(words, Sequence) or isinstance(words, (str, bytes)):
            timestamp_text = self._clean_text(
                result.get("raw_text")
            ) or self._clean_text(result.get("text"))
            split_words = timestamp_text.split()
            if (
                isinstance(timestamps, Sequence)
                and not isinstance(timestamps, (str, bytes))
                and len(split_words) == len(timestamps)
            ):
                words = split_words
        if (
            not isinstance(timestamps, Sequence)
            or isinstance(timestamps, (str, bytes))
            or not isinstance(words, Sequence)
            or isinstance(words, (str, bytes))
            or len(timestamps) != len(words)
            or not timestamps
        ):
            return []

        spans: list[tuple[str, int, int]] = []
        previous_start_ms = -1
        for raw_word, raw_timestamp in zip(words, timestamps):
            text = self._clean_text(raw_word)
            if not text:
                continue
            bounds = self._millisecond_pair(raw_timestamp, duration_ms)
            if bounds is None or bounds[0] < previous_start_ms:
                return []
            spans.append((text, bounds[0], bounds[1]))
            previous_start_ms = bounds[0]
        return spans

    def _mapping_timestamp_spans(
        self, timestamps: Sequence[Any], duration_ms: int
    ) -> list[tuple[str, int, int]]:
        spans: list[tuple[str, int, int]] = []
        previous_start_ms = -1
        for item in timestamps:
            if not isinstance(item, Mapping):
                return []
            text = self._clean_text(
                item.get("token", item.get("word", item.get("text")))
            )
            if not text:
                continue
            start_ms = self._seconds_to_milliseconds(item.get("start_time"))
            end_ms = self._seconds_to_milliseconds(item.get("end_time"))
            bounds = self._validated_bounds(start_ms, end_ms, duration_ms)
            if bounds is None or bounds[0] < previous_start_ms:
                return []
            spans.append((text, bounds[0], bounds[1]))
            previous_start_ms = bounds[0]
        return spans

    def _timestamp_bounds(
        self, result: Mapping[str, Any], duration_ms: int
    ) -> tuple[int, int] | None:
        candidates: list[tuple[int, int]] = []
        for key in ("timestamp", "timestamps"):
            raw_timestamps = result.get(key)
            if not isinstance(raw_timestamps, Sequence) or isinstance(
                raw_timestamps, (str, bytes)
            ):
                continue
            for item in raw_timestamps:
                if isinstance(item, Mapping):
                    start_ms = self._seconds_to_milliseconds(
                        item.get("start_time")
                    )
                    end_ms = self._seconds_to_milliseconds(
                        item.get("end_time")
                    )
                    pair = self._validated_bounds(
                        start_ms, end_ms, duration_ms
                    )
                else:
                    pair = self._millisecond_pair(item, duration_ms)
                if pair is not None:
                    candidates.append(pair)
        if not candidates:
            return None
        start_ms = min(pair[0] for pair in candidates)
        end_ms = max(pair[1] for pair in candidates)
        return (
            (start_ms, end_ms)
            if end_ms > start_ms
            else None
        )

    def _millisecond_pair(
        self, value: Any, duration_ms: int
    ) -> tuple[int, int] | None:
        if (
            not isinstance(value, Sequence)
            or isinstance(value, (str, bytes))
            or len(value) < 2
        ):
            return None
        return self._validated_bounds(
            self._non_negative_int(value[0]),
            self._non_negative_int(value[1]),
            duration_ms,
        )

    def _validated_bounds(
        self,
        start_ms: int | None,
        end_ms: int | None,
        duration_ms: int,
    ) -> tuple[int, int] | None:
        if (
            start_ms is None
            or end_ms is None
            or end_ms <= start_ms
            or start_ms >= duration_ms
        ):
            return None
        end_ms = min(end_ms, duration_ms)
        return (
            (start_ms, end_ms)
            if end_ms > start_ms
            else None
        )

    def _seconds_to_milliseconds(self, value: Any) -> int | None:
        try:
            parsed = int(round(float(value) * 1000))
        except (TypeError, ValueError):
            return None
        return parsed if parsed >= 0 else None

    def _timestamp_text(self, result: Mapping[str, Any]) -> str:
        spans = self._timestamp_token_spans(result, 2**63 - 1)
        return self._join_text_parts([span[0] for span in spans])

    def _texts_correspond(self, first: str, second: str) -> bool:
        normalize = lambda value: re.sub(r"\s+", "", value)
        return bool(first) and normalize(first) == normalize(second)

    def _join_text_parts(self, parts: Sequence[str]) -> str:
        result = ""
        for raw_part in parts:
            part = self._clean_text(raw_part)
            if not part:
                continue
            if result and self._needs_space(result[-1], part[0]):
                result += " "
            result += part
        return result.strip()

    def _needs_space(self, previous: str, following: str) -> bool:
        return bool(
            ASCII_WORD_PATTERN.fullmatch(previous)
            or ASCII_WORD_PATTERN.fullmatch(following)
        ) and following not in ",.!?;:，。！？；："

    def _clean_text(self, value: Any) -> str:
        if value is None:
            return ""
        if isinstance(value, Sequence) and not isinstance(
            value, (str, bytes)
        ):
            return self._join_text_parts([str(item) for item in value])
        if isinstance(value, Mapping):
            return ""
        return SPECIAL_TOKEN_PATTERN.sub("", str(value)).strip()

    def _speaker_label(self, value: Any) -> str | None:
        if value is None or str(value).strip() == "":
            return None
        label = str(value).strip()
        return label if label.lower().startswith("speaker") else f"speaker-{label}"

    def _confidence(self, value: Mapping[str, Any]) -> float | None:
        raw = value.get("confidence", value.get("score"))
        try:
            confidence = float(raw)
        except (TypeError, ValueError):
            return None
        return confidence if math.isfinite(confidence) and 0 <= confidence <= 1 else None

    def _non_negative_int(self, value: Any) -> int | None:
        try:
            parsed = int(round(float(value)))
        except (TypeError, ValueError):
            return None
        return parsed if parsed >= 0 else None
