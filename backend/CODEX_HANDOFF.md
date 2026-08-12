# AudioAgent 音频处理执行任务交接

日期：2026-07-21（Asia/Shanghai）

状态：后端实现和本地自动化验证已完成；未提交 Git，未执行数据库 SQL，未修改前端。下一阶段是人工应用迁移并使用真实 MySQL、RabbitMQ、MinIO 做端到端验收。

## 1. 已完成内容

- 已确认 `CONFIRMED` 的 `confirmationJson` 足以独立执行：执行任务只复制其中 `ACCEPTED` 的不可变步骤快照，不读取后续可能变化的当前处理方案。
- 已实现执行任务、执行步骤的实体、Mapper、状态/阶段模型、事务服务、控制器、VO、Long ID 字符串序列化和 `X-User-Id` 所有权校验。
- 已实现四个后端接口：
  - `POST /api/audio-processing/executions`
  - `GET /api/audio-processing/executions/{executionId}`
  - `POST /api/audio-processing/executions/{executionId}/retry`
  - `GET /api/audio-analysis/tasks/{taskId}/processing-execution`
- 同一 `confirmationId` 幂等创建；重复创建返回原 execution 且不重复投递。消息只包含 `executionId`。
- 已实现独立 RabbitMQ exchange、主队列、延迟重试队列和死信队列；支持手动 ack、条件抢占、自动重试、FAILED、DEAD_LETTER 和人工重试。
- `REVIEW_SILENCE` 记录为 `SKIPPED / REVIEW_ONLY_OPERATION`，不作为修复 filter；其他六类操作均有真实 FFmpeg 实现。
- Pipeline 固定顺序为：原时间轴局部增益/降噪 -> 静音裁剪 -> 两遍 loudnorm -> 峰值限制。
- 静音裁剪范围会排序、合并、去重，并使用 `atrim + asetpts + concat`；两遍 loudnorm 使用真实第一遍 JSON；limiter 使用 `10^(dBFS/20)` 转换。
- FFmpeg 通过参数列表调用，不经过 shell/cmd；设置超时并并发消费输出流；日志不记录完整命令和敏感本地路径。
- 已实现确定性临时目录、路径包含校验及成功/失败 `finally` 清理。
- 已实现从源 `AudioFile.bucketName/objectKey` 下载、非空校验、真实 Pipeline、结果文件校验、ffprobe 元数据、SHA-256、MinIO 上传和事务落库。
- 结果始终创建新的 `AudioFile`：`fileRole=REPAIR_RESULT`、`sourceFileId` 指向源文件，object key 为 `repair/{userId}/{executionId}/result.wav`；不更新也不覆盖源记录或源对象。
- 已实现上传成功但数据库提交失败时删除确定性结果对象的补偿逻辑。
- 已补充执行前的不可变步骤数量/状态一致性校验，防止缺失或篡改的落库快照进入 FFmpeg。
- MinIO 下载现在区分“对象确实不存在”和“临时存储/网络故障”：前者不可重试，后者进入重试链路。
- SQL 静态复核后移除了 `source_processing_step_id -> audio_processing_step` 外键；该字段仅作审计，因为方案重建会删除旧 plan step，而确认快照和 execution 必须继续保留。
- 已补齐成功落库、源文件不变、失败补偿、ffprobe 失败、快照缺失、FFmpeg 超时、MinIO 下载错误分类、临时目录清理等测试。
- 原有 `contextLoads` 已隔离 MinIO 并在测试中关闭 Rabbit listener 启动，完整测试不再依赖本机外部服务。
- `docs/audio-processing-execution.md` 已记录 API、Apifox、RabbitMQ、MinIO 和运行验收步骤。

## 2. 新增和修改文件

### 修改文件

- `src/main/java/com/audioagent/analysis/mapper/AudioProcessingConfirmationMapper.java`
- `src/main/java/com/audioagent/common/enums/ErrorCode.java`
- `src/main/java/com/audioagent/infrastructure/minio/MinioStorageService.java`
- `src/main/java/com/audioagent/infrastructure/minio/MinioStorageServiceImpl.java`
- `src/main/resources/application-dev.yml`
- `src/test/java/com/audioagent/AudioAgentBackendApplicationTests.java`

### 新增文档和 SQL

- `CODEX_HANDOFF.md`
- `docs/audio-processing-execution.md`
- `sql/11_create_audio_processing_execution.sql`

### 新增生产代码（40 个文件）

