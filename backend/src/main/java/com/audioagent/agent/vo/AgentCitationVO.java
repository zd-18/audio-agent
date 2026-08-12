package com.audioagent.agent.vo;

import com.audioagent.agent.entity.AgentMessageCitation;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AgentCitationVO {

    private String citationId;
    private String segmentId;
    private Integer segmentOrder;
    private Long startMs;
    private Long endMs;
    private String quote;

    public static AgentCitationVO from(AgentMessageCitation source) {
        return AgentCitationVO.builder()
                .citationId(source.getId().toString())
                .segmentId(source.getSegmentId().toString())
                .segmentOrder(source.getSegmentOrder())
                .startMs(source.getStartMs())
                .endMs(source.getEndMs())
                .quote(source.getQuote())
                .build();
    }
}
