package com.audioagent.agent.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAgentConversationRequest {

    /** 内容问答会话的转写稿 ID；与 audioFileId 二选一，均可空。 */
    @Pattern(regexp = "[1-9]\\d{0,18}",
            message = "transcriptId is invalid")
    private String transcriptId;

    /** 音频处理会话的音频文件 ID；与 transcriptId 二选一，均可空。 */
    @Pattern(regexp = "[1-9]\\d{0,18}",
            message = "audioFileId is invalid")
    private String audioFileId;

    @Size(max = 120, message = "title must not exceed 120 characters")
    private String title;
}
