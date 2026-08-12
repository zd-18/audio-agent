package com.audioagent.contentanalysis.model;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.contentanalysis.exception.ContentAnalysisException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AnalysisTypeCodecTest {

    @Test
    void readsValidJson() {
        AnalysisTypeCodec codec =
                new AnalysisTypeCodec(new ObjectMapper());

        List<AnalysisType> result = codec.read(
                "[\"SUMMARY\",\"KEY_POINTS\"]", 91L);

        assertEquals(
                List.of(AnalysisType.KEY_POINTS, AnalysisType.SUMMARY),
                result);
    }

    @Test
    void nullDoesNotReachObjectMapper() {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        AnalysisTypeCodec codec = new AnalysisTypeCodec(objectMapper);

        ContentAnalysisException failure = assertThrows(
                ContentAnalysisException.class,
                () -> codec.read(null, 91L));

        assertEquals(ErrorCode.AI_TASK_DATA_INVALID,
                failure.getErrorCode());
        assertFalse(failure.isRetryable());
        verifyNoInteractions(objectMapper);
    }

    @Test
    void invalidJsonHasClearInternalDataError() {
        AnalysisTypeCodec codec =
                new AnalysisTypeCodec(new ObjectMapper());

        ContentAnalysisException failure = assertThrows(
                ContentAnalysisException.class,
                () -> codec.read("{not-json}", 91L));

        assertEquals(ErrorCode.AI_TASK_DATA_INVALID,
                failure.getErrorCode());
        assertEquals("智能分析任务数据不完整",
                failure.getMessage());
        assertFalse(failure.isRetryable());
    }
}
