from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from typing import Any
from unittest.mock import patch

from app.config import Settings
from app.provider.funasr_provider import FunAsrProvider


def settings(*, punctuation_model: str | None = None) -> Settings:
    return Settings(
        provider="funasr",
        model_name="iic/SenseVoiceSmall",
        vad_model_name="fsmn-vad",
        punctuation_model_name=punctuation_model,
        speaker_model_name=None,
        device="cpu",
        disable_update=True,
        batch_size_seconds=20,
        transcription_timeout_seconds=600,
        max_upload_bytes=1024 * 1024,
        temp_directory=Path(tempfile.gettempdir()),
    )


class StubModel:
    def __init__(self, result: dict[str, Any]) -> None:
        self.result = result
        self.generate_kwargs: dict[str, Any] | None = None

    def generate(self, **kwargs: Any) -> list[dict[str, Any]]:
        self.generate_kwargs = kwargs
        return [self.result]


class FunAsrProviderSegmentationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.provider = FunAsrProvider(settings())

    def test_valid_sentence_info_produces_multiple_segments(self) -> None:
        segments = self.provider._sentence_segments(
            [
                {
                    "text": "第一句。",
                    "start": 120,
                    "end": 920,
                    "spk": 0,
                    "confidence": 0.91,
                },
                {
                    "sentence": "Second sentence!",
                    "start": 1100,
                    "end": 1980,
                    "spk": None,
                    "confidence": None,
                },
            ],
            include_speaker=True,
            duration_ms=2500,
        )

        self.assertEqual(2, len(segments))
        self.assertEqual([1, 2], [segment.order for segment in segments])
        self.assertEqual((120, 920), (segments[0].start_ms, segments[0].end_ms))
        self.assertEqual("speaker-0", segments[0].speaker)
        self.assertEqual("Second sentence!", segments[1].text)
        self.assertIsNone(segments[1].speaker)
        self.assertIsNone(segments[1].confidence)

    def test_timestamp_words_use_real_boundaries_for_multiple_segments(self) -> None:
        result = {
            "text": "你好。 Hello world!",
            "sentence_info": [],
            "words": ["你", "好", "。", "Hello", "world", "!"],
            "timestamp": [
                [100, 220],
                [230, 360],
                [360, 410],
                [1400, 1650],
                [1660, 1930],
                [1930, 1990],
            ],
        }

        segments = self.provider._timestamp_segments(
            result, result["text"], 2500
        )

        self.assertEqual(2, len(segments))
        self.assertEqual((100, 410), (segments[0].start_ms, segments[0].end_ms))
        self.assertEqual("你好。", segments[0].text)
        self.assertEqual((1400, 1990), (segments[1].start_ms, segments[1].end_ms))
        self.assertEqual("Hello world!", segments[1].text)
        self.assertEqual([1, 2], [segment.order for segment in segments])

    def test_dict_timestamps_keep_fun_asr_nano_seconds_as_real_boundaries(self) -> None:
        result = {
            "text": "中 English。",
            "timestamps": [
                {"token": "中", "start_time": 0.15, "end_time": 0.30},
                {"token": "English", "start_time": 0.31, "end_time": 0.75},
                {"token": "。", "start_time": 0.75, "end_time": 0.80},
            ],
        }

        segments = self.provider._timestamp_segments(
            result, result["text"], 1000
        )

        self.assertEqual(1, len(segments))
        self.assertEqual((150, 800), (segments[0].start_ms, segments[0].end_ms))
        self.assertEqual("中 English。", segments[0].text)

    def test_paraformer_space_separated_raw_text_maps_to_timestamps(self) -> None:
        result = {
            "text": "你好。再见！",
            "raw_text": "你 好 。 再 见 ！",
            "timestamp": [
                [100, 200], [210, 320], [320, 360],
                [1000, 1120], [1130, 1260], [1260, 1300],
            ],
        }

        segments = self.provider._timestamp_segments(
            result, result["text"], 1500
        )

        self.assertEqual(2, len(segments))
        self.assertEqual(["你好。", "再见！"], [s.text for s in segments])
        self.assertEqual([(100, 360), (1000, 1300)], [
            (s.start_ms, s.end_ms) for s in segments
        ])

    def test_unreliable_text_mapping_keeps_only_coarse_timestamp_range(self) -> None:
        result = {
            "timestamp": [[200, 400], [1000, 1300]],
            "words": ["mapping", "does-not-match"],
        }

        segments = self.provider._timestamp_segments(
            result, "authoritative full text", 1500
        )

        self.assertEqual(1, len(segments))
        self.assertEqual((200, 1300), (segments[0].start_ms, segments[0].end_ms))
        self.assertEqual("authoritative full text", segments[0].text)

    def test_invalid_empty_and_backward_sentences_are_filtered(self) -> None:
        segments = self.provider._sentence_segments(
            [
                {"text": "", "start": 0, "end": 100},
                {"text": "negative", "start": -1, "end": 100},
                {"text": "zero", "start": 200, "end": 200},
                {"text": "有效一", "start": 1000, "end": 1500},
                {"text": "倒序", "start": 500, "end": 900},
                {"text": "有效二", "start": 1900, "end": 2500},
            ],
            include_speaker=False,
            duration_ms=2200,
        )

        self.assertEqual(["有效一", "有效二"], [s.text for s in segments])
        self.assertEqual([1, 2], [s.order for s in segments])
        self.assertEqual((1900, 2200), (segments[1].start_ms, segments[1].end_ms))

    def test_missing_optional_speaker_and_confidence_do_not_drop_segment(self) -> None:
        segments = self.provider._sentence_segments(
            [{"text": "保留片段", "start": 0, "end": 500}],
            include_speaker=True,
            duration_ms=1000,
        )

        self.assertEqual(1, len(segments))
        self.assertIsNone(segments[0].speaker)
        self.assertIsNone(segments[0].confidence)

    def test_no_time_information_uses_full_text_fallback_and_logs_source(self) -> None:
        provider = FunAsrProvider(settings(punctuation_model="ct-punc"))
        model = StubModel({"key": "test", "text": "完整文字"})
        provider._model = model

        audio_path = Path("standardized.wav")
        with patch.object(
            provider, "_validate_standardized_wav", return_value=1000
        ):
            with self.assertLogs(
                "app.provider.funasr_provider", level="INFO"
            ) as logs:
                response = provider.transcribe(audio_path, "zh", False)

        self.assertEqual(1, len(response.segments))
        self.assertEqual((0, 1000), (
            response.segments[0].start_ms,
            response.segments[0].end_ms,
        ))
        self.assertEqual("完整文字", response.full_text)
        self.assertTrue(any(
            "source=FULL_TEXT_FALLBACK" in line for line in logs.output
        ))
        self.assertIsNotNone(model.generate_kwargs)
        self.assertTrue(model.generate_kwargs["output_timestamp"])
        self.assertTrue(model.generate_kwargs["return_raw_text"])
        self.assertTrue(model.generate_kwargs["sentence_timestamp"])


if __name__ == "__main__":
    unittest.main()
