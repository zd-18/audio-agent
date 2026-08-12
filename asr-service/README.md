# AudioAgent ASR Service

AudioAgent 的内部语音识别服务。运行时使用真实 FunASR 模型，不包含固定文字、Mock Provider 或演示结果。

默认流水线只加载 `iic/SenseVoiceSmall + fsmn-vad`。标点模型和说话人模型默认关闭，仅当 `ASR_PUNC_MODEL`、`ASR_SPEAKER_MODEL` 配置了有效模型名时才会传给 `AutoModel`。应用启动时加载一次模型，后续请求复用同一个实例。首次启动需要联网下载模型文件；模型权重遵循对应模型卡中的许可条款。

## Windows 启动

在 PowerShell 中执行：

```powershell
cd D:\mycode\audio-agent\asr-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
$env:ASR_MODEL='iic/SenseVoiceSmall'
$env:ASR_VAD_MODEL='fsmn-vad'
$env:ASR_PUNC_MODEL='ct-punc'
$env:ASR_SPEAKER_MODEL=''
$env:ASR_DEVICE='cpu'
$env:ASR_BATCH_SIZE_SECONDS='20'
python -m uvicorn app.main:app --host 127.0.0.1 --port 8090
```

`requirements.txt` 固定的是 CPU/通用安装基线。若要使用 NVIDIA GPU，请先按照 PyTorch 官方安装选择器安装与本机 CUDA 匹配的 `torch`/`torchaudio`，再安装其余依赖，并设置：

```powershell
$env:ASR_DEVICE='cuda'
```

可将 `.env.example` 中的值设置为系统环境变量。服务不会自动读取 `.env` 文件，避免生产环境中隐式加载未知配置。

## 健康检查

```powershell
Invoke-RestMethod http://127.0.0.1:8090/health
```

模型加载完成后返回：

```json
{"status":"UP","provider":"funasr","modelLoaded":true,"loadError":null}
```

模型加载失败不会终止 FastAPI 服务，此时健康检查返回 `modelLoaded=false` 和简短的 `loadError`，转写接口返回 HTTP 503。

## 内部转写接口

接口只监听本机地址，供 Spring Boot 消费者调用：

```powershell
curl.exe -X POST http://127.0.0.1:8090/internal/asr/transcribe `
  -F "file=@D:\audio\standardized.wav" `
  -F "language=zh" `
  -F "enableSpeakerDiarization=false"
```

输入必须是 mono、16000 Hz、pcm_s16le WAV。输出包含真实模型全文、毫秒时间戳片段及可用时的说话人标签/置信度。请求临时文件使用随机系统文件名，并在成功或失败后立即删除；日志不记录完整文字稿。

启用 `enableSpeakerDiarization=true` 前必须配置 `ASR_SPEAKER_MODEL`，否则接口返回明确的 HTTP 422 业务错误。`ASR_PUNC_MODEL` 和 `ASR_SPEAKER_MODEL` 的空字符串以及 `none`、`null`、`disabled`、`off`、`false` 均表示关闭对应模型。

当前固定的 FunASR 版本支持 `punc_model`。推荐为中文/中英文转写设置
`ASR_PUNC_MODEL=ct-punc`，服务会同时请求 token 时间戳与
`sentence_info`。标点模型仍是独立可选项；留空不会改变 ASR/VAD 模型，
服务会改用具有可靠文本对应关系的真实 timestamp，无法可靠对应时才保留
一个粗粒度片段。首次启用新的标点模型可能需要下载额外权重。

启动日志会记录 Python、FunASR、ModelScope 版本，实际 ASR/VAD/标点/
说话人模型，以及 `AutoModel` 和 `generate` 参数（不记录全文）。每次转写的
`source` 字段说明实际分段来源：`SENTENCE_INFO`、
`VAD_OR_TIMESTAMP` 或 `FULL_TEXT_FALLBACK`。

完整的分段契约、诊断信息和手动验收步骤见
[`../docs/asr-segmentation.md`](../docs/asr-segmentation.md)。

## 与后端联调

后端默认使用 `ASR_BASE_URL=http://127.0.0.1:8090`。ASR 服务未启动不会阻止 Spring Boot 启动，但已创建并被消费者执行的转写任务会按 RabbitMQ 重试规则处理，最终以友好失败原因进入 `FAILED`。
