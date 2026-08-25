package com.audioagent.processing.executor;

import com.audioagent.analysis.processing.ProcessingOperationType;
import com.audioagent.analysis.process.ExternalProcessContextRegistry;
import com.audioagent.analysis.probe.AudioMetadata;
import com.audioagent.analysis.probe.AudioMetadataProbe;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.enums.FileRole;
import com.audioagent.common.enums.FileStatus;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.file.service.AudioVersionSummaryBuilder;
import com.audioagent.infrastructure.minio.MinioProperties;
import com.audioagent.infrastructure.minio.MinioStorageService;
import com.audioagent.processing.entity.AudioProcessingExecution;
import com.audioagent.processing.entity.AudioProcessingExecutionStep;
import com.audioagent.processing.critic.ProcessingResultCritic;
import com.audioagent.processing.exception.ProcessingExecutionErrorClassifier;
import com.audioagent.processing.exception.ProcessingExecutionException;
import com.audioagent.processing.mapper.AudioProcessingExecutionMapper;
import com.audioagent.processing.mapper.AudioProcessingExecutionStepMapper;
import com.audioagent.processing.model.ProcessingExecutionStage;
import com.audioagent.processing.model.ProcessingExecutionStatus;
import com.audioagent.processing.pipeline.AudioProcessingPipeline;
import com.audioagent.processing.pipeline.ExecutableProcessingStep;
import com.audioagent.processing.pipeline.ProcessingOutput;
import com.audioagent.processing.pipeline.ProcessingOutputValidator;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioProcessingExecutionExecutor {

    private static final Set<String> SUPPORTED_SOURCE_EXTENSIONS = Set.of(
            "mp3", "wav", "m4a", "mp4", "aac");

    private final AudioProcessingExecutionMapper executionMapper;
    private final AudioProcessingExecutionStepMapper executionStepMapper;
    private final AudioFileMapper audioFileMapper;
    private final AudioVersionSummaryBuilder versionSummaryBuilder;
    private final MinioStorageService storageService;
    private final MinioProperties minioProperties;
    private final AudioProcessingPipeline pipeline;
    private final AudioMetadataProbe metadataProbe;
    private final ProcessingOutputValidator outputValidator;
    private final ProcessingResultCritic resultCritic;
    private final ProcessingExecutionWorkDirectory workDirectories;
    private final ProcessingExecutionErrorClassifier errorClassifier;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ExternalProcessContextRegistry processContexts;

    public void execute(Long executionId) {
        if (executionId == null || executionId <= 0) {
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_NOT_FOUND,
                    false, "Execution message is invalid");
        }
        if (executionMapper.claim(executionId, LocalDateTime.now()) != 1) {
            AudioProcessingExecution current = executionMapper
                    .selectExecutionById(executionId);
            log.info("Processing execution duplicate ignored, executionId={}, status={}",
                    executionId, current == null ? "NOT_FOUND"
                            : current.getExecutionStatus());
            return;
        }

        String processContext = "audio-processing:" + executionId;
        processContexts.begin(processContext);
        long started = System.currentTimeMillis();
        Path workDirectory = null;
        String uploadedObjectKey = null;
        boolean resultCommitted = false;
        try {
            processContexts.throwIfCurrentCancelled();
            AudioProcessingExecution execution = requireExecution(executionId);
            AudioFile source = requireSource(execution);
            workDirectory = workDirectories.prepare(executionId);
            Path input = download(source, workDirectory);
            List<AudioProcessingExecutionStep> storedSteps =
                    executionStepMapper.selectByExecutionId(executionId);
            requireExecutionSteps(execution, storedSteps);
            List<ExecutableProcessingStep> steps = storedSteps.stream()
                    .filter(step -> !"SKIPPED".equals(
                            step.getExecutionStatus()))
                    .map(this::toExecutable)
                    .toList();
            if (steps.stream().anyMatch(step -> step.operationType()
                    == ProcessingOperationType.NORMALIZE_VOLUME)) {
                log.info("Source audio identity diagnostics, executionId={}, "
                                + "originalFileName={}, extension={}",
                        executionId,
                        safeDiagnosticFileName(source.getOriginalName()),
                        safeDiagnosticExtension(source.getExtension()));
            }
            int processingSteps = executionStepMapper
                    .markExecutableProcessing(executionId,
                            LocalDateTime.now());
            if (processingSteps != execution.getExecutableStepCount()) {
                throw invalidSnapshot(
                        "Execution step states do not match the snapshot");
            }

            ProcessingOutput output = pipeline.execute(input, steps,
                    workDirectory, (stage, progress) -> executionMapper
                            .advance(executionId, stage.name(), progress,
                                    LocalDateTime.now()));
            processContexts.throwIfCurrentCancelled();

            executionMapper.advance(executionId,
                    ProcessingExecutionStage.METADATA_EXTRACTING.name(), 85,
                    LocalDateTime.now());
            AudioMetadata metadata = probeResult(output.path());
            Long actualDurationMs = metadata == null
                    ? null : metadata.getDurationMs();
            Long differenceMs = actualDurationMs == null ? null
                    : Math.abs(actualDurationMs
                    - output.expectedDurationMs());
            long toleranceMs = outputValidator.durationToleranceMs();
            log.info("Processed audio duration diagnostics, executionId={}, "
                            + "expectedDurationMs={}, actualDurationMs={}, "
                            + "differenceMs={}, toleranceMs={}, sampleRate={}, "
                            + "channels={}",
                    executionId, output.expectedDurationMs(),
                    actualDurationMs, differenceMs, toleranceMs,
                    metadata == null ? null : metadata.getSampleRate(),
                    metadata == null ? null : metadata.getChannels());
            int successfulSteps = executionStepMapper
                    .markProcessingSuccess(executionId, LocalDateTime.now());
            if (successfulSteps != execution.getExecutableStepCount()) {
                throw new ProcessingExecutionException(
                        ErrorCode.PROCESSING_EXECUTION_FAILED, false,
                        "Not all processing steps completed successfully");
            }
            executionMapper.advance(executionId,
                    ProcessingExecutionStage.REVIEWING.name(), 88,
                    LocalDateTime.now());
            resultCritic.review(output.path(), metadata,
                    output.expectedDurationMs(), executionStepMapper
                            .selectByExecutionId(executionId),
                    execution.getAcceptedStepCount());
            executionMapper.advance(executionId,
                    ProcessingExecutionStage.UPLOADING.name(), 90,
                    LocalDateTime.now());
            processContexts.throwIfCurrentCancelled();

            uploadedObjectKey = resultObjectKey(execution);
            ResultFileData result = uploadAndDigest(output.path(),
                    uploadedObjectKey);
            AudioProcessingExecution finalExecution = execution;
            String finalObjectKey = uploadedObjectKey;
            Boolean committed = transactionTemplate.execute(status ->
                    persistResult(finalExecution, source, metadata, result,
                            finalObjectKey, storedSteps));
            if (!Boolean.TRUE.equals(committed)) {
                throw new ProcessingExecutionException(
                        ErrorCode.PROCESSING_EXECUTION_FAILED,
                        true, "Processed result could not be committed");
            }
            resultCommitted = true;
            log.info("Processing execution completed, executionId={}, "
                            + "taskId={}, confirmationId={}, sourceFileId={}, "
                            + "status=SUCCESS, elapsedMs={}", executionId,
                    execution.getTaskId(), execution.getConfirmationId(),
                    source.getId(), System.currentTimeMillis() - started);
        } catch (Throwable error) {
            ProcessingExecutionException classified =
                    errorClassifier.classify(error);
            log.error("Processing execution failed, executionId={}, "
                            + "failureCode={}, retryable={}", executionId,
                    classified.getFailureCode(), classified.isRetryable());
            throw classified;
        } finally {
            if (!resultCommitted && uploadedObjectKey != null) {
                compensateDelete(uploadedObjectKey);
            }
            workDirectories.cleanQuietly(executionId);
            processContexts.complete(processContext);
        }
    }

    private AudioProcessingExecution requireExecution(Long executionId) {
        AudioProcessingExecution execution = executionMapper
                .selectExecutionById(executionId);
        if (execution == null
                || !ProcessingExecutionStatus.PROCESSING.name().equals(
                execution.getExecutionStatus())) {
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_NOT_FOUND,
                    false, "Claimed processing execution does not exist");
        }
        return execution;
    }

    private AudioFile requireSource(AudioProcessingExecution execution) {
        AudioFile source = audioFileMapper.selectById(
                execution.getAudioFileId());
        if (source == null || Integer.valueOf(1).equals(source.getDeleted())
                || source.getFileStatus() != FileStatus.AVAILABLE
                || !execution.getUserId().equals(source.getUserId())
                || source.getObjectKey() == null
                || source.getBucketName() == null) {
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_SOURCE_FILE_NOT_FOUND,
                    false, "Source audio file is unavailable");
        }
        String extension = source.getExtension() == null ? ""
                : source.getExtension().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_SOURCE_EXTENSIONS.contains(extension)) {
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_UNSUPPORTED_OPERATION,
                    false, "Source audio format is unsupported");
        }
        return source;
    }

    private void requireExecutionSteps(
            AudioProcessingExecution execution,
            List<AudioProcessingExecutionStep> steps) {
        if (steps == null || execution.getAcceptedStepCount() == null
                || execution.getExecutableStepCount() == null
                || execution.getSkippedStepCount() == null
                || steps.size() != execution.getAcceptedStepCount()) {
            throw invalidSnapshot(
                    "Execution step snapshot is incomplete");
        }
        long executable = steps.stream()
                .filter(step -> "PENDING".equals(
                        step.getExecutionStatus()))
                .count();
        if (execution.getSkippedStepCount() != 0
                || executable != execution.getExecutableStepCount()) {
            throw invalidSnapshot(
                    "Execution step counts do not match the snapshot");
        }
        for (AudioProcessingExecutionStep step : steps) {
            if (step.getId() == null || step.getStepOrder() == null
                    || !isExecutableOperation(step.getOperationType())
                    || !"PENDING".equals(step.getExecutionStatus())) {
                throw invalidSnapshot(
                        "Execution step snapshot contains an invalid state");
            }
        }
    }

    private boolean isExecutableOperation(String value) {
        try {
            return ProcessingOperationType.valueOf(value).isExecutable();
        } catch (Exception e) {
            return false;
        }
    }

    private ProcessingExecutionException invalidSnapshot(String message) {
        return new ProcessingExecutionException(
                ErrorCode.PROCESSING_EXECUTION_CONFIRMATION_NOT_READY,
                false, message);
    }

    private Path download(AudioFile source, Path workDirectory) {
        Path input = workDirectory.resolve("source."
                + source.getExtension().toLowerCase(Locale.ROOT));
        try (InputStream stream = storageService.getObject(
                source.getBucketName(), source.getObjectKey())) {
            Files.copy(stream, input, StandardCopyOption.REPLACE_EXISTING);
            if (!Files.isRegularFile(input) || Files.size(input) <= 0) {
                throw new ProcessingExecutionException(
                        ErrorCode.PROCESSING_EXECUTION_SOURCE_FILE_NOT_FOUND,
                        false, "Downloaded source audio is empty");
            }
            return input;
        } catch (ProcessingExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw errorClassifier.classify(e);
        }
    }

    private ExecutableProcessingStep toExecutable(
            AudioProcessingExecutionStep step) {
        try {
            ProcessingOperationType operation = ProcessingOperationType
                    .valueOf(step.getOperationType());
            Map<String, Object> parameters = objectMapper.readValue(
                    step.getEffectiveParametersJson(),
                    new TypeReference<>() {
                    });
            return new ExecutableProcessingStep(step.getId(),
                    step.getStepOrder(), operation, step.getStartMs(),
                    step.getEndMs(), parameters == null ? Map.of()
                    : Map.copyOf(parameters));
        } catch (Exception e) {
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_INVALID_PARAMETER,
                    false, "Persisted execution step is invalid", e);
        }
    }

    private AudioMetadata probeResult(Path result) {
        try {
            return metadataProbe.probe(result);
        } catch (RuntimeException e) {
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_OUTPUT_INVALID,
                    false, "Processed output cannot be read by ffprobe", e);
        }
    }

    private ResultFileData uploadAndDigest(Path result,
                                           String objectKey) {
        try {
            long size = Files.size(result);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(result);
                 DigestInputStream digested = new DigestInputStream(input,
                         digest)) {
                storageService.upload(objectKey, digested, size,
                        "audio/wav");
            }
            return new ResultFileData(size,
                    HexFormat.of().formatHex(digest.digest()));
        } catch (Exception e) {
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_UPLOAD_FAILED,
                    true, "Processed output could not be uploaded", e);
        }
    }

    private boolean persistResult(AudioProcessingExecution execution,
                                  AudioFile source,
                                  AudioMetadata metadata,
                                  ResultFileData result,
                                  String objectKey,
                                  List<AudioProcessingExecutionStep> steps) {
        AudioProcessingExecution current = executionMapper
                .selectExecutionById(execution.getId());
        if (current == null || current.getResultFileId() != null
                || !ProcessingExecutionStatus.PROCESSING.name().equals(
                current.getExecutionStatus())) {
            return false;
        }
        AudioFile existing = audioFileMapper.selectBySourceExecutionId(
                execution.getId());
        if (existing != null) {
            return executionMapper.complete(execution.getId(),
                    existing.getId(), LocalDateTime.now()) == 1;
        }
        Long rootAudioFileId = source.getRootAudioFileId() == null
                ? source.getId() : source.getRootAudioFileId();
        AudioFile root = audioFileMapper.selectByIdForUpdate(
                rootAudioFileId);
        if (root == null || !source.getUserId().equals(root.getUserId())) {
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_FAILED,
                    false, "Audio version root is invalid");
        }
        Integer versionNo = audioFileMapper.selectNextVersionNo(
                rootAudioFileId);
        if (versionNo == null || versionNo <= 0) {
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_FAILED,
                    false, "Audio version number could not be allocated");
        }
        LocalDateTime now = LocalDateTime.now();
        AudioFile file = new AudioFile();
        file.setId(IdWorker.getId());
        file.setUserId(source.getUserId());
        file.setSourceFileId(source.getId());
        file.setRootAudioFileId(rootAudioFileId);
        file.setVersionNo(versionNo);
        file.setVersionSummary(versionSummaryBuilder.build(steps));
        file.setSourceExecutionId(execution.getId());
        file.setFileRole(FileRole.REPAIR_RESULT);
        file.setOriginalName(resultName(source.getOriginalName(),
                execution.getId()));
        file.setExtension("wav");
        file.setMimeType("audio/wav");
        file.setBucketName(minioProperties.getBucketName());
        file.setObjectKey(objectKey);
        file.setSizeBytes(result.size());
        file.setSha256(result.sha256());
        file.setDurationMs(metadata.getDurationMs());
        file.setSampleRate(metadata.getSampleRate());
        file.setChannels(metadata.getChannels());
        file.setBitRate(toInteger(metadata.getBitRate()));
        file.setFileStatus(FileStatus.AVAILABLE);
        file.setCreatedAt(now);
        file.setUpdatedAt(now);
        file.setDeleted(0);
        if (audioFileMapper.insert(file) != 1
                || executionMapper.complete(execution.getId(), file.getId(),
                now) != 1) {
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_FAILED,
                    true, "Processed result metadata could not be saved");
        }
        return true;
    }

    private String resultObjectKey(AudioProcessingExecution execution) {
        return "repair/" + execution.getUserId() + "/"
                + execution.getId() + "/result.wav";
    }

    private String resultName(String originalName, Long executionId) {
        String safe = originalName == null ? "audio" : originalName
                .replace('\\', '/')
                .substring(originalName.replace('\\', '/')
                        .lastIndexOf('/') + 1)
                .replaceAll("(?i)\\.[a-z0-9]{1,8}$", "")
                .replaceAll("[^\\p{L}\\p{N}._-]", "_");
        if (safe.isBlank()) {
            safe = "audio";
        }
        if (safe.length() > 80) {
            safe = safe.substring(0, 80);
        }
        String id = executionId.toString();
        String shortId = id.substring(Math.max(0, id.length() - 8));
        return safe + "-repaired-" + shortId + ".wav";
    }

    private String safeDiagnosticFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "unknown";
        }
        String normalized = originalName.replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\r\\n\\t]", "_");
        return fileName.length() > 120
                ? fileName.substring(0, 120) : fileName;
    }

    private String safeDiagnosticExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return null;
        }
        String safe = extension.replaceAll("[^A-Za-z0-9]", "");
        return safe.length() > 20 ? safe.substring(0, 20) : safe;
    }

    private Integer toInteger(Long value) {
        if (value == null) {
            return null;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE
                : value.intValue();
    }

    private void compensateDelete(String objectKey) {
        try {
            storageService.delete(objectKey);
        } catch (RuntimeException e) {
            log.error("Processing output compensation failed, objectKey={}",
                    objectKey, e);
        }
    }

    private record ResultFileData(long size, String sha256) {
    }
}
