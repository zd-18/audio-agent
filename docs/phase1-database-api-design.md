# 音频质量诊断平台第一轮设计文档

> 版本：v1.0  
> 范围：第一轮基础版本  
> 核心目标：完成“文件上传 → 音频预处理 → ASR 转写 → 静音/音量问题检测 → 诊断结果查询”的基础闭环。

---

## 1. 项目第一轮范围

第一轮优先实现以下功能：

1. 用户上传 MP3、WAV、M4A、MP4 文件。
2. 文件保存到 MinIO，MySQL 保存文件元数据。
3. 创建音频分析任务。
4. 使用 FFmpeg 对音视频进行预处理。
5. 调用 ASR 服务生成带时间轴的转写结果。
6. 检测长时间静音、音量过低、音量过高等问题。
7. 保存并查询片段级诊断结果。
8. 查询任务状态、任务阶段和处理进度。
9. 获取原始文件或处理文件的临时试听、下载地址。

第一轮暂不实现：

- 分片上传和断点续传
- 音频自动修复
- SSE 实时进度推送
- Planner-Executor-Critic Agent
- Embedding 语义检索
- 多说话人精确分离
- 高级降噪与音色补全

---

## 2. 第一轮业务流程

```text
用户上传文件
    ↓
文件保存至 MinIO
    ↓
audio_file 保存文件元数据
    ↓
创建 audio_task 分析任务
    ↓
初始化 audio_task_stage
    ↓
异步执行 FFmpeg 预处理
    ↓
调用 ASR 生成时间轴转写
    ↓
执行静音与音量检测
    ↓
保存 audio_transcript_segment
    ↓
保存 audio_issue
    ↓
任务完成，前端展示诊断报告
```

---

# 3. 数据库设计

## 3.1 表结构总览

第一轮使用 5 张核心表：

| 表名 | 作用 |
|---|---|
| `audio_file` | 保存音频、视频及处理产物的文件元数据 |
| `audio_task` | 保存一次完整的音频分析任务 |
| `audio_task_stage` | 保存预处理、转写、检测等阶段执行情况 |
| `audio_transcript_segment` | 保存带时间轴的 ASR 转写片段 |
| `audio_issue` | 保存检测出的音频问题片段 |

表关系：

```text
audio_file
    │
    └── audio_task
            ├── audio_task_stage
            ├── audio_transcript_segment
            └── audio_issue
```

---

## 3.2 文件元数据表：audio_file

### 作用

保存原始文件、标准化音频等文件的元数据。  
真实文件保存在 MinIO，数据库不保存二进制文件。

### 建表 SQL

```sql
CREATE TABLE audio_file (
    id BIGINT UNSIGNED NOT NULL COMMENT '文件ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',

    source_file_id BIGINT UNSIGNED DEFAULT NULL COMMENT '来源文件ID',
    file_role TINYINT NOT NULL DEFAULT 1 COMMENT
        '文件角色：1原始文件 2标准化音频 3问题片段 4修复结果 5最终导出',

    original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    extension VARCHAR(20) DEFAULT NULL COMMENT '文件扩展名',
    mime_type VARCHAR(100) DEFAULT NULL COMMENT 'MIME类型',

    bucket_name VARCHAR(100) NOT NULL COMMENT 'MinIO桶名称',
    object_key VARCHAR(512) NOT NULL COMMENT 'MinIO对象路径',

    size_bytes BIGINT UNSIGNED NOT NULL COMMENT '文件大小，单位字节',
    sha256 CHAR(64) DEFAULT NULL COMMENT '文件SHA-256摘要',

    duration_ms BIGINT UNSIGNED DEFAULT NULL COMMENT '音频时长，单位毫秒',
    sample_rate INT UNSIGNED DEFAULT NULL COMMENT '采样率',
    channels TINYINT UNSIGNED DEFAULT NULL COMMENT '声道数',
    bit_rate INT UNSIGNED DEFAULT NULL COMMENT '比特率',

    file_status TINYINT NOT NULL DEFAULT 1 COMMENT
        '状态：1上传中 2可用 3处理中 4失败 5已删除',

    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (id),
    UNIQUE KEY uk_bucket_object (bucket_name, object_key),
    KEY idx_user_created (user_id, created_at),
    KEY idx_source_file (source_file_id),
    KEY idx_user_hash (user_id, sha256, size_bytes)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音频文件元数据表';
```

