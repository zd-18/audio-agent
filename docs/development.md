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

推荐版本：Java 21、Node.js 20+、MySQL 8、Python 3.10+、FFmpeg 6+。`infra/docker-compose.yml` 当前只包含 Redis 和 MinIO，MySQL 与 RabbitMQ 需要自行准备。

启动 Docker 中的 Redis 和 MinIO：

```powershell
cd D:\mycode\audio-agent\infra
Copy-Item .env.example .env
# 修改 .env 中的本地 MinIO 密码后执行
docker compose up -d
docker compose ps
```

## 环境变量

按部署环境设置以下变量；变量值不要提交到 Git：

```text
DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASSWORD
REDIS_HOST / REDIS_PORT / REDIS_PASSWORD / REDIS_DATABASE
RABBITMQ_HOST / RABBITMQ_PORT / RABBITMQ_USER / RABBITMQ_PASS
MINIO_ENDPOINT / MINIO_ACCESS_KEY / MINIO_SECRET_KEY / MINIO_BUCKET_NAME
MINIO_PRESIGNED_URL_ENABLED / MINIO_PRESIGNED_URL_EXPIRE_SECONDS
FFMPEG_EXECUTABLE / FFPROBE_EXECUTABLE / ASR_BASE_URL
AUDIO_ANALYSIS_DISPATCH_MODE
DEEPSEEK_ENABLED / DEEPSEEK_API_KEY / DEEPSEEK_BASE_URL / DEEPSEEK_MODEL
AGENT_MODEL_NAME
```

项目根目录的 [`.env.example`](../.env.example) 是变量清单模板。Spring Boot 不会自动加载该文件，请将变量配置到当前 PowerShell、IDE 启动配置或操作系统环境中。至少需要正确设置数据库密码、RabbitMQ 密码、MinIO 密钥和 FFmpeg/FFprobe 路径；启用 DeepSeek 时还需要 API Key。

PowerShell 示例：

```powershell
$env:DB_PASSWORD='你的本地数据库密码'
$env:RABBITMQ_PASS='你的本地 RabbitMQ 密码'
$env:MINIO_SECRET_KEY='与 infra/.env 中一致的密码'
$env:FFMPEG_EXECUTABLE='D:/ffmpeg/bin/ffmpeg.exe'
$env:FFPROBE_EXECUTABLE='D:/ffmpeg/bin/ffprobe.exe'
$env:DEEPSEEK_API_KEY='你的 API Key'
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
npm run test:e2e
```

`npm run test:e2e` 默认使用 Windows 已安装的 Microsoft Edge，并自动临时启动 Vite；它不需要下载 Playwright Chromium。当前端口 `5173` 已有开发服务时会复用该服务。

## 数据库说明

当前项目尚未接入 Flyway，数据库脚本分为两段：

### 全新数据库

按文件编号依次执行，不能只执行 `backend/sql`：

```text
scripts/sql/01_create_database.sql
scripts/sql/02_create_tables.sql
scripts/sql/03_create_analysis_tasks.sql
scripts/sql/04_create_analysis_results.sql
scripts/sql/05_alter_analysis_task_add_retry.sql
backend/sql/06_create_audio_issue_segments.sql
...
backend/sql/25_add_audio_file_recycle_bin.sql
```

其中 `01` 创建并选择 `audio_agent` 数据库，后续脚本均基于该数据库。建议每执行一个脚本就确认 Navicat 或 MySQL 客户端显示成功，再继续下一个。

### 已有数据库升级

只执行上次已应用编号之后的脚本。例如数据库已经执行到 `24`，本次只执行 `25_add_audio_file_recycle_bin.sql`。部分迁移包含非幂等 `ALTER TABLE`，重复执行可能出现“字段或约束已存在”。升级前先备份数据库。

### 推荐启动顺序

1. MySQL、Redis、RabbitMQ、MinIO。
2. 执行或确认数据库迁移。
3. FunASR 服务，确认 `/health` 返回 `modelLoaded=true`。
4. Spring Boot 后端。
5. React 前端。

### 常见启动问题

- 后端提示 `minio.secretKey` 不能为空：没有设置 `MINIO_SECRET_KEY`。
- RabbitMQ 持续连接失败：检查服务端口、用户、密码和虚拟主机权限。
- FFmpeg/FFprobe 找不到：同时设置 `FFMPEG_EXECUTABLE` 与 `FFPROBE_EXECUTABLE`。
- 转写最终失败：先检查 `http://127.0.0.1:8090/health` 和模型加载状态。
- AI 问答或内容分析失败：确认 `DEEPSEEK_ENABLED` 与 `DEEPSEEK_API_KEY`。
- 回收站接口报数据库列不存在：确认已执行 `25_add_audio_file_recycle_bin.sql`。
