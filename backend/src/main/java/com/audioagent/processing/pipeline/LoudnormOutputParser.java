package com.audioagent.processing.pipeline;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.processing.exception.ProcessingExecutionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class LoudnormOutputParser {

    private final ObjectMapper objectMapper;

    public LoudnormMeasurement parse(String output) {
        try {
            int key = output == null ? -1 : output.lastIndexOf("\"input_i\"");
            int start = key < 0 ? -1 : output.lastIndexOf('{', key);
            int end = key < 0 ? -1 : output.indexOf('}', key);
            if (start < 0 || end <= start) {
                throw new IllegalArgumentException("loudnorm JSON not found");
            }
            JsonNode json = objectMapper.readTree(
                    output.substring(start, end + 1));
            return new LoudnormMeasurement(number(json, "input_i"),
                    number(json, "input_tp"),
                    number(json, "input_lra"),
                    number(json, "input_thresh"),
                    number(json, "target_offset"));
        } catch (ProcessingExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_FFMPEG_FAILED,
                    true, "FFmpeg loudness measurements could not be parsed",
                    e);
        }
    }

    private BigDecimal number(JsonNode json, String name) {
        JsonNode value = json.get(name);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException(name + " is missing");
        }
        BigDecimal result = new BigDecimal(value.asText());
        if (!Double.isFinite(result.doubleValue())) {
            throw new IllegalArgumentException(name + " is not finite");
        }
        return result;
    }
}