### 关键字段说明

| 字段 | 说明 |
|---|---|
| `source_file_id` | 记录当前文件由哪个文件生成 |
| `file_role` | 区分原始文件、标准化音频等 |
| `bucket_name` | MinIO 桶名称 |
| `object_key` | MinIO 中的对象路径 |
| `sha256` | 后续可用于文件去重 |
| `duration_ms` | 音频时长，统一使用毫秒 |
| `file_status` | 文件当前状态 |

### 文件关系示例

```text
interview.mp4
    ↓ FFmpeg提取
interview_standard.wav
```

对应数据：

| id | source_file_id | file_role | original_name |
|---:|---:|---:|---|
| 1001 | null | 1 | interview.mp4 |
| 1002 | 1001 | 2 | interview_standard.wav |

---

## 3.3 音频分析任务表：audio_task

### 作用

一条记录表示用户针对某个文件发起的一次分析任务。

### 建表 SQL

```sql
CREATE TABLE audio_task (
    id BIGINT UNSIGNED NOT NULL COMMENT '任务ID',
    task_no VARCHAR(64) NOT NULL COMMENT '对外任务编号',

    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    source_file_id BIGINT UNSIGNED NOT NULL COMMENT '原始文件ID',
    processed_file_id BIGINT UNSIGNED DEFAULT NULL COMMENT '标准化音频文件ID',

    task_type TINYINT NOT NULL DEFAULT 1 COMMENT
        '任务类型：1完整分析 2仅转写 3仅质量检测',

    status TINYINT NOT NULL DEFAULT 0 COMMENT
        '状态：0待处理 1处理中 2等待确认 3已完成 4失败 5取消',

    current_stage VARCHAR(32) DEFAULT NULL COMMENT
        '当前阶段：PREPROCESS、TRANSCRIBE、ANALYZE、REPORT、FINISHED',

    progress TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '任务进度0-100',

    goal_text VARCHAR(1000) DEFAULT NULL COMMENT '用户自然语言处理目标',
    process_config JSON DEFAULT NULL COMMENT '分析参数配置',
    result_summary JSON DEFAULT NULL COMMENT '任务结果摘要',

    failure_stage VARCHAR(32) DEFAULT NULL COMMENT '失败阶段',
    failure_code VARCHAR(64) DEFAULT NULL COMMENT '失败错误码',
    failure_message TEXT DEFAULT NULL COMMENT '失败原因',
    retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '任务重试次数',

    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    started_at DATETIME(3) DEFAULT NULL COMMENT '开始时间',
    finished_at DATETIME(3) DEFAULT NULL COMMENT '完成时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_task_no (task_no),
    KEY idx_user_created (user_id, created_at),
    KEY idx_file_status (source_file_id, status),
    KEY idx_status_stage (status, current_stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音频分析任务表';
```

### Java 状态枚举建议

```java
public enum AudioTaskStatus {
    PENDING,
    PROCESSING,
    WAITING_CONFIRMATION,
    COMPLETED,
    FAILED,
    CANCELLED
}
```

```java
public enum AudioTaskStage {
    PREPROCESS,
    TRANSCRIBE,
    ANALYZE,
    REPORT,
    FINISHED
}
```

### 推荐状态流转

```text
PENDING
  ↓
PROCESSING
  ↓
COMPLETED

PROCESSING
  ↓
FAILED

PENDING / PROCESSING
  ↓
CANCELLED
```

---

## 3.4 任务阶段表：audio_task_stage

### 作用

记录每个处理阶段的执行状态、输入输出和错误信息，为后续失败重试与阶段恢复预留能力。

