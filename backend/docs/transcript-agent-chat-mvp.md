# Transcript Agent Chat Backend MVP

## Outcome and original gap

The project already had stable transcript and transcript-segment data, user
ownership checks, and DeepSeek-backed content analysis. The `/agent` route was
still a placeholder because there was no conversation/message/citation data
model, no chat API, and the existing DeepSeek client was tied to the fixed
content-analysis JSON contract.

This MVP adds a synchronous, transcript-grounded, multi-turn backend. A
conversation is bound to `transcriptId`; transcription and analysis task IDs
are never used as the Agent resource key. All API-facing BIGINT IDs are
serialized as strings.

## Change map

- `backend/sql/18_create_agent_chat.sql`: three Agent tables, constraints,
  indexes, and foreign keys.
- `com.audioagent.ai`: domain-neutral chat messages, request/response, HTTP
  client, timeout support, token usage, response format, and classified
  exceptions.
- `DeepSeekClientAdapter` and `ContentAnalysisConfiguration`: existing content
  analysis now uses the generic client through its original `DeepSeekClient`
  contract and still forces `json_object` output.
- `com.audioagent.agent`: controller, services, entities, mappers, DTO/VO,
  prompt, context selection, response parsing, citation validation, and Agent
  error handling.
- `application-dev.yml`: configurable Agent model, token, history, question,
  transcript-context, and timeout limits. No API key is stored in source.
- `com.audioagent.agent` tests and `agent-test-schema.sql`: unit and H2
  persistence/concurrency coverage with a mocked `AiChatClient`.

No frontend, ASR, transcript source segment, SSE, WebSocket, embedding, vector
database, external search, or tool-calling behavior is added.

## Database

Run `sql/18_create_agent_chat.sql` after the existing numbered migrations.

| Table | Purpose | Important guarantees |
|---|---|---|
| `agent_conversation` | User-owned conversation bound to one transcript | Multiple conversations per transcript; ACTIVE/ARCHIVED; user/transcript indexes; last successful sequence cursor |
| `agent_message` | USER and ASSISTANT history | Unique `(conversation_id, sequence_no)` and `(conversation_id, client_request_id)`; PROCESSING/SUCCESS/FAILED lifecycle; token and failure metadata |
| `agent_message_citation` | Auditable source citations | Stable transcript/segment IDs, database timing, verbatim quote, unique citation order; no JSON-only citation storage |

## APIs

All endpoints require the existing Sa-Token Bearer authentication.

| Method | Path | Description |
|---|---|---|
| POST | `/api/agent/conversations` | Create an ACTIVE conversation for an owned transcript |
| GET | `/api/agent/conversations` | Page current user's conversations; filter by transcript/status |
| GET | `/api/agent/conversations/{conversationId}` | Conversation detail without loading all messages |
| GET | `/api/agent/conversations/{conversationId}/messages` | Page messages in ascending sequence order; assistant messages include citations |
| POST | `/api/agent/conversations/{conversationId}/messages` | Persist question, call AI synchronously, validate/persist answer and citations |

## AI transport compatibility

`AiChatClient` accepts a list of system/user/assistant messages, model,
temperature, max tokens, optional response format, and per-request timeout. It
returns content, model, and prompt/completion/total token usage. The HTTP client
never logs or persists the API key and classifies authentication, payment,
rate-limit, timeout, invalid-request, invalid-response, and availability
errors.

Content analysis keeps its old `DeepSeekClient` interface through
`DeepSeekClientAdapter`. Its model/temperature/max-token settings, JSON response
format, not-configured category, balance category, prompts, repair flow,
validator, and `EvidenceQuoteResolver` remain unchanged.

## Prompt, transcript selection, and citations

The independent prompt version is `transcript-chat-v1`. It tells the model to
use only supplied transcript segments, return no chain-of-thought, admit
`根据当前文字稿无法确定。` when evidence is insufficient, return no more than
five citations, and emit only the lightweight Agent JSON contract.

Short transcripts use every segment when both configured limits allow it. For
long transcripts, selection is deterministic:

1. Extract Chinese bigrams plus English/numeric keywords from the question.
2. Score original segments by keyword overlap.
3. Prioritize highest-scoring segments and add one neighbor on each side.
4. Deduplicate and sort by the database `segmentOrder`.
5. Enforce both `context-max-chars` and `context-max-segments` without splitting
   or modifying a source segment.

Every prompt segment contains `segmentId`, `segmentOrder`, `startMs`, `endMs`,
and original text. Logs contain only IDs, counts, selected orders, and sizes,
not the full transcript.

The model supplies only `segmentId` and `quote`. Validation accepts exact or
normalized whitespace/punctuation matching, then always cuts the final quote
back from the original database string. A citation is rejected when its segment
is missing, outside the current user/transcript/context, has empty source text,
or cannot produce a continuous source substring. `startMs` and `endMs` always
come from the database.

