package com.audioagent.agent.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "audio-agent.agent")
public class AgentProperties {

    @NotBlank
    private String modelName = "deepseek-v4-pro";
    @NotBlank
    private String promptVersion = "transcript-chat-v1";
    @DecimalMin("0.0")
    @DecimalMax("2.0")
    private double temperature = 0.2;
    @Min(1)
    @Max(8192)
    private int answerMaxTokens = 1200;
    @Min(1)
    @Max(50)
    private int historyMessageLimit = 10;
    @Min(500)
    @Max(200000)
    private int contextMaxChars = 12000;
    @Min(1)
    @Max(1000)
    private int contextMaxSegments = 60;
    @Min(1)
    @Max(10000)
    private int maxQuestionChars = 2000;
    @Min(1)
    @Max(600)
    private int requestTimeoutSeconds = 180;
}