### 建表 SQL

```sql
CREATE TABLE audio_task_stage (
    id BIGINT UNSIGNED NOT NULL COMMENT '阶段记录ID',
    task_id BIGINT UNSIGNED NOT NULL COMMENT '任务ID',

    stage_code VARCHAR(32) NOT NULL COMMENT
        '阶段：PREPROCESS、TRANSCRIBE、ANALYZE、REPORT',

    attempt_no INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '第几次尝试',

    status TINYINT NOT NULL DEFAULT 0 COMMENT
        '状态：0待执行 1执行中 2成功 3失败 4跳过',

    progress TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '阶段进度',

    input_file_id BIGINT UNSIGNED DEFAULT NULL COMMENT '输入文件ID',
    output_file_id BIGINT UNSIGNED DEFAULT NULL COMMENT '输出文件ID',

    input_snapshot JSON DEFAULT NULL COMMENT '输入参数快照',
    output_snapshot JSON DEFAULT NULL COMMENT '输出结果摘要',

    error_code VARCHAR(64) DEFAULT NULL COMMENT '错误码',
    error_message TEXT DEFAULT NULL COMMENT '错误信息',

    started_at DATETIME(3) DEFAULT NULL,
    finished_at DATETIME(3) DEFAULT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_task_stage_attempt (task_id, stage_code, attempt_no),
    KEY idx_task_status (task_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音频任务阶段表';
```

### 阶段示例

| stage_code | status | 说明 |
|---|---|---|
| PREPROCESS | SUCCESS | FFmpeg 预处理完成 |
| TRANSCRIBE | RUNNING | ASR 转写中 |
| ANALYZE | PENDING | 等待执行质量检测 |
| REPORT | PENDING | 等待生成结果摘要 |

---

## 3.5 转写片段表：audio_transcript_segment

### 作用

按句子或片段保存 ASR 结果，支持时间轴展示和音频跳转。

### 建表 SQL

```sql
CREATE TABLE audio_transcript_segment (
    id BIGINT UNSIGNED NOT NULL COMMENT '转写片段ID',
    task_id BIGINT UNSIGNED NOT NULL COMMENT '所属任务ID',
    file_id BIGINT UNSIGNED NOT NULL COMMENT '对应音频文件ID',

    segment_no INT UNSIGNED NOT NULL COMMENT '片段序号',
    start_ms BIGINT UNSIGNED NOT NULL COMMENT '开始时间，毫秒',
    end_ms BIGINT UNSIGNED NOT NULL COMMENT '结束时间，毫秒',

    speaker_label VARCHAR(50) DEFAULT NULL COMMENT '说话人标签',
    language VARCHAR(20) DEFAULT NULL COMMENT '语言',
    content TEXT NOT NULL COMMENT '转写内容',

    confidence DECIMAL(6,5) DEFAULT NULL COMMENT '识别置信度',
    raw_result JSON DEFAULT NULL COMMENT 'ASR原始结果',

    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_task_segment (task_id, segment_no),
    KEY idx_task_time (task_id, start_ms, end_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音频转写片段表';
```

### 数据示例

| segment_no | start_ms | end_ms | content |
|---:|---:|---:|---|
| 1 | 5000 | 12000 | 大家好，今天介绍一下这个项目 |
| 2 | 13000 | 22000 | 首先来看一下系统的主要功能 |

---

## 3.6 音频问题表：audio_issue

### 作用

保存静音、音量异常等片段级问题，是第一轮最核心的业务表。

### 建表 SQL

