package com.audioagent.agent.workflow.planner;

import com.audioagent.agent.exception.AgentExecutionException;
import com.audioagent.analysis.processing.ProcessingOperationCatalog;
import com.audioagent.analysis.processing.ProcessingOperationType;
import com.audioagent.analysis.processing.ProcessingParameterValidator;
import com.audioagent.analysis.processing.ProcessingStepDraft;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentProcessingPlanParserTest {

    private AgentProcessingPlanParser parser;

    @BeforeEach
    void setUp() {
        parser = new AgentProcessingPlanParser(new ObjectMapper(),
                new ProcessingParameterValidator(new AnalysisProperties()),
                new ProcessingOperationCatalog());
    }

    @Test
    void parsesSupportedOrderedPlan() {
        var plan = parser.parse("""
                {
                  "summary":"先裁剪片段，再统一音量",
                  "steps":[
                    {"order":1,"operationType":"TRIM_SEGMENT",
                     "parameters":{},"startMs":1000,"endMs":2000,
                     "reason":"移除无关片段"},
                    {"order":2,"operationType":"NORMALIZE_VOLUME",
                     "parameters":{"targetLufs":-16,"truePeakLimitDbfs":-1},
                     "startMs":null,"endMs":null,"reason":"改善听感"}
                  ]
                }
                """, 10_000);

        assertEquals(2, plan.steps().size());
        assertEquals(ProcessingOperationType.TRIM_SEGMENT,
                plan.steps().getFirst().operationType());
        assertEquals(9_000L, plan.estimatedOutputDurationMs());
    }

    @Test
    void parsesDenoiseWholeAudioStep() {
        var plan = parser.parse("""
                {
                  "summary":"智能降噪",
                  "steps":[
                    {"order":1,"operationType":"DENOISE",
                     "parameters":{"strength":"STRONG"},
                     "startMs":null,"endMs":null,"reason":"背景噪声较大"}
                  ]
                }
                """, 10_000);

        assertEquals(1, plan.steps().size());
        assertEquals(ProcessingOperationType.DENOISE,
                plan.steps().getFirst().operationType());
        assertEquals("STRONG",
                plan.steps().getFirst().parameters().get("strength"));
        assertEquals(10_000L, plan.estimatedOutputDurationMs());
    }

    @Test
    void rejectsDenoiseWithSegmentRangeOrInvalidStrength() {
        AgentExecutionException rangeError = assertThrows(
                AgentExecutionException.class, () -> parser.parse("""
                {"summary":"降噪","steps":[
                  {"order":1,"operationType":"DENOISE",
                   "parameters":{"strength":"MEDIUM"},
                   "startMs":1000,"endMs":2000,"reason":"降噪"}]}
                """, 10_000));
        assertEquals(ErrorCode.AGENT_PLAN_INVALID,
                rangeError.getErrorCode());
        assertThrows(AgentExecutionException.class, () -> parser.parse("""
                {"summary":"降噪","steps":[
                  {"order":1,"operationType":"DENOISE",
                   "parameters":{"strength":"EXTREME"},
                   "startMs":null,"endMs":null,"reason":"降噪"}]}
                """, 10_000));
    }

    @Test
    void rejectsUnsupportedOperationType() {
        AgentExecutionException error = assertThrows(
                AgentExecutionException.class, () -> parser.parse("""
                {"summary":"降噪","steps":[
                  {"order":1,"operationType":"DENOISE_REVIEW",
                   "parameters":{},"startMs":null,"endMs":null,
                   "reason":"存在噪声"}]}
                """, 10_000));
        assertEquals(ErrorCode.AGENT_PLAN_INVALID, error.getErrorCode());
    }

    @Test
    void rejectsInvalidParameters() {
        AgentExecutionException error = assertThrows(
                AgentExecutionException.class, () -> parser.parse("""
                {"summary":"统一音量","steps":[
                  {"order":1,"operationType":"NORMALIZE_VOLUME",
                   "parameters":{"targetLufs":4,"truePeakLimitDbfs":-1},
                   "startMs":null,"endMs":null,"reason":"改善听感"}]}
                """, 10_000));
        assertEquals(ErrorCode.AGENT_PLAN_INVALID, error.getErrorCode());
    }

    @Test
    void rejectsCommandOrUnknownFields() {
        assertThrows(AgentExecutionException.class, () -> parser.parse("""
                {"summary":"统一音量","ffmpegCommand":"ffmpeg -i a b",
                 "steps":[{"order":1,"operationType":"NORMALIZE_VOLUME",
                 "parameters":{"targetLufs":-16,"truePeakLimitDbfs":-1},
                 "startMs":null,"endMs":null,"reason":"改善听感"}]}
                """, 10_000));
    }

    @Test
    void parsesSilenceCleanupCompressForLongPauseRequirement() {
        var plan = parser.parse("""
                {
                  "summary":"把停顿压短",
                  "steps":[
                    {"order":1,"operationType":"SILENCE_CLEANUP",
                     "parameters":{"mode":"COMPRESS",
                     "minSilenceMs":3000,"keepSilenceMs":800},
                     "startMs":null,"endMs":null,
                     "reason":"用户希望压缩较长的停顿"}
                  ]
                }
                """, 10_000);

        assertEquals(1, plan.steps().size());
        ProcessingStepDraft step = plan.steps().getFirst();
        assertEquals(ProcessingOperationType.SILENCE_CLEANUP,
                step.operationType());
        assertEquals("COMPRESS", step.parameters().get("mode"));
        assertEquals(3000, step.parameters().get("minSilenceMs"));
        assertEquals(800, step.parameters().get("keepSilenceMs"));
        assertEquals(null, step.startMs());
        assertEquals(10_000L, plan.estimatedOutputDurationMs());
    }

    @Test
    void parsesSilenceCleanupRemoveForExplicitDeletionRequirement() {
        var plan = parser.parse("""
                {
                  "summary":"删除长静音",
                  "steps":[
                    {"order":1,"operationType":"SILENCE_CLEANUP",
                     "parameters":{"mode":"REMOVE",
                     "minSilenceMs":3000,"keepSilenceMs":800},
                     "startMs":null,"endMs":null,"reason":"用户要求删除静音"}
                  ]
                }
                """, 10_000);

        assertEquals(ProcessingOperationType.SILENCE_CLEANUP,
                plan.steps().getFirst().operationType());
        assertEquals("REMOVE",
                plan.steps().getFirst().parameters().get("mode"));
    }

    @Test
    void rejectsSilenceCleanupWithSegmentRange() {
        AgentExecutionException error = assertThrows(
                AgentExecutionException.class, () -> parser.parse("""
                {"summary":"删除静音","steps":[
                  {"order":1,"operationType":"SILENCE_CLEANUP",
                   "parameters":{"mode":"REMOVE",
                   "minSilenceMs":3000,"keepSilenceMs":800},
                   "startMs":1000,"endMs":4000,"reason":"删除静音"}]}
                """, 10_000));
        assertEquals(ErrorCode.AGENT_PLAN_INVALID, error.getErrorCode());
    }

    @Test
    void rejectsDuplicateSilenceCleanupSteps() {
        AgentExecutionException error = assertThrows(
                AgentExecutionException.class, () -> parser.parse("""
                {"summary":"压缩停顿","steps":[
                  {"order":1,"operationType":"SILENCE_CLEANUP",
                   "parameters":{"mode":"COMPRESS",
                   "minSilenceMs":3000,"keepSilenceMs":800},
                   "startMs":null,"endMs":null,"reason":"压缩停顿"},
                  {"order":2,"operationType":"SILENCE_CLEANUP",
                   "parameters":{"mode":"REMOVE",
                   "minSilenceMs":3000,"keepSilenceMs":800},
                   "startMs":null,"endMs":null,"reason":"删除静音"}]}
                """, 10_000));
        assertEquals(ErrorCode.AGENT_PLAN_INVALID, error.getErrorCode());
    }

    @Test
    void acceptsSilenceCleanupCombinedWithDenoiseAndNormalize() {
        var plan = parser.parse("""
                {
                  "summary":"压缩停顿并降噪，最后统一音量",
                  "steps":[
                    {"order":1,"operationType":"SILENCE_CLEANUP",
                     "parameters":{"mode":"COMPRESS",
                     "minSilenceMs":3000,"keepSilenceMs":800},
                     "startMs":null,"endMs":null,"reason":"压缩停顿"},
                    {"order":2,"operationType":"DENOISE",
                     "parameters":{"strength":"MEDIUM"},
                     "startMs":null,"endMs":null,"reason":"降噪"},
                    {"order":3,"operationType":"NORMALIZE_VOLUME",
                     "parameters":{"targetLufs":-16,"truePeakLimitDbfs":-1},
                     "startMs":null,"endMs":null,"reason":"统一音量"}
                  ]
                }
                """, 10_000);

        assertEquals(3, plan.steps().size());
        assertEquals(List.of(ProcessingOperationType.SILENCE_CLEANUP,
                        ProcessingOperationType.DENOISE,
                        ProcessingOperationType.NORMALIZE_VOLUME),
                plan.steps().stream().map(
                        ProcessingStepDraft::operationType).toList());
    }
}
