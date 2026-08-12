package com.audioagent.issue.entity;

import com.audioagent.common.enums.IssueStatus;
import com.audioagent.common.enums.IssueType;
import com.audioagent.common.enums.Repairability;
import com.audioagent.common.enums.Severity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("audio_issue")
public class AudioIssue {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long taskId;

    private Long fileId;

    private Long transcriptSegmentId;

    private Integer issueNo;

    private Long startMs;

    private Long endMs;

    private IssueType issueType;

    private Severity severity;

    private BigDecimal score;

    private String metricData;

    private String description;

    private String suggestion;

    private Repairability repairability;

    private IssueStatus issueStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