```sql
CREATE TABLE audio_issue (
    id BIGINT UNSIGNED NOT NULL COMMENT '问题ID',
    task_id BIGINT UNSIGNED NOT NULL COMMENT '分析任务ID',
    file_id BIGINT UNSIGNED NOT NULL COMMENT '对应音频文件ID',

    transcript_segment_id BIGINT UNSIGNED DEFAULT NULL COMMENT '关联转写片段ID',
    issue_no INT UNSIGNED NOT NULL COMMENT '任务内问题序号',

    start_ms BIGINT UNSIGNED NOT NULL COMMENT '开始时间，毫秒',
    end_ms BIGINT UNSIGNED NOT NULL COMMENT '结束时间，毫秒',

    issue_type VARCHAR(32) NOT NULL COMMENT
        '问题类型：LONG_SILENCE、LOW_VOLUME、HIGH_VOLUME、NOISE、CLIPPING',

    severity TINYINT NOT NULL COMMENT '严重程度：1低 2中 3高',
    score DECIMAL(6,5) DEFAULT NULL COMMENT '问题评分0-1',

    metric_data JSON DEFAULT NULL COMMENT '检测指标',
    description VARCHAR(500) NOT NULL COMMENT '问题说明',
    suggestion VARCHAR(500) DEFAULT NULL COMMENT '修复建议',

    repairability TINYINT NOT NULL DEFAULT 0 COMMENT
        '可修复性：0未知 1自动修复 2人工确认 3建议删除 4建议补录',

    issue_status TINYINT NOT NULL DEFAULT 0 COMMENT
        '处理状态：0未处理 1已选择修复 2已忽略 3已修复',

    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_task_issue_no (task_id, issue_no),
    KEY idx_task_type (task_id, issue_type),
    KEY idx_task_time (task_id, start_ms, end_ms),
    KEY idx_task_status (task_id, issue_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音频问题片段表';
```

### 问题类型建议

```java
public enum AudioIssueType {
    LONG_SILENCE,
    LOW_VOLUME,
    HIGH_VOLUME,
    VOLUME_FLUCTUATION,
    NOISE,
    CLIPPING
}
```

第一轮优先实现：

```text
LONG_SILENCE
LOW_VOLUME
HIGH_VOLUME
```

### 静音问题示例

```json
{
  "startMs": 25000,
  "endMs": 31600,
  "issueType": "LONG_SILENCE",
  "severity": "MEDIUM",
  "score": 0.86,
  "metricData": {
    "durationMs": 6600,
    "thresholdDb": -45
  },
  "description": "检测到6.6秒连续静音",
  "suggestion": "建议删除该时间段",
  "repairability": "AUTO_REPAIR"
}
```

---

# 4. 后端接口设计

## 4.1 接口分组

第一轮接口分为以下 4 组：

| 接口组 | 作用 |
|---|---|
| 文件接口 | 上传、查询、试听、下载 |
| 分析任务接口 | 创建任务、查询状态、查询阶段 |
| 转写接口 | 查询时间轴转写结果 |
| 诊断接口 | 查询问题列表、问题详情和统计 |

统一前缀：

```text
/api/v1
```

---

## 4.2 统一响应结构

### 普通响应

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "requestId": "7ad6c2f0e51a4c76"
}
```

### 分页响应

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [],
    "page": 1,
    "size": 10,
    "total": 25
  },
  "requestId": "7ad6c2f0e51a4c76"
}
```

---

# 5. 文件管理接口

## 5.1 上传文件

```http
POST /api/v1/files
Content-Type: multipart/form-data
```

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `file` | MultipartFile | 是 | 音频或视频文件 |

第一轮支持格式：

```text
mp3
wav
m4a
mp4
```

### 返回示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "fileId": 1001,
    "originalName": "interview.mp3",
    "sizeBytes": 52834210,
    "mimeType": "audio/mpeg",
    "fileStatus": "AVAILABLE",
    "durationMs": null
  }
}
```

### 后端处理步骤

```text
校验文件格式和大小
    ↓
生成 MinIO objectKey
    ↓
上传文件至 MinIO
    ↓
写入 audio_file
    ↓
返回 fileId
```

该接口只完成上传，不同步执行 ASR 和问题检测。

---

## 5.2 查询文件信息

```http
GET /api/v1/files/{fileId}
```

### 返回示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "fileId": 1001,
    "originalName": "interview.mp3",
    "fileRole": "ORIGINAL",
    "sizeBytes": 52834210,
    "durationMs": 365200,
    "sampleRate": 44100,
    "channels": 2,
    "fileStatus": "AVAILABLE",
    "createdAt": "2026-07-13T16:30:00"
  }
}
```

