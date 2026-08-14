package com.audioagent.agent.workflow.planner;

import com.audioagent.agent.workflow.model.AgentPlannerResult;
import com.audioagent.agent.workflow.model.AgentProcessingContext;

public interface AgentProcessingPlanner {

    AgentPlannerResult plan(AgentProcessingContext context,
                            String requirement);
}
