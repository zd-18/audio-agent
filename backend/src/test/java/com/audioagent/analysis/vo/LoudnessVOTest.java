package com.audioagent.analysis.vo;

import com.audioagent.analysis.entity.AudioAnalysisResult;
import com.audioagent.analysis.loudness.LoudnessEvaluator;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LoudnessVOTest {

    @Test
    void zeroMetricsAreNotMistakenForMissingData() {
        AudioAnalysisResult result = new AudioAnalysisResult();
        result.setIntegratedLoudnessLufs(BigDecimal.ZERO);
        result.setLoudnessRangeLu(BigDecimal.ZERO);
        result.setSamplePeakDbfs(BigDecimal.ZERO);
        result.setTruePeakDbfs(BigDecimal.ZERO);

        LoudnessVO vo = LoudnessVO.from(result,
                new LoudnessEvaluator(new AnalysisProperties()));

        assertNotNull(vo);
        assertEquals(BigDecimal.ZERO, vo.getIntegratedLoudnessLufs());
        assertEquals(BigDecimal.ZERO, vo.getLoudnessRangeLu());
        assertEquals(BigDecimal.ZERO, vo.getSamplePeakDbfs());
        assertEquals(BigDecimal.ZERO, vo.getTruePeakDbfs());
    }
}
