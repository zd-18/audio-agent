# ASR 细粒度分段与稳定引用契约

## 当前运行基线

- Python：本机验收环境为 3.12.7。
- FunASR：`1.3.22`（由 `asr-service/requirements.txt` 固定）。
- ModelScope：`1.38.1`（由 `asr-service/requirements.txt` 固定）。
- 默认 ASR：`iic/SenseVoiceSmall`。
- 默认 VAD：`fsmn-vad`。
- 标点：通过 `ASR_PUNC_MODEL` 独立配置；代码默认关闭，推荐值为
  `ct-punc`。
- 说话人：通过 `ASR_SPEAKER_MODEL` 独立配置，默认关闭。

`AutoModel` 总是收到 `model`、`device`、`disable_update`，并仅在相应
配置非空时收到 `vad_model`、`punc_model`、`spk_model`。

`generate` 收到：

- `input`：本地临时标准化 WAV 路径；
- `language`：请求语言；
- `batch_size_s`：`ASR_BATCH_SIZE_SECONDS`；
- `use_itn=True`；
- `output_timestamp=True`；
- `return_raw_text=True`；
- `sentence_timestamp=True`（仅配置标点模型时，否则为 `False`）。

## FunASR 1.3.22 返回结构与分段优先级

当前 `AutoModel.generate` 返回 `list[dict]`。第一条结果常见字段为
`key`、`text`、`timestamp`、`words`、`raw_text`、`sentence_info`；字段
是否存在由 ASR、VAD、标点和说话人模型能力及调用参数决定。

- `text`、`timestamp`、`words`、`sentence_info` 都位于第一条结果 dict，
  不位于返回 list 顶层。
- `timestamp` 是毫秒边界数组 `[[start_ms, end_ms], ...]`。当前
  SenseVoice 在 `output_timestamp=True` 时同时返回可一一对应的
  `words`。
- Fun-ASR-Nano 类模型还可能返回 `timestamps`，元素是带 `token`、
  `start_time`、`end_time` 的 dict，其中时间单位为秒；FunASR 会同时
  兼容转换为 `timestamp`。
- `sentence_info` 是 dict 数组，当前句子级结构使用 `text`、`start`、
  `end`、`timestamp`，说话人 VAD 模式也可能使用 `sentence` 和 `spk`。

服务按以下顺序输出 segment：

1. `SENTENCE_INFO`：保留每个合法句子的真实起止毫秒，不合并句子。
2. `VAD_OR_TIMESTAMP`：只有 token/word 与时间戳可靠一一对应时，才按
   句末标点或真实语音停顿组织多个片段；片段起止仍直接取模型时间戳。
   如果只有真实时间范围、没有可靠文本映射，则只输出一个粗粒度真实
   范围片段。
3. `FULL_TEXT_FALLBACK`：完全没有可用时间边界时输出一个 0 至音频时长
   的全文片段，并记录 WARN。

服务不会按字符数、平均时长或固定秒数分配、插值或伪造时间戳。

## 输出与稳定引用

ASR 服务的每个 segment 稳定返回：

```json
{
  "order": 1,
  "startMs": 120,
  "endMs": 920,
  "speaker": null,
  "text": "第一句。",
  "confidence": null
}
```

后端持久化后，文字稿详情的片段额外返回字符串 `segmentId`，以及稳定
引用别名 `segmentOrder`；为兼容当前页面仍保留 `order`。后续 Agent 引用
应保存 `segmentId`、`segmentOrder`、`startMs`、`endMs`、`text`。所有
BIGINT ID 均以字符串传给前端。

## 日志判断

启动时查看 `Loading FunASR pipeline` 日志，可确认运行版本、模型和参数。
每个请求会出现两条不含全文的关键日志：

- `FunASR result diagnostics`：返回对象类型、第一条 key、
  `sentence_info`/`timestamp` 是否存在及数量、全文长度。
- `FunASR segmentation completed source=...`：最终来源和 segment 数量。

`source=FULL_TEXT_FALLBACK` 还会有 WARN，表示本次只能得到一个粗段。
标点模型加载失败时，`FunASR pipeline loading failed` 会列出已配置的模型、
异常类型和截断后的错误信息，但不输出 API Key 或识别全文。

## 手动验收

1. 在 PowerShell 启动 ASR：

   ```powershell
   cd D:\mycode\audio-agent\asr-service
   .\.venv\Scripts\Activate.ps1
   $env:ASR_MODEL='iic/SenseVoiceSmall'
   $env:ASR_VAD_MODEL='fsmn-vad'
   $env:ASR_PUNC_MODEL='ct-punc'
   $env:ASR_SPEAKER_MODEL=''
   $env:ASR_DEVICE='cpu'
   python -m uvicorn app.main:app --host 127.0.0.1 --port 8090
   ```

2. 确认启动日志中的 `asrModel`、`vadModel`、`puncModel` 与预期一致，
   再访问 `http://127.0.0.1:8090/health`，确认 `modelLoaded=true`。

3. 启动现有后端和前端。由于成功任务会被安全复用，旧文字稿不会自动
   重分段；请在上传页把 01:16 音频重新上传为新的音频文件，再创建新的
   转写任务。不要修改旧 transcript/segment 记录。

4. 等待任务成功，在 ASR 日志中查找该请求的
   `FunASR segmentation completed`。优先期望 `SENTENCE_INFO`；无标点
   模型时可能是 `VAD_OR_TIMESTAMP`。同时确认 `segmentCount` 大于 1。

5. 在 MySQL 只读查询最新任务和片段：

   ```sql
   SELECT t.id AS transcript_id, t.audio_file_id,
          t.transcription_task_id, t.segment_count,
          LENGTH(t.full_text) AS full_text_length, t.created_at
   FROM audio_transcript t
   ORDER BY t.created_at DESC
   LIMIT 5;

   SELECT id AS segment_id, segment_order, start_ms, end_ms,
          speaker_label, confidence, LEFT(text, 80) AS text_preview
   FROM audio_transcript_segment
   WHERE transcript_id = :new_transcript_id
   ORDER BY segment_order ASC;
   ```

6. 用新任务的 `audio_file_id`、`transcription_task_id` 和 `created_at` 对照
   上传时间，确认查询的不是旧文字稿。检查 segment ID 互不相同、顺序连续，
   `start_ms/end_ms` 不倒序，并确认 `audio_transcript.full_text` 完整。

7. 打开新转写任务详情页，确认显示多个独立时间范围。逐个点击片段，播放器
   应从各自 `startMs` 开始，现有智能分析仍能读取全部片段。

## 自动化验证

```powershell
cd D:\mycode\audio-agent\asr-service
.\.venv\Scripts\python.exe -m unittest discover -s tests -v

# 可选：使用真实模型检查返回 key 和分段来源，只输出文本长度而非全文
.\.venv\Scripts\python.exe -m scripts.inspect_funasr_result `
  D:\audio\standardized.wav --language zh

cd D:\mycode\audio-agent\backend
.\mvnw.cmd test

cd D:\mycode\audio-agent\frontend
npm run build
```
