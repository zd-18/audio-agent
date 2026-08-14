package com.audioagent.processing.snapshot;

import com.audioagent.analysis.vo.ProcessingConfirmationVO;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.processing.exception.ProcessingExecutionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingExecutionSnapshotParserTest {

    private ObjectMapper objectMapper;
    private ProcessingExecutionSnapshotParser parser;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        parser = new ProcessingExecutionSnapshotParser(objectMapper);
    }

    @Test
    void copiesOnlyAcceptedSupportedSteps() throws Exception {
        ProcessingExecutionSnapshot result = parse(List.of(
                segment(1, "TRIM_SEGMENT", "ACCEPTED", 1_000, 2_000),
                segment(2, "INCREASE_GAIN", "REJECTED", 2_000, 3_000),
                segment(3, "REVIEW_SILENCE", "PENDING", 3_000, 4_000)));

        assertEquals(1, result.acceptedSteps().size());
        assertEquals("TRIM_SEGMENT",
                result.acceptedSteps().getFirst().operationType());
    }

    @Test
    void acceptsNormalizeVolumeWithSafeParameters() throws Exception {
        ProcessingExecutionSnapshot result = parse(List.of(normalize(1,
                Map.of("targetLufs", -16,
                        "truePeakLimitDbfs", -1))));

        assertEquals("NORMALIZE_VOLUME",
                result.acceptedSteps().getFirst().operationType());
    }

    @Test
    void rejectsLegacyAcceptedOperationAsUnsupported() {
        ProcessingExecutionException error = assertThrows(
                ProcessingExecutionException.class,
                () -> parse(List.of(segment(1, "TRIM_SILENCE",
                        "ACCEPTED", 1_000, 2_000))));
        assertEquals(ErrorCode.PROCESSING_EXECUTION_UNSUPPORTED_OPERATION
                .name(), error.getFailureCode());
        assertTrue(error.getMessage().contains("regenerate"));
    }

    @Test
    void trimSegmentRequiresValidRange() {
        assertThrows(ProcessingExecutionException.class,
                () -> parse(List.of(segment(1, "TRIM_SEGMENT",
                        "ACCEPTED", 2_000, 2_000))));
    }

    @Test
    void duplicateNormalizeVolumeIsRejected() {
        assertThrows(ProcessingExecutionException.class,
                () -> parse(List.of(
                        normalize(1, Map.of("targetLufs", -16,
                                "truePeakLimitDbfs", -1)),
                        normalize(2, Map.of("targetLufs", -18,
                                "truePeakLimitDbfs", -2)))));
    }

    @Test
    void normalizeTargetOutsideSafeRangeIsRejected() {
        assertThrows(ProcessingExecutionException.class,
                () -> parse(List.of(normalize(1,
                        Map.of("targetLufs", -7,
                                "truePeakLimitDbfs", -1)))));
    }

    @Test
    void nonConfirmedSnapshotIsRejected() throws Exception {
        ProcessingConfirmationVO vo = confirmation(List.of());
        vo.setConfirmationStatus("DRAFT");
        assertThrows(ProcessingExecutionException.class,
                () -> parser.parse(objectMapper.writeValueAsString(vo)));
    }

    @Test
    void missingSnapshotIsRejected() {
        assertThrows(ProcessingExecutionException.class,
                () -> parser.parse(null));
    }

    private ProcessingExecutionSnapshot parse(
            List<ProcessingConfirmationVO.Step> steps) throws Exception {
        return parser.parse(objectMapper.writeValueAsString(
                confirmation(steps)));
    }

    private ProcessingConfirmationVO confirmation(
            List<ProcessingConfirmationVO.Step> steps) {
        return ProcessingConfirmationVO.builder()
                .confirmationId(60L).taskId(10L).audioFileId(20L)
                .planId(30L).sourcePlanRevision(2)
                .confirmationStatus("CONFIRMED")
                .acceptedStepCount((int) steps.stream()
                        .filter(step -> "ACCEPTED".equals(
                                step.getDecision())).count())
                .rejectedStepCount(0).pendingStepCount(0)
                .steps(steps).build();
    }

    private ProcessingConfirmationVO.Step normalize(
            int order, Map<String, Object> parameters) {
        return step(order, "NORMALIZE_VOLUME", "ACCEPTED",
                null, null, parameters);
    }

    private ProcessingConfirmationVO.Step segment(
            int order, String operation, String decision,
            long startMs, long endMs) {
        return step(order, operation, decision, startMs, endMs,
                Map.of("mode", "REVIEW_BEFORE_APPLY"));
    }

    private ProcessingConfirmationVO.Step step(
            int order, String operation, String decision,
            Long startMs, Long endMs, Map<String, Object> parameters) {
        return ProcessingConfirmationVO.Step.builder()
                .stepConfirmationId(100L + order)
                .sourceStepId(200L + order)
                .stepOrder(order).operationType(operation)
                .decision(decision).userConfirmed(true)
                .startMs(startMs).endMs(endMs)
                .effectiveParameters(parameters).build();
    }
}