- `src/main/java/com/audioagent/processing/config/`：`AudioProcessingConfiguration.java`、`AudioProcessingProperties.java`
- `src/main/java/com/audioagent/processing/controller/`：`AudioProcessingExecutionController.java`
- `src/main/java/com/audioagent/processing/dispatch/`：`AudioProcessingExecutionDispatcher.java`、`RabbitAudioProcessingExecutionDispatcher.java`
- `src/main/java/com/audioagent/processing/dto/`：`CreateProcessingExecutionRequest.java`
- `src/main/java/com/audioagent/processing/entity/`：`AudioProcessingExecution.java`、`AudioProcessingExecutionStep.java`
- `src/main/java/com/audioagent/processing/exception/`：`ProcessingExecutionErrorClassifier.java`、`ProcessingExecutionException.java`
- `src/main/java/com/audioagent/processing/executor/`：`AudioProcessingExecutionExecutor.java`、`ProcessingExecutionWorkDirectory.java`
- `src/main/java/com/audioagent/processing/mapper/`：`AudioProcessingExecutionMapper.java`、`AudioProcessingExecutionStepMapper.java`
- `src/main/java/com/audioagent/processing/model/`：`ProcessingExecutionStage.java`、`ProcessingExecutionStatus.java`、`ProcessingExecutionStepStatus.java`
- `src/main/java/com/audioagent/processing/mq/`：`AudioProcessingExecutionMessage.java`、`AudioProcessingExecutionMessageListener.java`、`AudioProcessingRabbitConfig.java`、`AudioProcessingRabbitConstants.java`
- `src/main/java/com/audioagent/processing/pipeline/`：`AudioProcessingPipeline.java`、`ExecutableProcessingStep.java`、`FfmpegCommandExecutor.java`、`FfmpegValueFormatter.java`、`LoudnessNormalizeProcessor.java`、`LoudnormMeasurement.java`、`LoudnormOutputParser.java`、`PeakLimiterProcessor.java`、`ProcessingOutput.java`、`ProcessingOutputValidator.java`、`ProcessingProgressListener.java`、`SegmentAudioProcessor.java`、`SilenceTrimPlanner.java`、`SilenceTrimProcessor.java`
- `src/main/java/com/audioagent/processing/service/`：`AudioProcessingExecutionService.java`、`impl/AudioProcessingExecutionServiceImpl.java`
- `src/main/java/com/audioagent/processing/snapshot/`：`ProcessingExecutionSnapshot.java`、`ProcessingExecutionSnapshotParser.java`
- `src/main/java/com/audioagent/processing/vo/`：`ProcessingExecutionVO.java`

### 新增处理任务测试（12 个文件）

- `src/test/java/com/audioagent/processing/exception/ProcessingExecutionErrorClassifierTest.java`
- `src/test/java/com/audioagent/processing/executor/AudioProcessingExecutionExecutorPersistenceTest.java`
- `src/test/java/com/audioagent/processing/executor/AudioProcessingExecutionExecutorTest.java`
- `src/test/java/com/audioagent/processing/mq/AudioProcessingExecutionMessageListenerTest.java`
- `src/test/java/com/audioagent/processing/mq/AudioProcessingExecutionMessageTest.java`
- `src/test/java/com/audioagent/processing/pipeline/AudioProcessingPipelineIntegrationTest.java`
- `src/test/java/com/audioagent/processing/pipeline/FfmpegCommandExecutorTest.java`
- `src/test/java/com/audioagent/processing/pipeline/FfmpegValueFormatterTest.java`
- `src/test/java/com/audioagent/processing/pipeline/LoudnormOutputParserTest.java`
- `src/test/java/com/audioagent/processing/pipeline/SilenceTrimPlannerTest.java`
- `src/test/java/com/audioagent/processing/service/impl/AudioProcessingExecutionServiceImplTest.java`
- `src/test/java/com/audioagent/processing/snapshot/ProcessingExecutionSnapshotParserTest.java`

## 3. 尚未完成、需要人工执行的事项

- `sql/11_create_audio_processing_execution.sql` 尚未在任何数据库执行。
- 尚未使用真实 MySQL + RabbitMQ + MinIO 做完整 API 端到端流程。
- 尚未在真实基础设施中确认：一个 CONFIRMED 快照只产生一个 execution、一个修复对象和一条 `REPAIR_RESULT`，并能通过现有播放/下载接口访问。
- 自动重试、延迟队列、死信路由和人工重试已由单元测试覆盖，但仍需在 RabbitMQ 管理台做一次真实观察。
- 上传后数据库失败的对象删除补偿已由 mock 测试覆盖，尚未通过真实 MinIO/数据库故障注入验证。
- 前端展示执行进度和结果试听不属于本轮，前端没有修改。
- 没有创建提交；所有修改仍位于当前工作树。

## 4. SQL、配置、构建和测试记录

### SQL

