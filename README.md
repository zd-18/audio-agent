# Audio Agent

AudioAgent 是一个包含音频上传、质量分析、异步转写、音频处理和
DeepSeek 文字内容分析的前后端项目。

## DeepSeek 内容分析

本地 PowerShell 启动后端前设置：

```powershell
$env:DEEPSEEK_API_KEY="用户自己的Key"
$env:DEEPSEEK_BASE_URL="https://api.deepseek.com"
$env:DEEPSEEK_MODEL="deepseek-v4-flash"
```

API Key 为空时后端仍可启动，但创建内容分析任务会返回
`AI_SERVICE_NOT_CONFIGURED`。不要把真实 Key 写入源码、YAML、前端、
文档或 Git；生产环境应使用 Secret 管理。

内容分析只会向 DeepSeek 发送文字稿 SourceChunk、分析目标、必要的
语言/时长元信息和 JSON 输出约束。原始音频、用户 Token、数据库、
MinIO、RabbitMQ 和本地路径不会发送。账户余额不足会返回对应业务错误，
请求频率过高会进入有限次数的退避重试。

现有数据库升级按顺序执行：

`backend/sql/16_create_audio_content_analysis.sql`

`backend/sql/17_align_transcript_segment_speaker_column.sql`

全新环境按文件编号执行 `scripts/sql/01` 至 `05`，再执行
`backend/sql/06` 至 `17`。旧初始化脚本已停止创建过时版
`audio_transcript_segment`，该表统一由迁移 14 创建。

详细接口和 Apifox 验收步骤见
`backend/docs/deepseek-content-analysis.md`。
