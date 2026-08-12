# 统一音频分析报告

统一报告是面向普通用户的规则化产品结果。它聚合已保存的音频概览、
整体响度和问题时间段，不会重新下载或重新分析音频，也不会保存逐帧数据。

## 生命周期

- FULL 分析保存结果和问题片段后生成报告，报告保存成功后任务才进入 `SUCCESS`。
- `audio.analysis.report.enabled=false` 时跳过报告持久化，其他分析流程照常完成。
- 历史 `SUCCESS` 任务首次查询报告时，会使用已有结果惰性创建快照。
- `task_id` 唯一，重试通过 upsert 更新当前快照，不新增第二份报告。

## 可配置评分

质量分是产品启发式指标，不是行业标准或绝对质量结论。初始分为 100，
各类问题、整体响度、真实峰值和动态范围的扣分都来自
`audio.analysis.report.score`，等级边界来自 `audio.analysis.report.grade`。
最终结果限制在 0～100。

同类问题在同一时间区域高度重叠时只计一次；不同类型问题达到
`overlap-threshold-ratio` 时，后续扣分乘以 `overlap-discount-ratio`。
这样保留多种风险的影响，同时避免对同一个时间区域重复重罚。

## 查询接口

`GET /api/audio-analysis/tasks/{taskId}/report`

任务不存在、任务未成功、历史数据不足或快照解析失败时，接口返回对应的
业务错误码。所有 Long 类型身份字段均按 JSON 字符串返回。
