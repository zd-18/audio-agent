package com.audioagent.analysis.dispatch;

import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.enums.AnalysisTaskStatus;
import com.audioagent.analysis.exception.AudioAnalysisException;
import com.audioagent.analysis.executor.AudioAnalysisTaskExecutor;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

@Slf4j
@Component
@ConditionalOnProperty(
        name = "audio.analysis.dispatch-mode",
        havingValue = "local",
        matchIfMissing = true
)
@RequiredArgsConstructor
public class LocalAudioAnalysisTaskDispatcher
        implements AudioAnalysisTaskDispatcher {

    private final AudioAnalysisTaskExecutor executor;
    private final AudioAnalysisTaskMapper taskMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    @Async("audioAnalysisExecutor")
    public void dispatch(Long taskId) {
        AudioAnalysisTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.error(
                    "Task not found for local dispatch, taskId={}",
                    taskId
            );
            return;
        }

        log.info(
                "Local dispatch executing, taskId={}, audioFileId={}",
                taskId, task.getAudioFileId()
        );

        try {
            executor.execute(taskId, task.getAudioFileId());
        } catch (AudioAnalysisException e) {
            /*
             * local 模式下不重试，所有异常直接标记 FAILED。
             */
            log.error(
                    "Local analysis failed, taskId={}, errorCode={}",
                    taskId, e.getErrorCode(), e
            );
            failTask(taskId, e);
        } catch (Exception e) {
            log.error(
                    "Unexpected local analysis error, taskId={}",
                    taskId, e
            );
            failTask(taskId, new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes.INVALID_AUDIO_FILE,
                    false,
                    "分析失败: " + truncate(e.getMessage(), 490),
                    e));
        }
    }

    private void failTask(Long taskId, AudioAnalysisException e) {
        String msg = truncate(e.getMessage(), 1000);
        transactionTemplate.execute(s -> {
            LambdaUpdateWrapper<AudioAnalysisTask> w =
                    new LambdaUpdateWrapper<>();
            w.eq(AudioAnalysisTask::getId, taskId)
                    .set(AudioAnalysisTask::getStatus,
                            AnalysisTaskStatus.FAILED)
                    .set(AudioAnalysisTask::getProgress, 0)
                    .set(AudioAnalysisTask::getErrorMessage, msg)
                    .set(AudioAnalysisTask::getLastErrorCode,
                            e.getErrorCode())
                    .set(AudioAnalysisTask::getFinishedAt,
                            LocalDateTime.now())
                    .set(AudioAnalysisTask::getUpdatedAt,
                            LocalDateTime.now());
            taskMapper.update(null, w);
            return null;
        });
    }

    private static String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen
                ? s.substring(0, maxLen - 3) + "..."
                : s;
    }
}