## Idempotency, ordering, and transactions

The first short transaction locks the conversation row, checks ownership and
status, checks `(conversationId, clientRequestId)`, allocates two sequence
numbers, saves USER as SUCCESS, and saves ASSISTANT as PROCESSING. The row lock
and unique constraints protect concurrent sequence allocation and duplicate
requests.

Transcript/history construction, JSON parsing, citation validation, and the
DeepSeek HTTP call happen after that transaction commits. A second short
transaction updates ASSISTANT to SUCCESS, inserts citations, and advances the
conversation cursor only when the completed message has a greater sequence
than the existing cursor. This prevents out-of-order concurrent AI completions
from moving `lastMessageId` backwards.

On failure, USER remains committed and ASSISTANT is updated to FAILED in a new
short transaction with a public failure code/message. A repeated
`clientRequestId` returns the existing pair in SUCCESS, PROCESSING, or FAILED
state and never invokes AI again.

History contains at most the configured recent SUCCESS messages, in sequence
order, and excludes the current question, FAILED assistant responses, and
PROCESSING messages.

## Error and observability contract

Agent errors occupy `40901-40912`, including conversation not found/access
denied/status invalid, transcript not found, empty/long message, duplicate,
context failure, AI unavailable/timeout, invalid response, and invalid citation.
Public responses do not expose prompts, SQL, stack traces, configuration, or
provider response bodies.

Structured logs include the relevant user/conversation/message/transcript IDs,
`clientRequestId`, question length, history and context counts, selected segment
orders, model latency and token counts, citation match counts, and sanitized
failure classifications. They do not log API keys, full questions, full
answers, or full transcripts.

## Automated verification

The test AI is a Mockito fake; no real DeepSeek request is made.

```text
mvn -q -Dtest='com.audioagent.agent.**' test          PASS
mvn -q -Dtest='com.audioagent.contentanalysis.**' test PASS
mvn test                                              BUILD SUCCESS
Tests run: 397, Failures: 0, Errors: 0, Skipped: 0
```

Coverage includes ownership, multiple conversations per transcript, paging,
first and multi-turn chat, strict sequence numbers, concurrent allocation,
out-of-order-safe cursor updates, idempotency, failure persistence, token usage,
context selection/limits, Chinese/English/numeric relevance, exact/normalized
citations, source quote recovery, cross-user/transcript rejection, and existing
content-analysis/transcription regression tests.

## PowerShell manual acceptance

### 1. Apply SQL and start the backend

```powershell
Set-Location D:\mycode\audio-agent\backend

# The mysql client prompts for the database password.
mysql -h localhost -P 3306 -u root -p audio_agent `
  -e "source D:/mycode/audio-agent/backend/sql/18_create_agent_chat.sql"

$env:DEEPSEEK_API_KEY = '<your DeepSeek API key>'
$env:DEEPSEEK_MODEL = 'deepseek-v4-pro'
mvn spring-boot:run
```

Use the project's existing MySQL, RabbitMQ, and MinIO startup procedure before
starting Spring Boot. The API listens on `http://localhost:8080` by default.

### 2. Login and obtain the Bearer token

Run in another PowerShell window, replacing the credentials with an existing
project user who owns transcript `2084176870403137537`:

```powershell
$BaseUrl = 'http://localhost:8080'
$LoginBody = @{
  username = '<username>'
  password = '<password>'
} | ConvertTo-Json

$Login = Invoke-RestMethod -Method Post `
  -Uri "$BaseUrl/api/auth/login" `
  -ContentType 'application/json; charset=utf-8' `
  -Body $LoginBody

if ($Login.code -ne 0) { throw $Login.message }
$Token = $Login.data.token
$Headers = @{ Authorization = "Bearer $Token" }
```

### 3. Create a conversation for the requested transcript

```powershell
$TranscriptId = '2084176870403137537'
$CreateBody = @{
  transcriptId = $TranscriptId
  title = $null
} | ConvertTo-Json

$Created = Invoke-RestMethod -Method Post `
  -Uri "$BaseUrl/api/agent/conversations" `
  -Headers $Headers -ContentType 'application/json; charset=utf-8' `
  -Body $CreateBody

if ($Created.code -ne 0) { throw $Created.message }
$ConversationId = $Created.data.conversationId
$Created.data | Format-List
```

### 4. Send the first question and verify citations

```powershell
$ClientRequestId = [guid]::NewGuid().ToString()
$QuestionBody = @{
  content = '这段音频主要表达了什么观点？'
  clientRequestId = $ClientRequestId
} | ConvertTo-Json

$FirstReply = Invoke-RestMethod -Method Post `
  -Uri "$BaseUrl/api/agent/conversations/$ConversationId/messages" `
  -Headers $Headers -ContentType 'application/json; charset=utf-8' `
  -Body $QuestionBody

