package com.audioagent.setting.vo;

import com.audioagent.setting.entity.UserSetting;
import com.audioagent.setting.enums.DenoiseStrength;
import com.audioagent.setting.enums.ProcessingStrategy;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class UserSettingVO {

    private final DenoiseStrength defaultDenoiseStrength;
    private final ProcessingStrategy processingStrategy;
    private final Boolean autoLimitPeak;
    private final Boolean requireStepConfirmation;
    private final Boolean preservePlaybackPosition;
    private final Integer issueContextSeconds;
    private final BigDecimal defaultPlaybackVolume;
    private final Integer defaultPageSize;
    private final Boolean notifyOnTaskComplete;
    private final Boolean autoOpenResultPage;

    public static UserSettingVO from(UserSetting setting) {
        return UserSettingVO.builder()
                .defaultDenoiseStrength(
                        setting.getDefaultDenoiseStrength())
                .processingStrategy(setting.getProcessingStrategy())
                .autoLimitPeak(setting.getAutoLimitPeak())
                .requireStepConfirmation(
                        setting.getRequireStepConfirmation())
                .preservePlaybackPosition(
                        setting.getPreservePlaybackPosition())
                .issueContextSeconds(setting.getIssueContextSeconds())
                .defaultPlaybackVolume(
                        setting.getDefaultPlaybackVolume())
                .defaultPageSize(setting.getDefaultPageSize())
                .notifyOnTaskComplete(setting.getNotifyOnTaskComplete())
                .autoOpenResultPage(setting.getAutoOpenResultPage())
                .build();
    }
}
