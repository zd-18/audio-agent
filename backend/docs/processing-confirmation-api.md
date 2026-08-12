# 用户确认处理步骤后端接口

## 1. 数据库升级

先在 Navicat 中选择 `audio_agent` 数据库，然后完整执行：

- `sql/10_create_audio_processing_confirmation.sql`

脚本会先检查并补充 `audio_processing_plan.plan_revision`，然后创建：

- `audio_processing_confirmation`
- `audio_processing_step_confirmation`

系统建议仍保存在 processing plan/step 表；用户决定单独保存在 confirmation 表。本功能不会修改音频，也不会调用 FFmpeg。

`plan_version` 是规则生成器版本；`plan_revision` 是同一分析任务的方案重生成次数。首次方案为 revision 1，每次调用方案生成接口都会递增。

## 2. 公共请求约定

以下新接口都需要请求头：

```http
X-User-Id: 7
Content-Type: application/json
```

服务端根据 task 关联的 `audio_file.user_id` 校验归属。客户端不能提交 planId、audioFileId 或源步骤列表。

## 3. 接口

### 创建或获取当前 revision 的草稿

```http
POST /api/audio-analysis/tasks/{taskId}/processing-confirmation
```

前提：任务状态为 `SUCCESS`，当前方案状态为 `READY`。同一 `planId + planRevision` 重复调用是幂等的：已有 DRAFT 或 CONFIRMED 时直接返回原记录。

### 查询当前 revision 的确认单

```http
GET /api/audio-analysis/tasks/{taskId}/processing-confirmation
```

只查询当前最新 revision。未创建时返回 `PROCESSING_CONFIRMATION_NOT_FOUND`；steps 始终按 `stepOrder` 排序，空列表返回 `[]`。

### 修改单个步骤决定

```http
PUT /api/audio-analysis/processing-confirmations/{confirmationId}/steps/{stepConfirmationId}
```

示例：

```json
{
  "decision": "ACCEPTED",
  "userConfirmed": true,
  "parameterOverrides": {
    "suggestedGainDb": 2.5
  },
  "userNote": "试听后确认轻微提升"
}
```

`decision` 仅支持 `PENDING`、`ACCEPTED`、`REJECTED`。PUT 会用原始参数加本次完整 overrides 重新生成 effectiveParameters，并重新统计 accepted/rejected/pending。

### 最终确认

```http
POST /api/audio-analysis/processing-confirmations/{confirmationId}/confirm
```

要求没有 PENDING；被接受且 `requiresConfirmation=true` 的步骤必须 `userConfirmed=true`。确认成功后状态不可逆地变为 `CONFIRMED`，并保存完整结构化 `confirmation_json` 快照。允许全部拒绝；此时 `acceptedStepCount=0`，未来不得创建执行任务。

### 取消草稿

```http
POST /api/audio-analysis/processing-confirmations/{confirmationId}/cancel
```

仅 DRAFT 可取消。取消后保留数据库历史，不能再编辑或确认。

## 4. 参数白名单

| operationType | 可修改字段 | 校验 |
|---|---|---|
| `TRIM_SILENCE` | `suggestedKeepHeadMs`, `suggestedKeepTailMs` | 均不小于 0，合计严格小于片段时长 |
| `INCREASE_GAIN` | `suggestedGainDb` | 大于 0，绝对值不超过配置 `maxAbsoluteDb` |
| `DECREASE_GAIN` | `suggestedGainDb` | 小于 0，绝对值不超过配置 `maxAbsoluteDb` |
| `DENOISE_REVIEW` | `suggestedStrength` | 仅 `LIGHT`、`MEDIUM` |
| `NORMALIZE_LOUDNESS` | `targetLufs`, `truePeakLimitDbfs` | target -24 到 -8；true peak -6 到 0 |
| `LIMIT_PEAK` | `truePeakLimitDbfs` | -6 到 0 |
| `REVIEW_SILENCE` | 无 | 任意 override 都会被拒绝 |

未知字段、null、NaN、Infinity 都会返回 `PROCESSING_PARAMETER_INVALID`。`operationType`、`startMs`、`endMs`、`sourceIssueId` 不可通过该请求修改。

## 5. Apifox 验证顺序

1. 用一个 `SUCCESS` task 调用 `POST /tasks/{taskId}/processing-plan`，记录 `planId`、`planRevision` 和 steps。
2. 调用创建确认单接口，确认状态为 DRAFT、每个方案步骤各有一条 PENDING，Long ID 在 JSON 中为字符串。
3. 重复创建，确认返回同一个 confirmationId。
4. 调用查询接口，确认步骤按 stepOrder 排序，参数是对象而不是 JSON 字符串。
5. 对每个步骤调用 PUT，分别验证 ACCEPTED、REJECTED、参数 override 合并和计数。
6. 保留一个 PENDING 调用 confirm，确认返回 `PROCESSING_CONFIRMATION_HAS_PENDING_STEPS`。
7. 对 requiresConfirmation 步骤设置 ACCEPTED 但 userConfirmed=false，确认返回 `PROCESSING_STEP_CONFIRMATION_REQUIRED`。
8. 清除所有 PENDING 后 confirm，确认状态、confirmedAt 和计数正确；再次 PUT/confirm/cancel 均应失败。
9. 新生成一个 revision，确认旧 DRAFT 变为 STALE；旧单不能修改或确认，新 revision 需要重新创建确认单。
10. 新建一张草稿调用 cancel，确认变为 CANCELLED 且不能修改。
11. 换一个 X-User-Id 重试读写，确认返回无权访问。

## 6. 后续执行任务如何消费

执行任务只能读取状态为 `CONFIRMED` 的 `confirmation_json`，把它当作不可变输入：

1. 先验证 confirmationStatus 为 CONFIRMED，且 acceptedStepCount 大于 0。
2. 只取 decision 为 ACCEPTED 的步骤。
3. 使用快照中的 operationType、startMs/endMs 和 effectiveParameters，不再读取客户端输入，也不重新套用当前规则默认值。
4. rejected 步骤只用于审计，不进入执行队列。
5. acceptedStepCount 为 0 时直接返回“无需执行”，禁止创建音频处理任务。

这样即使规则版本或当前 plan revision 后续发生变化，执行内容仍与用户最终确认时完全一致。

