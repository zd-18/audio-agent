package com.audioagent.analysis.processing;

import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProcessingPlanSummaryBuilder {

    private final AnalysisProperties properties;

    public String build(List<ProcessingStepDraft> steps) {
        if (steps == null || steps.isEmpty()) {
            return "当前未发现需要优先处理的问题。";
        }
        if (steps.size() >= properties.getProcessingPlan()
                .getManyStepsThreshold()) {
            return "本次生成 " + steps.size()
                    + " 个处理步骤，请按优先级依次确认。";
        }
        boolean trim = contains(steps,
                ProcessingOperationType.TRIM_SEGMENT);
        boolean normalize = contains(steps,
                ProcessingOperationType.NORMALIZE_VOLUME);
        boolean denoise = contains(steps,
                ProcessingOperationType.DENOISE);
        if (trim && normalize) {
            return "建议先裁剪已标记片段，再统一整段音量。";
        }
        if (trim && denoise) {
            return "建议先裁剪已标记片段，再降低背景噪声。";
        }
        if (trim) {
            return "检测到可裁剪片段，请试听并确认时间范围。";
        }
        if (denoise && normalize) {
            return "建议先降低背景噪声，再统一整段音量。";
        }
        if (denoise) {
            return "检测到持续背景噪声，建议执行智能降噪。";
        }
        return "检测到整体音量偏差，建议执行音量标准化。";
    }

    private boolean contains(List<ProcessingStepDraft> steps,
                             ProcessingOperationType type) {
        return steps.stream().anyMatch(step -> step.operationType() == type);
    }
}
