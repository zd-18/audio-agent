# Audio processing execution API

This backend executes only an immutable `CONFIRMED` confirmation snapshot.
It never rebuilds execution steps from the current processing plan and never
overwrites the source `audio_file` row or MinIO object.

## Database migration

Run `sql/11_create_audio_processing_execution.sql` in Navicat against the
`audio_agent` database before enabling the endpoints.

## Endpoints

All endpoints require a positive `X-User-Id` header. Snowflake IDs are JSON
strings in responses.

### Create or return the existing execution

`POST /api/audio-processing/executions`

```json
{
  "confirmationId": "2079000000000000001"
}
```

The confirmation must be `CONFIRMED`, owned by the caller, belong to a
successful analysis task, and contain at least one accepted step. Repeating
the request returns the row protected by
`uk_execution_confirmation_id`; it does not publish another message.

### Query an execution

`GET /api/audio-processing/executions/{executionId}`

Returns status, stage, monotonic progress, step outcomes, retry count,
failure details, and `resultFileId`. `steps` is always an array.

### Query the execution for a task's latest confirmed result

`GET /api/audio-analysis/tasks/{taskId}/processing-execution`

### Manually retry a terminal failure

`POST /api/audio-processing/executions/{executionId}/retry`

Only `FAILED` and `DEAD_LETTER` executions without a result file can be
retried. The existing execution and execution-step rows are reset and reused.

## Pipeline and operation policy

This stage executes only two operation types. Historical plan values remain
readable, but an accepted legacy operation is rejected before an execution
row is created.

Execution order is fixed in `AudioProcessingPipeline`:

1. Remove the exact confirmed `startMs` / `endMs` ranges.
2. Normalize the remaining whole audio with two-pass `loudnorm`.

| operationType | Execution behavior |
| --- | --- |
| `TRIM_SEGMENT` | `atrim` + `asetpts` + `concat` over retained ranges; removes exactly `startMs` through `endMs` |
| `NORMALIZE_VOLUME` | Two-pass `loudnorm` using measured first-pass JSON |

Overlapping trim ranges are merged and deduplicated. `NORMALIZE_VOLUME` may
occur at most once.

Output is configured as WAV PCM 16-bit. Successful output is uploaded to:

`repair/{userId}/{executionId}/result.wav`

The new `audio_file` row has `file_role=REPAIR_RESULT`,
`source_file_id=<original file ID>`, fresh SHA-256 and ffprobe metadata, and
`file_status=AVAILABLE`. Existing download and playback endpoints can access
it through `resultFileId`.

## RabbitMQ topology

- Exchange: `audio.processing.exchange`
- Main queue: `audio.processing.queue`
- Retry queue: `audio.processing.retry.queue`
- Dead-letter queue: `audio.processing.dlq`
- Main routing key: `audio.processing.execute`
- Retry routing key: `audio.processing.retry`
- Dead-letter routing key: `audio.processing.dead`

Every main, retry, and dead-letter payload contains only `executionId`.
The retry queue uses TTL and dead-letters back to the main routing key.

## Apifox verification

1. Run migration 11 and start MySQL, MinIO, RabbitMQ, FFmpeg, and the backend.
2. Complete the existing upload → analysis → processing plan → confirmation
   flow, accepting at least one step, then confirm it.
3. Call create with that `confirmationId` and record `executionId`.
4. Repeat create; verify the same ID is returned and the main queue receives
   no second message.
5. Poll the execution query until `SUCCESS`; verify progress is 100 and
   `resultFileId` is non-null.
6. Query/download/play `resultFileId`; verify the source file ID and source
   object still work and were not modified.
7. Call the task-level query and verify it returns the same execution.
8. For failure testing, temporarily stop MinIO before execution, restore it,
   observe delayed retries, and use manual retry only after `FAILED` or
   `DEAD_LETTER`.
9. Use another `X-User-Id`; create/query/retry must be denied.

In RabbitMQ Management, inspect all four resources, bindings, durable flags,
the retry TTL, retry queue dead-letter target, ready/unacked counts, and DLQ
messages. In MinIO, verify results under the deterministic `repair/` prefix
and confirm the original object remains unchanged.
