# 本地运行

本文档承接 README 中偏开发配置的内容。它只描述当前项目已有的本地依赖和启动方式，不包含真实密码、Token 或 API Key。

## 依赖服务

启动前准备：

- MySQL：执行项目现有 SQL 脚本，数据库连接由环境变量提供。
- Redis：用于分片上传状态、TTL 和合并锁。
- RabbitMQ：用于 Outbox、分析任务、retry queue 和 DLQ。
- MinIO：用于临时分片、合并对象和正式音频。
- FFmpeg / FFprobe：后端本机可执行文件。
- FunASR：`asr-service` 内部服务，默认监听 `127.0.0.1:8090`。
- DeepSeek：内容分析和 Agent 的外部 AI 服务，使用环境变量配置。

`infra/docker-compose.yml` 当前只包含 Redis 和 MinIO。本项目文档整理不启动 Docker，也不修改真实数据库。

## 环境变量

按部署环境设置以下变量；变量值不要提交到 Git：

```text
DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASSWORD
REDIS_HOST / REDIS_PORT / REDIS_PASSWORD / REDIS_DATABASE
RABBITMQ_HOST / RABBITMQ_PORT / RABBITMQ_USER / RABBITMQ_PASS
MINIO_ENDPOINT / MINIO_ACCESS_KEY / MINIO_SECRET_KEY / MINIO_BUCKET_NAME
MINIO_PRESIGNED_URL_ENABLED / MINIO_PRESIGNED_URL_EXPIRE_SECONDS
FFMPEG_EXECUTABLE / ASR_BASE_URL
DEEPSEEK_ENABLED / DEEPSEEK_API_KEY / DEEPSEEK_BASE_URL / DEEPSEEK_MODEL
AGENT_MODEL_NAME
```

后端默认使用 `ASR_BASE_URL=http://127.0.0.1:8090`；ASR 服务未启动不会阻止 Spring Boot 启动，但已执行的转写任务会按重试规则处理并最终进入 `FAILED`。

## 启动 FunASR 服务

Windows PowerShell 示例：

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
python -m uvicorn app.main:app --host 127.0.0.1 --port 8090
```

首次运行可能需要下载模型。更完整的 FunASR 分段契约见 [`asr-segmentation.md`](asr-segmentation.md)。

## 启动后端

后端是 Java 21 + Spring Boot 项目：

```powershell
cd D:\mycode\audio-agent\backend
.\mvnw.cmd spring-boot:run
```

默认 HTTP 端口为 `8080`。数据库、Redis、RabbitMQ、MinIO、FFmpeg、FunASR 和 DeepSeek 的连接参数来自环境变量及 `application-dev.yml` 的默认配置。

## 启动前端

```powershell
cd D:\mycode\audio-agent\frontend
npm install
npm run dev
```

常用前端校验命令：

```powershell
npm run build
npm run test
```

## 数据库说明

当前项目使用已有编号 SQL 脚本维护数据库结构。本项目尚未接入 Flyway；请按项目现有说明和环境实际情况执行脚本，不要把数据库密码写入脚本或文档。