- 新增 `sql/11_create_audio_processing_execution.sql`，创建 `audio_processing_execution` 和 `audio_processing_execution_step`，包含唯一键、索引、CHECK 和必要外键。
- `source_processing_step_id` 明确保留为无外键审计字段，避免方案重建删除旧步骤时破坏历史 execution。
- 本轮严格未执行任何 SQL。

### 配置

`application-dev.yml` 新增 `audio.processing`：

- `enabled: ${AUDIO_PROCESSING_ENABLED:true}`
- `max-retry-count: 3`
- `execution-timeout-seconds: 600`
- `retry-delay-milliseconds: 10000`
- `temp-root: ${java.io.tmpdir}/audio-agent/executions`
- output：WAV、`pcm_s16le`、48000 Hz、保留声道
- denoise：LIGHT -35 dB、MEDIUM -30 dB
- loudness：target LRA 11
- validation：最短 500 ms、最小 1024 bytes、时长容差 1000 ms

### 本轮命令和结果

- 恢复检查：`git rev-parse --show-toplevel`、`git branch --show-current`、`git status --short`、`git diff --stat` 均已执行；仓库根目录为 `D:/mycode/audio-agent`，当前分支保持 `feat/frontend-processing-confirmation`。
- `mvn -q -DskipTests compile`：通过。
- `mvn -q -Dtest='com.audioagent.processing.**' test`：恢复后运行通过，当时 56 个 processing 测试（包含真实 FFmpeg/ffprobe 集成测试）。
- 新增补强测试的定向 Maven 命令：通过。
- `mvn clean compile`：通过；编译 192 个生产源文件，BUILD SUCCESS。
- 第一次 `mvn test`：失败；唯一错误为原有 `AudioAgentBackendApplicationTests.contextLoads` 启动时连接 `localhost:9000` MinIO 被拒绝。业务测试和 processing 测试没有失败。该测试随后改为 Mock MinIO，并关闭测试中的 Rabbit listener。
- `mvn -q -Dtest=AudioAgentBackendApplicationTests test`：修复后通过。
- 第二次完整 `mvn test`：通过；40 个测试套件、220 个测试，Failures 0、Errors 0、Skipped 0，BUILD SUCCESS。
- 完整测试中的 `AudioProcessingPipelineIntegrationTest` 使用本机真实 FFmpeg/ffprobe，真实执行局部处理、静音裁剪、两遍 loudnorm 和 limiter；未使用 mock 结果音频。
- `git diff --check`：无空白错误；只有 Git 的 LF/CRLF 转换提示。

## 5. 当前错误和中间状态

- 当前没有编译错误。
- 当前没有失败测试或未解决的测试错误。
- 当前没有半写入的数据库状态，因为 SQL 从未执行。
- 当前工作区是预期的未提交状态：6 个 tracked 文件被修改，新增文档、SQL、40 个生产文件和 12 个 processing 测试文件未跟踪。
- Maven 输出仍有 Mockito 动态 agent 的未来兼容性警告，以及若干测试故意触发异常分支产生的日志；它们不影响本次 220 个测试全部通过。
- 尚不能称为生产环境已验收：数据库迁移和真实基础设施端到端检查仍需人工完成。

## 6. 下一次继续时的准确步骤

1. 保留当前分支和工作树，先重新读取本文件并查看 `git status --short`。
2. 在目标开发数据库备份/确认后，由人工在 Navicat 执行 `sql/11_create_audio_processing_execution.sql`；执行前确认数据库名为 `audio_agent`。不要由 Codex 自动执行。
3. 启动 MySQL、RabbitMQ、MinIO，并确认 FFmpeg/ffprobe 路径与 `application-dev.yml` 环境变量正确。
4. 启动后端，按 `docs/audio-processing-execution.md` 的 Apifox 流程准备一个 CONFIRMED confirmation，然后调用创建 execution 接口。
5. 轮询 execution 查询接口，观察 RabbitMQ 的主队列、retry queue、DLQ，并确认任务最终为 SUCCESS、progress=100、stage=COMPLETED。
6. 在数据库确认源 `audio_file` 未变化，新增且仅新增一条 `REPAIR_RESULT`，其 `source_file_id`、`result_file_id` 和元数据正确。
7. 在 MinIO 确认源对象未覆盖，结果对象位于 `repair/{userId}/{executionId}/result.wav`，再通过现有播放/下载接口验收。
8. 额外做一次可重试故障和一次不可重试故障，验证 retryCount、FAILED/DEAD_LETTER、人工 retry 和临时目录/对象补偿。
9. 只有真实联调通过后再决定是否提交；本交接未授权提交或前端修改。

## 7. 约束执行情况

- 未切换分支。
- 未执行 `git reset`、`git checkout --`、`git clean` 或丢弃修改。
- 未执行数据库 SQL。
- 未提交 Git。
- 未修改前端。
- 未覆盖原始音频。
