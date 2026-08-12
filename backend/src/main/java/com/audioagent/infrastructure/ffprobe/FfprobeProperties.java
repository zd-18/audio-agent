package com.audioagent.infrastructure.ffprobe;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "audio.ffprobe")
public class FfprobeProperties {

    @NotBlank
    private String executable = "ffprobe";

    @Min(1)
    @Max(60)
    private int timeoutSeconds = 15;
}
