package com.audioagent.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum FileStatus {

    UPLOADING(1, "上传中"),
    AVAILABLE(2, "可用"),
    PROCESSING(3, "处理中"),
    FAILED(4, "失败"),
    DELETED(5, "已删除"),
    ARCHIVED(6, "已归档");

    @EnumValue
    private final int code;
    private final String label;

    FileStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }
}
