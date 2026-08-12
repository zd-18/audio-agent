# DeepSeek 智能内容分析 MVP

## 配置与隐私

PowerShell：

```powershell
$env:DEEPSEEK_API_KEY="用户自己的Key"
$env:DEEPSEEK_BASE_URL="https://api.deepseek.com"
$env:DEEPSEEK_MODEL="deepseek-v4-flash"
```

默认模型为 `deepseek-v4-flash`，可通过环境变量覆盖。API Key 为空不影响
应用启动，但创建任务会返回 `AI_SERVICE_NOT_CONFIGURED`。生产环境应使用
Secret 管理 Key。只有文字稿分块、分析目标、语言/时长元信息和 JSON 约束
会发送至 DeepSeek；原始音频和平台基础设施配置不会发送。

日志只记录任务、用户、文字稿 ID、模型、Prompt 版本、字符/分块数量、
耗时、Token 数和结果数量，不记录完整文字稿、Prompt、模型原始响应、
Authorization 或 API Key。

## 数据库

已有环境按顺序执行：

1. `backend/sql/16_create_audio_content_analysis.sql`
2. `backend/sql/17_align_transcript_segment_speaker_column.sql`

它新增：

- `audio_content_analysis_task`
- `audio_content_analysis_result`

`audio_content_analysis_result.task_id` 唯一。任务和结果查询均带用户所有权，
保存结果和完成任务在一个事务内。

## API

以下接口均使用现有 Bearer Token：

- `POST /api/content-analysis/tasks`
- `GET /api/content-analysis/tasks/{taskId}`
- `GET /api/content-analysis/tasks/{taskId}/result`
- `POST /api/content-analysis/tasks/{taskId}/retry`

创建请求：

```json
{
  "transcriptId": "2082751491419303938",
  "analysisTypes": [
    "SUMMARY",
    "KEY_POINTS",
    "CHAPTERS",
    "SPEECH_ISSUES"
  ],
  "summaryStyle": "STANDARD"
}
```

所有雪花 ID 都是 JSON 字符串。MQ 消息只携带 `taskId`。任务进度依次为
5、15、30、45、75、90、100。429、5xx 和连接/读取超时最多自动重试
2 次，采用退避延迟并优先遵循合理的 `Retry-After`；认证、余额、请求、
JSON 校验和数据库结构错误不自动重试。

## Apifox 手工验收

1. 注册或登录，复制登录响应 Token，并给后续请求设置
   `Authorization: Bearer <token>`。
2. 完成一次音频转写，从文字稿响应复制字符串形式的 `transcriptId`。
3. 设置 `DEEPSEEK_API_KEY` 后重启后端；不设置时先验证创建接口返回
   `AI_SERVICE_NOT_CONFIGURED`。
4. 调用创建接口，确认响应中的 `taskId`、`transcriptId` 为字符串。
5. 每 2 秒查询任务接口，确认状态从 `PENDING/RUNNING` 到 `SUCCESS`，
   进度最终为 100。
6. 查询 result，检查 summary、keyPoints、chapters、speechIssues、usage；
   evidenceChunkIds 必须真实，时间字段来自转写片段且
   `timePrecision=SEGMENT`。
7. 使用另一用户 Token 查询同一 taskId，确认返回 `AI_TASK_NOT_FOUND`。
8. 模拟 401、402、422，确认不重试；模拟 429、503 或超时，确认重试数
   不超过 2。余额不足和限流分别返回对应安全错误。
9. 对失败任务调用 retry，确认旧失败字段被清理并重新进入异步流程。
10. 查看 RabbitMQ，确认内容分析使用独立的主队列、retry queue 和 DLQ；
    查看 MinIO，确认没有上传或修改任何原始音频对象。