---

## 5.3 获取试听或下载地址

```http
GET /api/v1/files/{fileId}/download-url
```

### 查询参数

| 参数 | 可选值 | 说明 |
|---|---|---|
| `disposition` | `inline` | 在线试听 |
| `disposition` | `attachment` | 下载文件 |

### 返回示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "url": "MinIO临时预签名地址",
    "expireSeconds": 1800
  }
}
```

注意：

- 前端不直接持有 MinIO 账号密码。
- 不返回永久公开地址。
- 获取地址前校验当前用户是否拥有该文件。

---

## 5.4 删除文件

```http
DELETE /api/v1/files/{fileId}
```

第一轮先采用逻辑删除：

```text
deleted = 1
```

删除前校验：

1. 文件属于当前用户。
2. 文件不存在正在执行的任务。
3. 文件未被有效任务结果引用。

---

# 6. 音频分析任务接口

## 6.1 创建分析任务

```http
POST /api/v1/audio-tasks
Content-Type: application/json
```

### 请求示例

```json
{
  "sourceFileId": 1001,
  "taskType": "FULL_ANALYSIS",
  "goalText": "检查长时间静音和音量异常",
  "config": {
    "enableTranscription": true,
    "enableSilenceDetection": true,
    "enableVolumeDetection": true,
    "enableNoiseDetection": false,
    "silenceThresholdMs": 3000
  }
}
```

### 返回示例

```json
{
  "code": 0,
  "message": "任务已提交",
  "data": {
    "taskId": 2001,
    "taskNo": "AT202607130001",
    "status": "PENDING",
    "currentStage": null,
    "progress": 0
  }
}
```

建议 HTTP 状态码：

```text
202 Accepted
```

### 后端处理步骤

```text
校验文件归属和状态
    ↓
写入 audio_task
    ↓
初始化 audio_task_stage
    ↓
提交异步任务
    ↓
返回 taskId
```

第一轮可先使用线程池异步执行；后续再替换为 RabbitMQ。

---

## 6.2 查询任务详情

```http
GET /api/v1/audio-tasks/{taskId}
```

### 返回示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": 2001,
    "taskNo": "AT202607130001",
    "sourceFileId": 1001,
    "processedFileId": 1002,
    "status": "PROCESSING",
    "currentStage": "TRANSCRIBE",
    "progress": 45,
    "goalText": "检查长时间静音和音量异常",
    "failureStage": null,
    "failureMessage": null,
    "createdAt": "2026-07-13T16:35:00",
    "startedAt": "2026-07-13T16:35:02",
    "finishedAt": null
  }
}
```

第一轮前端可每隔 2～3 秒轮询该接口。

---

## 6.3 分页查询任务列表

```http
GET /api/v1/audio-tasks
```

### 查询参数

| 参数 | 类型 | 说明 |
|---|---|---|
| `page` | Integer | 页码，默认 1 |
| `size` | Integer | 每页数量，默认 10 |
| `status` | String | 任务状态 |
| `taskType` | String | 任务类型 |
| `keyword` | String | 文件名或任务编号 |
| `startTime` | DateTime | 开始时间 |
| `endTime` | DateTime | 结束时间 |

### 返回示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [
      {
        "taskId": 2001,
        "taskNo": "AT202607130001",
        "fileName": "interview.mp3",
        "status": "COMPLETED",
        "progress": 100,
        "issueCount": 8,
        "createdAt": "2026-07-13T16:35:00"
      }
    ],
    "page": 1,
    "size": 10,
    "total": 1
  }
}
```

---

## 6.4 查询任务阶段

```http
GET /api/v1/audio-tasks/{taskId}/stages
```

### 返回示例

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "stageCode": "PREPROCESS",
      "status": "SUCCESS",
      "progress": 100,
      "startedAt": "2026-07-13T16:35:02",
      "finishedAt": "2026-07-13T16:35:08",
      "errorMessage": null
    },
    {
      "stageCode": "TRANSCRIBE",
      "status": "RUNNING",
      "progress": 50,
      "startedAt": "2026-07-13T16:35:08",
      "finishedAt": null,
      "errorMessage": null
    },
    {
      "stageCode": "ANALYZE",
      "status": "PENDING",
      "progress": 0,
      "startedAt": null,
      "finishedAt": null,
      "errorMessage": null
    }
  ]
}
```