if ($FirstReply.code -ne 0) { throw $FirstReply.message }
$FirstReply.data.assistantMessage | Format-List
$FirstReply.data.assistantMessage.citations | Format-Table

$Segments = Invoke-RestMethod -Method Get `
  -Uri "$BaseUrl/api/audio-transcriptions/transcripts/$TranscriptId/segments?current=1&size=100" `
  -Headers $Headers

foreach ($Citation in $FirstReply.data.assistantMessage.citations) {
  $Source = $Segments.data.records | Where-Object {
    $_.segmentId -eq $Citation.segmentId
  } | Select-Object -First 1
  if ($null -eq $Source) { throw "Missing segment $($Citation.segmentId)" }
  if (-not $Source.text.Contains($Citation.quote)) {
    throw "Quote is not verbatim for segment $($Citation.segmentId)"
  }
  if ($Source.startMs -ne $Citation.startMs -or
      $Source.endMs -ne $Citation.endMs) {
    throw "Database timing mismatch for segment $($Citation.segmentId)"
  }
}
```

### 5. Query history and verify idempotency

```powershell
$History = Invoke-RestMethod -Method Get `
  -Uri "$BaseUrl/api/agent/conversations/$ConversationId/messages?current=1&size=50" `
  -Headers $Headers
$History.data.records | Format-Table sequenceNo, role, status, messageId

# Send the exact same clientRequestId again.
$Duplicate = Invoke-RestMethod -Method Post `
  -Uri "$BaseUrl/api/agent/conversations/$ConversationId/messages" `
  -Headers $Headers -ContentType 'application/json; charset=utf-8' `
  -Body $QuestionBody

if ($Duplicate.data.userMessage.messageId -ne
    $FirstReply.data.userMessage.messageId) {
  throw 'Idempotency failed for userMessage'
}
if ($Duplicate.data.assistantMessage.messageId -ne
    $FirstReply.data.assistantMessage.messageId) {
  throw 'Idempotency failed for assistantMessage'
}
```

### 6. Verify second-turn history

```powershell
$SecondBody = @{
  content = '它是如何支持这个观点的？'
  clientRequestId = [guid]::NewGuid().ToString()
} | ConvertTo-Json

$SecondReply = Invoke-RestMethod -Method Post `
  -Uri "$BaseUrl/api/agent/conversations/$ConversationId/messages" `
  -Headers $Headers -ContentType 'application/json; charset=utf-8' `
  -Body $SecondBody

$SecondReply.data.assistantMessage | Format-List

$HistoryAfterSecond = Invoke-RestMethod -Method Get `
  -Uri "$BaseUrl/api/agent/conversations/$ConversationId/messages?current=1&size=50" `
  -Headers $Headers
$HistoryAfterSecond.data.records | Format-Table sequenceNo, role, status

# Expected sequence: USER 1, ASSISTANT 2, USER 3, ASSISTANT 4.
# The backend log for the second call should show historyMessageCount=2.
```

### 7. Inspect persisted rows

```powershell
mysql -h localhost -P 3306 -u root -p audio_agent `
  -e "SELECT id,user_id,transcript_id,title,status,model_name,prompt_version,last_message_id,last_message_at FROM agent_conversation WHERE id='$ConversationId'"

mysql -h localhost -P 3306 -u root -p audio_agent `
  -e "SELECT id,conversation_id,sequence_no,role,status,reply_to_message_id,client_request_id,prompt_tokens,completion_tokens,total_tokens,failure_code FROM agent_message WHERE conversation_id='$ConversationId' ORDER BY sequence_no"

mysql -h localhost -P 3306 -u root -p audio_agent `
  -e "SELECT id,message_id,transcript_id,segment_id,segment_order,start_ms,end_ms,quote,citation_order FROM agent_message_citation WHERE conversation_id='$ConversationId' ORDER BY message_id,citation_order"

# Exactly one USER row should exist for the repeated clientRequestId.
mysql -h localhost -P 3306 -u root -p audio_agent `
  -e "SELECT COUNT(*) AS request_count FROM agent_message WHERE conversation_id='$ConversationId' AND client_request_id='$ClientRequestId'"
```

## Current limitations and frontend contract

This is intentionally a synchronous MVP. It has no streaming/cancel/regenerate,
conversation archive/delete UI, embeddings, semantic reranker, planner/critic,
external search, or tools. Long-transcript retrieval is lexical and may return
insufficient context for paraphrased questions with little surface overlap.
Provider retry orchestration is not performed inside a POST request; failures
are persisted and a repeated `clientRequestId` returns that failed attempt.

The future Agent page can use the five APIs above. It should keep all
`conversationId`, `transcriptId`, `messageId`, `replyToMessageId`, `citationId`,
and `segmentId` values as strings; render messages by `sequenceNo`; use
`status` for PROCESSING/FAILED states; show token fields only when present; and
seek the existing player with citation `startMs` while displaying
`segmentOrder`, `endMs`, and `quote`.
