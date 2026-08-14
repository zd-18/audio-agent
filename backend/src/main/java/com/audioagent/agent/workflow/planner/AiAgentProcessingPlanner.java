package com.audioagent.agent.workflow.planner;

import com.audioagent.agent.config.AgentProperties;
import com.audioagent.agent.workflow.model.AgentPlannerResult;
import com.audioagent.agent.workflow.model.AgentProcessingContext;
import com.audioagent.ai.AiChatClient;
import com.audioagent.ai.AiChatMessage;
import com.audioagent.ai.AiChatRequest;
import com.audioagent.ai.AiChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AiAgentProcessingPlanner implements AgentProcessingPlanner {

    private final AiChatClient aiChatClient;
    private final AgentProperties properties;
    private final AgentProcessingPlanPrompt prompt;
    private final AgentProcessingPlanParser parser;

    @Override
    public AgentPlannerResult plan(AgentProcessingContext context,
                                   String requirement) {
        AiChatResponse response = aiChatClient.chat(new AiChatRequest(
                properties.getModelName(), List.of(
                AiChatMessage.system(prompt.systemPrompt()),
                AiChatMessage.user(prompt.userPrompt(context, requirement))),
                0.1, properties.getAnswerMaxTokens(),
                Map.of("type", "json_object"), Duration.ofSeconds(
                properties.getRequestTimeoutSeconds())));
        return new AgentPlannerResult(parser.parse(response.content(),
                context.durationMs()), response.model(),
                response.promptTokens(), response.completionTokens(),
                response.totalTokens());
    }
}
