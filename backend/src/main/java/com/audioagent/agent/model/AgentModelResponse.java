package com.audioagent.agent.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentModelResponse {

    private String answer;
    private Boolean insufficientContext;
    private List<Citation> citations = new ArrayList<>();

    @Data
    public static class Citation {
        private String segmentId;
        private String quote;
    }
}
