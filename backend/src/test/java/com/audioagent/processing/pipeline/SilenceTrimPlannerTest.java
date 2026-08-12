package com.audioagent.processing.pipeline;

import com.audioagent.analysis.processing.ProcessingOperationType;
import com.audioagent.processing.exception.ProcessingExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SilenceTrimPlannerTest {

    private SilenceTrimPlanner planner;

    @BeforeEach
    void setUp() {
        planner = new SilenceTrimPlanner();
    }

    @Test
    void appliesHeadAndTailRetention() {
        var plan = planner.plan(20_000,
                List.of(trim(10_000, 16_000, 200, 200)));
        assertEquals(List.of(new SilenceTrimPlanner.Range(
                10_200, 15_800)), plan.removed());
        assertEquals(14_400, plan.retainedDurationMs());
    }

    @Test
    void overlappingRemovalRangesAreMerged() {
        var plan = planner.plan(20_000, List.of(
                trim(1_000, 6_000, 0, 0),
                trim(5_000, 8_000, 0, 0)));
        assertEquals(List.of(new SilenceTrimPlanner.Range(1_000, 8_000)),
                plan.removed());
        assertEquals(13_000, plan.retainedDurationMs());
    }

    @Test
    void duplicateRemovalRangesAreDeduplicated() {
        var plan = planner.plan(10_000, List.of(
                trim(1_000, 2_000, 0, 0),
                trim(1_000, 2_000, 0, 0)));
        assertEquals(9_000, plan.retainedDurationMs());
        assertEquals(1, plan.removed().size());
    }

    @Test
    void touchingRemovalRangesAreMerged() {
        var merged = planner.merge(List.of(
                new SilenceTrimPlanner.Range(1_000, 2_000),
                new SilenceTrimPlanner.Range(2_000, 3_000)));
        assertEquals(List.of(new SilenceTrimPlanner.Range(1_000, 3_000)),
                merged);
    }

    @Test
    void rangesAreClampedToAudioDuration() {
        var plan = planner.plan(5_000,
                List.of(trim(4_000, 8_000, 0, 0)));
        assertEquals(List.of(new SilenceTrimPlanner.Range(4_000, 5_000)),
                plan.removed());
    }

    @Test
    void retainedRangesStayOrdered() {
        var plan = planner.plan(10_000, List.of(
                trim(7_000, 8_000, 0, 0),
                trim(2_000, 3_000, 0, 0)));
        assertEquals(List.of(
                new SilenceTrimPlanner.Range(0, 2_000),
                new SilenceTrimPlanner.Range(3_000, 7_000),
                new SilenceTrimPlanner.Range(8_000, 10_000)),
                plan.retained());
    }

    @Test
    void zeroLengthAudioIsRejected() {
        assertThrows(ProcessingExecutionException.class,
                () -> planner.plan(0, List.of()));
    }

    @Test
    void fullRemovalIsRejected() {
        assertThrows(ProcessingExecutionException.class,
                () -> planner.plan(1_000,
                        List.of(trim(0, 1_000, 0, 0))));
    }

    @Test
    void fractionalMillisecondRetentionIsRejected() {
        ExecutableProcessingStep step = new ExecutableProcessingStep(
                1L, 1, ProcessingOperationType.TRIM_SILENCE,
                0L, 1_000L,
                Map.of("suggestedKeepHeadMs", 0.5,
                        "suggestedKeepTailMs", 0));
        assertThrows(ProcessingExecutionException.class,
                () -> planner.plan(2_000, List.of(step)));
    }

    private ExecutableProcessingStep trim(long start, long end,
                                          Number head, Number tail) {
        return new ExecutableProcessingStep(1L, 1,
                ProcessingOperationType.TRIM_SILENCE, start, end,
                Map.of("suggestedKeepHeadMs", head,
                        "suggestedKeepTailMs", tail));
    }
}