---

## 6.5 重试失败任务

```http
POST /api/v1/audio-tasks/{taskId}/retry
```

### 请求示例

```json
{
  "fromFailedStage": true
}
```

### 处理规则

1. 当前任务状态必须为 `FAILED`。
2. 读取 `failure_stage`。
3. 将任务重新置为待处理。
4. 增加重试次数。
5. 从失败阶段继续执行。

---

## 6.6 取消任务

```http
POST /api/v1/audio-tasks/{taskId}/cancel
```

允许取消的状态：

```text
PENDING
PROCESSING
```

建议使用条件更新：

```sql
UPDATE audio_task
SET status = 5,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE id = ?
  AND status IN (0, 1);
```

消费者在每个阶段开始前检查任务是否已取消。

---

# 7. 转写结果接口

## 7.1 查询时间轴转写片段

```http
GET /api/v1/audio-tasks/{taskId}/transcripts
```

### 查询参数

| 参数 | 类型 | 说明 |
|---|---|---|
| `page` | Integer | 页码 |
| `size` | Integer | 每页数量 |
| `startMs` | Long | 起始时间 |
| `endMs` | Long | 结束时间 |

### 返回示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [
      {
        "segmentId": 3001,
        "segmentNo": 1,
        "startMs": 5000,
        "endMs": 12000,
        "speakerLabel": null,
        "content": "大家好，今天介绍一下这个项目",
        "confidence": 0.9521
      },
      {
        "segmentId": 3002,
        "segmentNo": 2,
        "startMs": 13000,
        "endMs": 22000,
        "speakerLabel": null,
        "content": "首先来看一下系统的主要功能",
        "confidence": 0.9135
      }
    ],
    "page": 1,
    "size": 100,
    "total": 2
  }
}
```

---

## 7.2 获取完整转写文本

```http
GET /api/v1/audio-tasks/{taskId}/transcript-text
```

### 返回示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "language": "zh",
    "fullText": "大家好，今天介绍一下这个项目……",
    "segmentCount": 56
  }
}
```

---

# 8. 问题诊断接口

## 8.1 查询问题列表

```http
GET /api/v1/audio-tasks/{taskId}/issues
```

### 查询参数

| 参数 | 类型 | 说明 |
|---|---|---|
| `issueType` | String | 问题类型 |
| `severity` | String | 严重程度 |
| `issueStatus` | String | 处理状态 |
| `startMs` | Long | 起始时间 |
| `endMs` | Long | 结束时间 |

### 返回示例

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "issueId": 4001,
      "issueNo": 1,
      "startMs": 25000,
      "endMs": 31600,
      "issueType": "LONG_SILENCE",
      "severity": "MEDIUM",
      "score": 0.86,
      "description": "检测到6.6秒连续静音",
      "suggestion": "建议删除该时间段",
      "repairability": "AUTO_REPAIR",
      "issueStatus": "UNRESOLVED",
      "metricData": {
        "durationMs": 6600,
        "thresholdDb": -45
      }
    }
  ]
}
```

---

## 8.2 查询单个问题详情

```http
GET /api/v1/audio-issues/{issueId}
```

### 返回示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "issueId": 4001,
    "taskId": 2001,
    "startMs": 25000,
    "endMs": 31600,
    "contextStartMs": 22000,
    "contextEndMs": 34600,
    "issueType": "LONG_SILENCE",
    "severity": "MEDIUM",
    "transcriptText": "",
    "previewUrl": "临时试听地址",
    "metricData": {
      "durationMs": 6600,
      "thresholdDb": -45
    },
    "description": "检测到6.6秒连续静音",
    "suggestion": "建议删除该时间段"
  }
}
```

