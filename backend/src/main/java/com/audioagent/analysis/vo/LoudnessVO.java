package com.audioagent.analysis.vo;

import com.audioagent.analysis.entity.AudioAnalysisResult;
import com.audioagent.analysis.loudness.LoudnessEvaluation;
import com.audioagent.analysis.loudness.LoudnessEvaluator;
import com.audioagent.analysis.loudness.LoudnessMetrics;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoudnessVO {

    private BigDecimal integratedLoudnessLufs;

    private BigDecimal loudnessRangeLu;

    private BigDecimal samplePeakDbfs;

    private BigDecimal truePeakDbfs;

    private String loudnessLevel;

    private String peakRisk;

    private String dynamicRangeLevel;

    private List<String> suggestions;

    public static LoudnessVO from(AudioAnalysisResult result,
                                  LoudnessEvaluator evaluator) {
        if (result == null || result.getIntegratedLoudnessLufs() == null) {
            return null;
        }
        LoudnessMetrics metrics = new LoudnessMetrics(
                result.getIntegratedLoudnessLufs(),
                result.getLoudnessRangeLu(),
                result.getSamplePeakDbfs(),
                result.getTruePeakDbfs());
        LoudnessEvaluation evaluation = evaluator.evaluate(metrics);
        return LoudnessVO.builder()
                .integratedLoudnessLufs(metrics.integratedLoudnessLufs())
                .loudnessRangeLu(metrics.loudnessRangeLu())
                .samplePeakDbfs(metrics.samplePeakDbfs())
                .truePeakDbfs(metrics.truePeakDbfs())
                .loudnessLevel(evaluation.loudnessLevel())
                .peakRisk(evaluation.peakRisk())
                .dynamicRangeLevel(evaluation.dynamicRangeLevel())
                .suggestions(evaluation.suggestions())
                .build();
    }
}
