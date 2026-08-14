package com.audioagent.file.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AudioVersionChainVO {

    @Builder.Default
    private List<AudioVersionVO> versions = List.of();
}