---

## 8.3 查询问题统计

```http
GET /api/v1/audio-tasks/{taskId}/issue-summary
```

### 返回示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "total": 12,
    "highSeverity": 2,
    "mediumSeverity": 7,
    "lowSeverity": 3,
    "byType": {
      "LONG_SILENCE": 5,
      "LOW_VOLUME": 4,
      "HIGH_VOLUME": 3
    },
    "autoRepairable": 9,
    "manualConfirmation": 3
  }
}
```

---

## 8.4 忽略问题

```http
POST /api/v1/audio-issues/{issueId}/ignore
```

后端将问题状态更新为 `IGNORED`，并校验问题归属当前用户。

---

## 8.5 恢复问题

```http
POST /api/v1/audio-issues/{issueId}/reopen
```

将已忽略的问题恢复为未处理状态。

---

# 9. 第一轮接口清单

## 9.1 必须优先实现

```text
POST   /api/v1/files
GET    /api/v1/files/{fileId}
GET    /api/v1/files/{fileId}/download-url

POST   /api/v1/audio-tasks
GET    /api/v1/audio-tasks/{taskId}
GET    /api/v1/audio-tasks
GET    /api/v1/audio-tasks/{taskId}/stages

GET    /api/v1/audio-tasks/{taskId}/transcripts
GET    /api/v1/audio-tasks/{taskId}/transcript-text

GET    /api/v1/audio-tasks/{taskId}/issues
GET    /api/v1/audio-issues/{issueId}
GET    /api/v1/audio-tasks/{taskId}/issue-summary
```

## 9.2 可在第一轮后半阶段补充

```text
DELETE /api/v1/files/{fileId}

POST   /api/v1/audio-tasks/{taskId}/retry
POST   /api/v1/audio-tasks/{taskId}/cancel

POST   /api/v1/audio-issues/{issueId}/ignore
POST   /api/v1/audio-issues/{issueId}/reopen
```

---

# 10. 业务错误码建议

```text
AUDIO_FILE_NOT_FOUND
AUDIO_FILE_FORMAT_UNSUPPORTED
AUDIO_FILE_TOO_LARGE
AUDIO_FILE_UPLOAD_FAILED
AUDIO_FILE_ACCESS_DENIED

AUDIO_TASK_NOT_FOUND
AUDIO_TASK_ALREADY_RUNNING
AUDIO_TASK_STATUS_INVALID
AUDIO_TASK_CANCELLED

AUDIO_PREPROCESS_FAILED
ASR_SERVICE_UNAVAILABLE
ASR_TRANSCRIBE_FAILED
AUDIO_ANALYZE_FAILED

AUDIO_ISSUE_NOT_FOUND
AUDIO_ISSUE_STATUS_INVALID

