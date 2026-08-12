package com.audioagent.contentanalysis.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContentAnalysisOutput {

    private Summary summary;
    private List<KeyPoint> keyPoints;
    private List<Chapter> chapters;
    private List<SpeechIssue> speechIssues;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Summary {
        private String oneSentence;
        private String detailed;
        private List<String> topics;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class KeyPoint {
        private Integer order;
        private String title;
        private String description;
        private List<String> evidenceChunkIds;
        private String evidenceQuote;
        private Long startMs;
        private Long endMs;
        private List<Integer> sourceSegmentOrders;
        private TimePrecision timePrecision;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Chapter {
        private Integer order;
        private String title;
        private String summary;
        private String startChunkId;
        private String endChunkId;
        private Long startMs;
        private Long endMs;
        private TimePrecision timePrecision;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SpeechIssue {
        private Integer order;
        private SpeechIssueType type;
        private SpeechIssueSeverity severity;
        private String description;
        private List<String> evidenceChunkIds;
        private String evidenceQuote;
        private String suggestion;
        private Long startMs;
        private Long endMs;
        private List<Integer> sourceSegmentOrders;
        private TimePrecision timePrecision;
    }
}
