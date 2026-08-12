package com.audioagent.transcription.executor;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.enums.FileStatus;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.infrastructure.minio.MinioStorageService;
import com.audioagent.transcription.client.AsrClient;
import com.audioagent.transcription.dto.AsrTranscriptionRequest;
import com.audioagent.transcription.dto.AsrTranscriptionResponse;
import com.audioagent.transcription.entity.AudioTranscriptionTask;
import com.audioagent.transcription.exception.TranscriptionErrorClassifier;
import com.audioagent.transcription.exception.TranscriptionException;
import com.audioagent.transcription.mapper.AudioTranscriptionTaskMapper;
import com.audioagent.transcription.model.TranscriptionTaskStatus;
import com.audioagent.transcription.service.TranscriptionResultPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioTranscriptionTaskExecutor {

    private static final Set<String> SAFE_EXTENSIONS = Set.of(
            "mp3", "wav", "m4a", "mp4", "aac", "ogg", "webm");

    private final AudioTranscriptionTaskMapper taskMapper;
    private final AudioFileMapper audioFileMapper;
    private final MinioStorageService storageService;
    private final TranscriptionWorkDirectory workDirectories;
    private final AudioStandardizer standardizer;
    private final AsrClient asrClient;
    private final AsrResponseValidator responseValidator;
    private final TranscriptionResultPersistenceService persistenceService;
    private final TranscriptionErrorClassifier errorClassifier;

    public void execute(Long taskId) {
        AudioTranscriptionTask task = requireRunningTask(taskId);
        Path workDirectory = null;
        long startedAt = System.nanoTime();
        try {
            AudioFile audioFile = requireAudioFile(task);
            workDirectory = workDirectories.prepare(taskId);
            Path input = download(audioFile, workDirectory);
            taskMapper.updateProgress(taskId, 25, LocalDateTime.now());
            Path standardized = standardizer.standardize(
                    input, workDirectory);
            taskMapper.updateProgress(taskId, 45, LocalDateTime.now());

            validateStandardized(taskId, standardized);

            log.info("Transcription calling ASR, taskId={}, stage=CALL_ASR, "
                            + "fileName={}, fileSize={}, exists=true, readable=true",
                    taskId, standardized.getFileName(),
                    Files.size(standardized));

            AsrTranscriptionResponse response = asrClient.transcribe(
                    new AsrTranscriptionRequest(
                            standardized,
                            task.getLanguage(),
                            Boolean.TRUE.equals(
                                    task.getEnableSpeakerDiarization())));
            responseValidator.validate(response);
            taskMapper.updateProgress(taskId, 85, LocalDateTime.now());
            persistenceService.save(task, response);
            log.info("Transcription completed, taskId={}, fileId={}, "
                            + "stage=COMPLETED, fullTextLength={}, "
                            + "segmentCount={}, durationMs={}, elapsedMs={}",
                    taskId, task.getAudioFileId(),
                    response.getFullText() == null
                            ? 0 : response.getFullText().length(),
                    response.getSegments().size(),
                    response.getDurationMs(),
                    (System.nanoTime() - startedAt) / 1_000_000);
        } catch (TranscriptionException e) {
            log.error("Transcription execution failed, taskId={}, "
                            + "stage=EXECUTE, exceptionClass={}, "
                            + "exceptionMessage={}",
                    taskId, e.getClass().getName(), e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Transcription execution failed, taskId={}, "
                            + "stage=EXECUTE, exceptionClass={}, "
                            + "exceptionMessage={}",
                    taskId, e.getClass().getName(), e.getMessage(), e);
            throw errorClassifier.classify(e);
        } finally {
            workDirectories.cleanQuietly(taskId);
        }
    }

    private void validateStandardized(Long taskId, Path standardized) {
        if (standardized == null
                || !Files.isRegularFile(standardized)
                || !Files.isReadable(standardized)
                || getFileSize(standardized) <= 0) {
            log.error("Transcription standardized file invalid, taskId={}, "
                            + "stage=CALL_ASR, fileName={}, fileSize={}, "
                            + "exists={}, readable={}",
                    taskId,
                    standardized == null ? null : standardized.getFileName(),
                    getFileSize(standardized),
                    standardized != null && Files.isRegularFile(standardized),
                    standardized != null && Files.isReadable(standardized));
            throw new TranscriptionException(
                    ErrorCode.AUDIO_STANDARDIZATION_FAILED,
                    false,
                    "标准化音频文件无效");
        }
    }

    private static long getFileSize(Path path) {
        if (path == null) {
            return -1;
        }
        try {
            return Files.size(path);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private AudioTranscriptionTask requireRunningTask(Long taskId) {
        if (taskId == null || taskId <= 0) {
            throw new TranscriptionException(
                    ErrorCode.TRANSCRIPTION_TASK_NOT_FOUND, false,
                    "转写任务不存在");
        }
        AudioTranscriptionTask task = taskMapper.selectById(taskId);
        if (task == null
                || task.getStatus() != TranscriptionTaskStatus.RUNNING) {
            throw new TranscriptionException(
                    ErrorCode.TRANSCRIPTION_TASK_NOT_FOUND, false,
                    "转写任务不存在或状态已变化");
        }
        return task;
    }

    private AudioFile requireAudioFile(AudioTranscriptionTask task) {
        AudioFile file = audioFileMapper.selectById(task.getAudioFileId());
        if (file == null || Integer.valueOf(1).equals(file.getDeleted())
                || file.getFileStatus() != FileStatus.AVAILABLE
                || !task.getUserId().equals(file.getUserId())
                || file.getBucketName() == null
                || file.getObjectKey() == null) {
            throw new TranscriptionException(
                    ErrorCode.TRANSCRIPTION_NOT_AVAILABLE, false,
                    "源音频当前不可用于转写");
        }
        return file;
    }

    private Path download(AudioFile file, Path workDirectory) {
        String extension = file.getExtension() == null ? ""
                : file.getExtension().toLowerCase(Locale.ROOT);
        if (!SAFE_EXTENSIONS.contains(extension)) {
            throw new TranscriptionException(
                    ErrorCode.AUDIO_FILE_FORMAT_UNSUPPORTED, false,
                    "当前音频格式不支持转写");
        }
        Path input = workDirectory.resolve("source." + extension);
        try (InputStream stream = storageService.getObject(
                file.getBucketName(), file.getObjectKey())) {
            Files.copy(stream, input, StandardCopyOption.REPLACE_EXISTING);
            if (!Files.isRegularFile(input) || Files.size(input) <= 0) {
                throw new TranscriptionException(
                        ErrorCode.TRANSCRIPTION_NOT_AVAILABLE, false,
                        "源音频内容为空");
            }
            return input;
        } catch (TranscriptionException e) {
            throw e;
        } catch (BusinessException e) {
            boolean retryable = e.getCode()
                    != ErrorCode.MINIO_OBJECT_NOT_FOUND.getCode();
            throw new TranscriptionException(
                    ErrorCode.TRANSCRIPTION_NOT_AVAILABLE, retryable,
                    retryable ? "音频文件暂时无法读取，请稍后重试"
                            : "音频文件不存在", e);
        } catch (Exception e) {
            throw new TranscriptionException(
                    ErrorCode.TRANSCRIPTION_NOT_AVAILABLE, true,
                    "音频文件暂时无法读取，请稍后重试", e);
        }
    }
}