MINIO_UPLOAD_FAILED
MINIO_OBJECT_NOT_FOUND
```

统一异常返回示例：

```json
{
  "code": 41003,
  "message": "当前文件格式不支持",
  "data": null,
  "requestId": "7ad6c2f0e51a4c76"
}
```

---

# 11. 重要设计原则

## 11.1 文件和任务分离

不要设计成一个同步接口：

```text
上传文件并等待分析完成
```

应拆分为：

```text
上传文件 → 返回 fileId
创建任务 → 返回 taskId
后台异步处理
```

---

## 11.2 时间统一使用毫秒

数据库和接口统一使用：

```text
startMs
endMs
durationMs
```

不要存储：

```text
00:01:25.300
```

时间显示格式由前端转换。

---

## 11.3 文件存 MinIO，元数据存 MySQL

MySQL 保存：

```text
bucket_name
object_key
size_bytes
sha256
duration_ms
sample_rate
channels
```

MinIO 保存：

```text
原始文件
标准化音频
后续处理产物
```

---

## 11.4 前端不能直接修改任务状态

禁止设计：

```http
PUT /api/v1/audio-tasks/{taskId}
{
  "status": "COMPLETED"
}
```

任务状态必须由后端任务执行器根据真实执行结果更新。

---

## 11.5 所有资源都要校验用户归属

查询以下资源时都必须校验：

```text
file.userId == currentUserId
task.userId == currentUserId
issue.task.userId == currentUserId
```

不能只根据主键查询后直接返回。

---

## 11.6 JSON 只保存扩展数据

适合放 JSON：

- ASR 原始返回
- 检测指标
- 任务配置
- 阶段输入输出摘要

不适合只放 JSON：

- 用户 ID
- 任务状态
- 问题类型
- 开始和结束时间
- 文件 ID

---

## 11.7 第一轮可以不使用数据库物理外键

`task_id`、`file_id`、`transcript_segment_id` 等字段作为逻辑外键，通过业务代码、索引和事务保证一致性。

这样便于后续：

- 逻辑删除
- 批量导入
- 数据迁移
- 异步处理
- 分库分表扩展

---

# 12. 推荐开发顺序

## 阶段一：文件接入

1. 创建 `audio_file` 表。
2. 搭建 MinIO。
3. 完成普通文件上传。
4. 完成文件信息查询。
5. 完成临时试听和下载地址获取。

## 阶段二：任务管理

1. 创建 `audio_task`。
2. 创建 `audio_task_stage`。
3. 完成创建分析任务接口。
4. 完成任务详情和列表查询。
5. 完成任务阶段查询。

## 阶段三：音频预处理

1. 接入 FFmpeg。
2. 从 MP4 提取音频。
3. 统一采样率、声道和格式。
4. 将标准化音频保存到 MinIO。
5. 更新任务阶段和文件信息。

## 阶段四：ASR 转写

1. 创建 `audio_transcript_segment`。
2. 接入 Whisper 或第三方 ASR。
3. 保存句子级时间轴转写。
4. 完成转写结果查询接口。

## 阶段五：问题检测

1. 创建 `audio_issue`。
2. 实现长时间静音检测。
3. 实现音量过低、过高检测。
4. 保存片段级问题数据。
5. 完成问题列表、详情和统计接口。

---

# 13. 第一轮验收标准

第一轮完成后，系统应能跑通：

```text
上传一个 MP3 或 MP4 文件
    ↓
返回 fileId
    ↓
创建分析任务
    ↓
返回 taskId
    ↓
后台执行 FFmpeg 预处理
    ↓
完成 ASR 转写
    ↓
检测静音和音量问题
    ↓
前端查询任务状态
    ↓
展示带时间轴的转写文本
    ↓
展示问题片段及诊断建议
    ↓
点击问题后能够跳转到对应音频位置
```

验收时至少准备 3 份测试音频：

1. 包含明显长静音的录音。
2. 包含音量过低或过高的录音。
3. 普通清晰录音，用于验证无问题场景。

---

# 14. 后续扩展方向

第一轮稳定后，再增加：

1. `audio_repair_job` 和 `audio_repair_item`，支持静音删除与音量标准化。
2. MinIO Multipart Upload，支持分片上传与断点续传。
3. RabbitMQ 异步流水线。
4. Redis 任务进度缓存。
5. SSE 实时推送。
6. SHA-256 文件去重。
7. 阶段级 Checkpoint。
8. Agent 目标解析和工具编排。
9. 噪声检测与基础降噪。
10. 修复前后试听对比及版本管理。

---

## 文档结论

第一轮的重点不是一次性加入大量中间件和 AI 能力，而是先完成一条稳定、可演示、可扩展的基础链路：

```text
文件上传
→ 音频预处理
→ ASR 转写
→ 静音与音量检测
→ 时间轴诊断报告
```

在这条链路稳定后，再逐步加入分片上传、RabbitMQ、断点恢复、SSE 和 Agent 编排，项目会更容易控制，也更适合持续扩展为简历项目。
