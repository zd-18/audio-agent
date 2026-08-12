package com.audioagent.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum AudioTaskStatus {

    PENDING(0, "待处理"),
    PROCESSING(1, "处理中"),
    WAITING_CONFIRMATION(2, "等待确认"),
    COMPLETED(3, "已完成"),
    FAILED(4, "失败"),
    CANCELLED(5, "取消");

    @EnumValue
    private final int code;
    private final String label;

    AudioTaskStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }
}
