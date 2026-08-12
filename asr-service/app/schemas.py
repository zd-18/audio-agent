from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


class HealthResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    status: str
    provider: str
    model_loaded: bool = Field(alias="modelLoaded")
    load_error: str | None = Field(default=None, alias="loadError")


class SegmentResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    order: int = Field(ge=1)
    start_ms: int = Field(ge=0, alias="startMs")
    end_ms: int = Field(gt=0, alias="endMs")
    speaker: str | None = None
    text: str = Field(min_length=1)
    confidence: float | None = Field(default=None, ge=0, le=1)


class TranscriptionResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    language: str
    duration_ms: int = Field(gt=0, alias="durationMs")
    full_text: str = Field(min_length=1, alias="fullText")
    speaker_count: int | None = Field(
        default=None, ge=0, alias="speakerCount"
    )
    segments: list[SegmentResponse] = Field(min_length=1)
