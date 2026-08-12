from __future__ import annotations

import argparse
import json
import logging
from pathlib import Path

from app.config import Settings
from app.provider.funasr_provider import FunAsrProvider


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Run a real FunASR transcription and print metadata only."
    )
    parser.add_argument("audio", type=Path)
    parser.add_argument("--language", default="zh")
    args = parser.parse_args()

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s - %(message)s",
    )
    provider = FunAsrProvider(Settings.from_environment())
    provider.load()
    if not provider.model_loaded:
        raise RuntimeError(provider.load_error or "FunASR model did not load")

    result = provider.transcribe(
        args.audio.resolve(), args.language, False
    )
    print(json.dumps({
        "language": result.language,
        "durationMs": result.duration_ms,
        "fullTextLength": len(result.full_text),
        "segmentCount": len(result.segments),
        "segments": [
            {
                "order": segment.order,
                "startMs": segment.start_ms,
                "endMs": segment.end_ms,
                "textLength": len(segment.text),
                "speakerPresent": segment.speaker is not None,
                "confidencePresent": segment.confidence is not None,
            }
            for segment in result.segments
        ],
    }, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
