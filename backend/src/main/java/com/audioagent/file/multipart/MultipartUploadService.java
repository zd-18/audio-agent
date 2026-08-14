package com.audioagent.file.multipart;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.enums.FileRole;
import com.audioagent.common.enums.FileStatus;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.file.multipart.dto.MultipartUploadInitRequest;
import com.audioagent.file.multipart.vo.MultipartChunkVO;
import com.audioagent.file.multipart.vo.MultipartUploadCompleteVO;
import com.audioagent.file.multipart.vo.MultipartUploadInitVO;
import com.audioagent.file.multipart.vo.MultipartUploadProgressVO;
import com.audioagent.file.vo.AudioFileVO;
import com.audioagent.infrastructure.ffprobe.AudioMetadata;
import com.audioagent.infrastructure.ffprobe.AudioMetadataService;
import com.audioagent.infrastructure.minio.MinioProperties;
import com.audioagent.infrastructure.minio.MinioStorageService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultipartUploadService {

    public static final long DEFAULT_CHUNK_SIZE = 8L * 1024 * 1024;
    private static final long MIN_CHUNK_SIZE = 5L * 1024 * 1024;
    private static final long MAX_CHUNK_SIZE = 10L * 1024 * 1024;
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024 * 1024;
    private static final int MAX_CHUNK_COUNT = 10_000;
    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of("mp3", "wav", "m4a", "mp4");
    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
            "audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav",
            "audio/wave", "audio/vnd.wave", "audio/mp4", "audio/x-m4a",
            "audio/m4a", "video/mp4", "application/mp4",
            "application/octet-stream"
    );

    private final MultipartUploadStateStore stateStore;
    private final AudioFileMapper audioFileMapper;
    private final MinioStorageService minioStorageService;
    private final MinioProperties minioProperties;
    private final AudioMetadataService audioMetadataService;
    private final MultipartUploadCompletionPersistenceService
            completionPersistenceService;

    @Transactional(readOnly = true)
    public MultipartUploadInitVO initialize(Long userId,
                                             MultipartUploadInitRequest request) {
        validateUserId(userId);
        ValidatedInit init = validateInitRequest(request);

        AudioFile duplicate = findDuplicate(userId, init.sha256());
        if (duplicate != null) {
            return MultipartUploadInitVO.builder()
                    .status(MultipartUploadStatus.COMPLETED.name())
                    .instantUpload(true)
                    .chunkSize(init.chunkSize())
                    .totalChunks(init.totalChunks())
                    .uploadedChunks(List.of())
                    .audioFile(AudioFileVO.from(duplicate))
                    .build();
        }

        String uploadId = generateUploadId(userId, init);
        var existing = stateStore.find(uploadId);
        if (existing.isPresent()) {
            MultipartUploadState state = existing.get();
            requireOwner(state, userId);
            ensureSameUpload(state, init);
            return initResponse(state, false, null);
        }

        long now = System.currentTimeMillis();
        MultipartUploadState state = MultipartUploadState.builder()
                .uploadId(uploadId)
                .userId(userId)
                .originalName(init.originalName())
                .extension(init.extension())
                .mimeType(init.mimeType())
                .sizeBytes(init.sizeBytes())
                .sha256(init.sha256())
                .chunkSize(init.chunkSize())
                .totalChunks(init.totalChunks())
                .finalObjectKey(generateFinalObjectKey(userId,
                        init.extension()))
                .status(MultipartUploadStatus.INIT)
                .createdAt(now)
                .updatedAt(now)
                .build();
        stateStore.create(state);
        return initResponse(state, false, null);
    }

    public MultipartUploadProgressVO progress(Long userId, String uploadId) {
        MultipartUploadState state = requireOwnedState(userId, uploadId);
        List<Integer> uploaded = stateStore.uploadedChunks(uploadId);
        return MultipartUploadProgressVO.builder()
                .uploadId(uploadId)
                .status(state.getStatus().name())
                .sizeBytes(state.getSizeBytes())
                .chunkSize(state.getChunkSize())
                .totalChunks(state.getTotalChunks())
                .uploadedCount(uploaded.size())
                .uploadedChunks(uploaded)
                .build();
    }

    public MultipartChunkVO uploadChunk(Long userId, String uploadId,
                                         int chunkIndex,
                                         long declaredChunkSize,
                                         MultipartFile chunk) {
        MultipartUploadState state = requireOwnedState(userId, uploadId);
        validateChunk(state, chunkIndex, declaredChunkSize, chunk);

        if (state.getStatus() == MultipartUploadStatus.COMPLETED) {
            return chunkResponse(state, chunkIndex);
        }
        if (state.getStatus() == MultipartUploadStatus.MERGING) {
            throw new BusinessException(ErrorCode.MULTIPART_UPLOAD_BUSY);
        }

        String objectKey = chunkObjectKey(state, chunkIndex);
        if (stateStore.isChunkUploaded(uploadId, chunkIndex)
                && minioStorageService.exists(objectKey)) {
            return chunkResponse(state, chunkIndex);
        }

        try (InputStream inputStream = chunk.getInputStream()) {
            minioStorageService.upload(objectKey, inputStream,
                    chunk.getSize(), "application/octet-stream");
        } catch (BusinessException e) {
            safeUpdateFailed(state);
            throw e;
        } catch (Exception e) {
            log.error("Chunk upload failed, uploadId={}, chunkIndex={}",
                    uploadId, chunkIndex, e);
            throw new BusinessException(ErrorCode.AUDIO_FILE_UPLOAD_FAILED,
                    "分片上传失败，请重试");
        }

        stateStore.markChunkUploaded(state, chunkIndex);
        return chunkResponse(state, chunkIndex);
    }

    public MultipartUploadCompleteVO complete(Long userId, String uploadId) {
        MultipartUploadState state = requireOwnedState(userId, uploadId);
        AudioFile duplicate = findDuplicate(userId, state.getSha256());
        if (duplicate != null) {
            completionPersistenceService.ensureUploadedEvent(duplicate);
            markCompletedBestEffort(state, duplicate.getId());
            cleanupCompletedTemporaryObjects(state);
            return completeResponse(state, duplicate, true);
        }

        if (state.getStatus() == MultipartUploadStatus.COMPLETED) {
            AudioFile completedFile = loadCompletedFile(state, userId);
            completionPersistenceService.ensureUploadedEvent(completedFile);
            return completeResponse(state, completedFile, false);
        }

        List<Integer> uploaded = stateStore.uploadedChunks(uploadId);
        if (uploaded.size() != state.getTotalChunks()
                || !containsEveryChunk(uploaded, state.getTotalChunks())) {
            throw new BusinessException(
                    ErrorCode.MULTIPART_CHUNKS_INCOMPLETE,
                    "仍有分片未上传完成"
            );
        }

        String lockKey = "hash:" + userId + ":" + state.getSha256();
        String lockToken = UUID.randomUUID().toString();
        if (!stateStore.acquireMergeLock(lockKey, lockToken)) {
            throw new BusinessException(ErrorCode.MULTIPART_UPLOAD_BUSY);
        }

        Path localMergedFile = null;
        boolean published = false;
        try {
            state = requireOwnedState(userId, uploadId);
            duplicate = findDuplicate(userId, state.getSha256());
            if (duplicate != null) {
                completionPersistenceService.ensureUploadedEvent(duplicate);
                markCompletedBestEffort(state, duplicate.getId());
                cleanupCompletedTemporaryObjects(state);
                return completeResponse(state, duplicate, true);
            }

            stateStore.updateStatus(state, MultipartUploadStatus.MERGING);
            String mergedObjectKey = mergedObjectKey(state);
            MultipartUploadState mergingState = state;
            List<String> sourceKeys = IntStream.range(0,
                            state.getTotalChunks())
                    .mapToObj(index -> chunkObjectKey(mergingState, index))
                    .toList();
            minioStorageService.compose(mergedObjectKey, sourceKeys,
                    state.getMimeType());

            localMergedFile = Files.createTempFile(
                    "audio-multipart-", "." + state.getExtension());
            String serverSha256 = downloadAndHash(mergedObjectKey,
                    localMergedFile);
            long mergedSize = Files.size(localMergedFile);
            if (mergedSize != state.getSizeBytes()) {
                throw hashMismatch(state, "合并后的文件大小不一致");
            }
            if (!MessageDigest.isEqual(
                    state.getSha256().getBytes(StandardCharsets.US_ASCII),
                    serverSha256.getBytes(StandardCharsets.US_ASCII))) {
                throw hashMismatch(state, "文件完整性校验失败，请重新选择文件上传");
            }

            AudioMetadata metadata = audioMetadataService.extractMetadata(
                    localMergedFile);
            duplicate = findDuplicate(userId, state.getSha256());
            if (duplicate != null) {
                completionPersistenceService.ensureUploadedEvent(duplicate);
                markCompletedBestEffort(state, duplicate.getId());
                cleanupCompletedTemporaryObjects(state);
                return completeResponse(state, duplicate, true);
            }

            if (minioStorageService.exists(state.getFinalObjectKey())) {
                throw new BusinessException(
                        ErrorCode.MULTIPART_UPLOAD_STATE_INVALID,
                        "目标文件标识冲突，请重新初始化上传"
                );
            }
            minioStorageService.copy(mergedObjectKey,
                    state.getFinalObjectKey());
            published = true;

            AudioFile audioFile = completionPersistenceService
                    .persistCompletedUpload(buildAudioFile(state, metadata));
            if (audioFile.getId() == null) {
                throw new BusinessException(
                        ErrorCode.AUDIO_FILE_UPLOAD_FAILED,
                        "文件元数据保存失败"
                );
            }

            markCompletedBestEffort(state, audioFile.getId());
            cleanupCompletedTemporaryObjects(state);
            log.info("Multipart upload completed, uploadId={}, fileId={}, userId={}",
                    uploadId, audioFile.getId(), userId);
            return completeResponse(state, audioFile, false);
        } catch (BusinessException e) {
            if (published) {
                compensateDelete(state.getFinalObjectKey());
            }
            if (state.getStatus() != MultipartUploadStatus.COMPLETED) {
                safeUpdateFailed(state);
            }
            throw e;
        } catch (Exception e) {
            if (published) {
                compensateDelete(state.getFinalObjectKey());
            }
            safeUpdateFailed(state);
            log.error("Multipart merge failed, uploadId={}, userId={}",
                    uploadId, userId, e);
            throw new BusinessException(ErrorCode.AUDIO_FILE_UPLOAD_FAILED,
                    "文件合并失败，请重试");
        } finally {
            stateStore.releaseMergeLock(lockKey, lockToken);
            if (localMergedFile != null) {
                try {
                    Files.deleteIfExists(localMergedFile);
                } catch (Exception e) {
                    log.warn("Failed to delete local multipart temp file: {}",
                            localMergedFile, e);
                }
            }
        }
    }

    public void cleanupExpiredUploads() {
        for (var expired : stateStore.expiredUploads(
                System.currentTimeMillis())) {
            var existing = stateStore.find(expired.uploadId());
            if (existing.isPresent()
                    && existing.get().getStatus()
                    != MultipartUploadStatus.COMPLETED) {
                continue;
            }
            List<String> keys = temporaryObjectKeys(expired.userId(),
                    expired.uploadId(), expired.totalChunks());
            try {
                minioStorageService.deleteAll(keys);
                stateStore.removeCleanupMember(expired.member());
                log.info("Expired multipart upload cleaned, uploadId={}, userId={}",
                        expired.uploadId(), expired.userId());
            } catch (RuntimeException e) {
                log.warn("Expired multipart upload cleanup will retry, uploadId={}",
                        expired.uploadId(), e);
            }
        }
    }

    private MultipartUploadState requireOwnedState(Long userId,
                                                    String uploadId) {
        validateUserId(userId);
        validateUploadId(uploadId);
        MultipartUploadState state = stateStore.find(uploadId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MULTIPART_UPLOAD_NOT_FOUND));
        requireOwner(state, userId);
        return state;
    }

    private void requireOwner(MultipartUploadState state, Long userId) {
        if (!Objects.equals(state.getUserId(), userId)) {
            throw new BusinessException(
                    ErrorCode.MULTIPART_UPLOAD_ACCESS_DENIED);
        }
    }

    private ValidatedInit validateInitRequest(
            MultipartUploadInitRequest request) {
        if (request == null) {
            throw invalid("初始化参数不能为空");
        }
        String originalName = cleanFileName(request.getOriginalName());
        String extension = extractExtension(originalName);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(
                    ErrorCode.AUDIO_FILE_FORMAT_UNSUPPORTED,
                    "仅支持 MP3、WAV、M4A 和 MP4 文件"
            );
        }
        long sizeBytes = requirePositive(request.getSizeBytes(),
                "文件大小不正确");
        if (sizeBytes > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.AUDIO_FILE_TOO_LARGE,
                    "文件大小不能超过 20GB");
        }
        String sha256 = request.getSha256() == null ? ""
                : request.getSha256().trim().toLowerCase(Locale.ROOT);
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw invalid("SHA-256 格式不正确");
        }
        long chunkSize = requirePositive(request.getChunkSize(),
                "分片大小不正确");
        if (chunkSize < MIN_CHUNK_SIZE || chunkSize > MAX_CHUNK_SIZE) {
            throw invalid("分片大小必须在 5MB 到 10MB 之间");
        }
        int totalChunks = request.getTotalChunks() == null
                ? 0 : request.getTotalChunks();
        long expectedChunks = (sizeBytes + chunkSize - 1) / chunkSize;
        if (totalChunks <= 0 || totalChunks > MAX_CHUNK_COUNT
                || totalChunks != expectedChunks) {
            throw invalid("分片总数与文件大小不匹配");
        }
        String mimeType = normalizeMimeType(request.getMimeType());
        if (!SUPPORTED_MIME_TYPES.contains(mimeType)) {
            throw new BusinessException(
                    ErrorCode.AUDIO_FILE_FORMAT_UNSUPPORTED,
                    "文件类型不受支持"
            );
        }
        if (!isMimeCompatible(extension, mimeType)) {
            throw new BusinessException(
                    ErrorCode.AUDIO_FILE_FORMAT_UNSUPPORTED,
                    "文件扩展名与类型不匹配"
            );
        }
        return new ValidatedInit(originalName, extension, mimeType,
                sizeBytes, sha256, chunkSize, totalChunks);
    }

    private void validateChunk(MultipartUploadState state, int chunkIndex,
                               long declaredChunkSize, MultipartFile chunk) {
        if (chunkIndex < 0 || chunkIndex >= state.getTotalChunks()) {
            throw new BusinessException(ErrorCode.MULTIPART_CHUNK_INVALID,
                    "分片序号超出范围");
        }
        if (chunk == null || chunk.isEmpty()) {
            throw new BusinessException(ErrorCode.MULTIPART_CHUNK_INVALID,
                    "分片内容不能为空");
        }
        long expectedSize = chunkIndex == state.getTotalChunks() - 1
                ? state.getSizeBytes()
                - state.getChunkSize() * (state.getTotalChunks() - 1L)
                : state.getChunkSize();
        if (expectedSize <= 0 || declaredChunkSize != expectedSize
                || chunk.getSize() != declaredChunkSize) {
            throw new BusinessException(ErrorCode.MULTIPART_CHUNK_INVALID,
                    "分片大小与初始化信息不一致");
        }
    }

    private AudioFile buildAudioFile(MultipartUploadState state,
                                     AudioMetadata metadata) {
        LocalDateTime now = LocalDateTime.now();
        AudioFile audioFile = new AudioFile();
        audioFile.setId(IdWorker.getId());
        audioFile.setUserId(state.getUserId());
        audioFile.setSourceFileId(null);
        audioFile.setRootAudioFileId(audioFile.getId());
        audioFile.setVersionNo(0);
        audioFile.setVersionSummary("原始版本");
        audioFile.setSourceExecutionId(null);
        audioFile.setFileRole(FileRole.ORIGINAL);
        audioFile.setOriginalName(state.getOriginalName());
        audioFile.setExtension(state.getExtension());
        audioFile.setMimeType(state.getMimeType());
        audioFile.setBucketName(minioProperties.getBucketName());
        audioFile.setObjectKey(state.getFinalObjectKey());
        audioFile.setSizeBytes(state.getSizeBytes());
        audioFile.setSha256(state.getSha256());
        audioFile.setDurationMs(metadata.getDurationMs());
        audioFile.setFileStatus(FileStatus.AVAILABLE);
        audioFile.setCreatedAt(now);
        audioFile.setUpdatedAt(now);
        audioFile.setDeleted(0);
        return audioFile;
    }

    private String downloadAndHash(String objectKey, Path target)
            throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream inputStream = minioStorageService.getObject(objectKey);
             DigestInputStream digestInputStream =
                     new DigestInputStream(inputStream, digest)) {
            Files.copy(digestInputStream, target,
                    StandardCopyOption.REPLACE_EXISTING);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private AudioFile findDuplicate(Long userId, String sha256) {
        AudioFile audioFile = audioFileMapper.selectFirstAvailableByHash(userId, sha256,
                FileStatus.AVAILABLE.getCode());
        if (audioFile == null || !StringUtils.hasText(audioFile.getBucketName())
                || !StringUtils.hasText(audioFile.getObjectKey())) {
            return null;
        }
        return minioStorageService.exists(audioFile.getBucketName(),
                audioFile.getObjectKey()) ? audioFile : null;
    }

    private AudioFile loadCompletedFile(MultipartUploadState state,
                                        Long userId) {
        if (state.getAudioFileId() == null) {
            throw new BusinessException(
                    ErrorCode.MULTIPART_UPLOAD_STATE_INVALID);
        }
        AudioFile audioFile = audioFileMapper.selectById(
                state.getAudioFileId());
        if (audioFile == null || !Objects.equals(audioFile.getUserId(), userId)
                || audioFile.getFileStatus() != FileStatus.AVAILABLE
                || Integer.valueOf(1).equals(audioFile.getDeleted())) {
            throw new BusinessException(
                    ErrorCode.MULTIPART_UPLOAD_STATE_INVALID);
        }
        return audioFile;
    }

    private MultipartUploadInitVO initResponse(MultipartUploadState state,
                                                boolean instant,
                                                AudioFile audioFile) {
        return MultipartUploadInitVO.builder()
                .uploadId(state.getUploadId())
                .status(state.getStatus().name())
                .instantUpload(instant)
                .chunkSize(state.getChunkSize())
                .totalChunks(state.getTotalChunks())
                .uploadedChunks(stateStore.uploadedChunks(state.getUploadId()))
                .audioFile(audioFile == null ? null : AudioFileVO.from(audioFile))
                .build();
    }

    private MultipartChunkVO chunkResponse(MultipartUploadState state,
                                            int chunkIndex) {
        int uploadedCount = stateStore.uploadedChunks(
                state.getUploadId()).size();
        return MultipartChunkVO.builder()
                .uploadId(state.getUploadId())
                .status(state.getStatus().name())
                .chunkIndex(chunkIndex)
                .uploadedCount(uploadedCount)
                .totalChunks(state.getTotalChunks())
                .build();
    }

    private MultipartUploadCompleteVO completeResponse(
            MultipartUploadState state, AudioFile audioFile,
            boolean instantUpload) {
        return MultipartUploadCompleteVO.builder()
                .uploadId(state.getUploadId())
                .status(MultipartUploadStatus.COMPLETED.name())
                .instantUpload(instantUpload)
                .audioFile(AudioFileVO.from(audioFile))
                .build();
    }

    private boolean cleanupTemporaryObjects(MultipartUploadState state) {
        try {
            minioStorageService.deleteAll(temporaryObjectKeys(
                    state.getUserId(), state.getUploadId(),
                    state.getTotalChunks()));
            return true;
        } catch (RuntimeException e) {
            log.warn("Temporary multipart objects will be cleaned later, uploadId={}",
                    state.getUploadId(), e);
            return false;
        }
    }

    private void cleanupCompletedTemporaryObjects(
            MultipartUploadState state) {
        if (!cleanupTemporaryObjects(state)) {
            try {
                stateStore.scheduleCleanup(state, Duration.ofHours(1));
            } catch (RuntimeException e) {
                log.error("Failed to schedule multipart cleanup, uploadId={}",
                        state.getUploadId(), e);
            }
        }
    }

    private void markCompletedBestEffort(MultipartUploadState state,
                                         Long audioFileId) {
        try {
            stateStore.markCompleted(state, audioFileId);
        } catch (RuntimeException e) {
            state.setStatus(MultipartUploadStatus.COMPLETED);
            state.setAudioFileId(audioFileId);
            log.error("Database completion committed but Redis state update failed, "
                            + "uploadId={}, audioFileId={}",
                    state.getUploadId(), audioFileId, e);
        }
    }

    private List<String> temporaryObjectKeys(Long userId, String uploadId,
                                             int totalChunks) {
        List<String> keys = new ArrayList<>(totalChunks + 1);
        for (int index = 0; index < totalChunks; index++) {
            keys.add(temporaryPrefix(userId, uploadId) + "/chunks/" + index);
        }
        keys.add(temporaryPrefix(userId, uploadId) + "/merged");
        return keys;
    }

    private String chunkObjectKey(MultipartUploadState state, int index) {
        return temporaryPrefix(state.getUserId(), state.getUploadId())
                + "/chunks/" + index;
    }

    private String mergedObjectKey(MultipartUploadState state) {
        return temporaryPrefix(state.getUserId(), state.getUploadId())
                + "/merged";
    }

    private String temporaryPrefix(Long userId, String uploadId) {
        return "temporary/chunk-uploads/" + userId + "/" + uploadId;
    }

    private String generateFinalObjectKey(Long userId, String extension) {
        String datePath = LocalDate.now().format(
                DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String objectName = UUID.randomUUID().toString().replace("-", "");
        return "original/" + userId + "/" + datePath + "/"
                + objectName + "." + extension;
    }

    private String generateUploadId(Long userId, ValidatedInit init) {
        String identity = userId + ":" + init.sha256() + ":"
                + init.sizeBytes() + ":" + init.originalName() + ":"
                + init.mimeType() + ":" + init.chunkSize() + ":"
                + init.totalChunks();
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
    }

    private void ensureSameUpload(MultipartUploadState state,
                                  ValidatedInit init) {
        if (!state.getOriginalName().equals(init.originalName())
                || !state.getMimeType().equals(init.mimeType())
                || !state.getSizeBytes().equals(init.sizeBytes())
                || !state.getSha256().equals(init.sha256())
                || !state.getChunkSize().equals(init.chunkSize())
                || !state.getTotalChunks().equals(init.totalChunks())) {
            throw new BusinessException(
                    ErrorCode.MULTIPART_UPLOAD_STATE_INVALID,
                    "同一上传任务的文件信息不一致"
            );
        }
    }

    private boolean containsEveryChunk(List<Integer> uploaded,
                                       int totalChunks) {
        for (int index = 0; index < totalChunks; index++) {
            if (uploaded.get(index) != index) {
                return false;
            }
        }
        return true;
    }

    private BusinessException hashMismatch(MultipartUploadState state,
                                           String message) {
        cleanupTemporaryObjects(state);
        stateStore.clearUploadedChunks(state.getUploadId());
        stateStore.updateStatus(state, MultipartUploadStatus.FAILED);
        return new BusinessException(ErrorCode.MULTIPART_HASH_MISMATCH,
                message);
    }

    private void safeUpdateFailed(MultipartUploadState state) {
        try {
            stateStore.updateStatus(state, MultipartUploadStatus.FAILED);
        } catch (RuntimeException stateError) {
            log.error("Failed to persist multipart failure state, uploadId={}",
                    state.getUploadId(), stateError);
        }
    }

    private void compensateDelete(String objectKey) {
        try {
            minioStorageService.delete(objectKey);
        } catch (RuntimeException deleteError) {
            log.error("Failed to compensate published multipart object: {}",
                    objectKey, deleteError);
        }
    }

    private String cleanFileName(String value) {
        if (!StringUtils.hasText(value) || value.length() > 255) {
            throw invalid("文件名不正确");
        }
        String cleaned = StringUtils.cleanPath(value.trim());
        if (cleaned.contains("..") || cleaned.contains("/")
                || cleaned.contains("\\")) {
            throw invalid("文件名不合法");
        }
        return cleaned;
    }

    private String extractExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 1 || dot == fileName.length() - 1) {
            throw new BusinessException(
                    ErrorCode.AUDIO_FILE_FORMAT_UNSUPPORTED,
                    "文件缺少有效扩展名"
            );
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeMimeType(String value) {
        if (!StringUtils.hasText(value)) {
            return "application/octet-stream";
        }
        return value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private boolean isMimeCompatible(String extension, String mimeType) {
        if ("application/octet-stream".equals(mimeType)) {
            return true;
        }
        return switch (extension) {
            case "mp3" -> Set.of("audio/mpeg", "audio/mp3")
                    .contains(mimeType);
            case "wav" -> Set.of("audio/wav", "audio/x-wav", "audio/wave",
                            "audio/vnd.wave")
                    .contains(mimeType);
            case "m4a" -> Set.of("audio/mp4", "audio/x-m4a", "audio/m4a",
                            "application/mp4")
                    .contains(mimeType);
            case "mp4" -> Set.of("audio/mp4", "video/mp4", "application/mp4")
                    .contains(mimeType);
            default -> false;
        };
    }

    private long requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw invalid(message);
        }
        return value;
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw invalid("用户 ID 不正确");
        }
    }

    private void validateUploadId(String uploadId) {
        if (uploadId == null || !uploadId.matches("[0-9a-f]{32}")) {
            throw invalid("uploadId 格式不正确");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.PARAM_INVALID, message);
    }

    private record ValidatedInit(String originalName, String extension,
                                 String mimeType, long sizeBytes,
                                 String sha256, long chunkSize,
                                 int totalChunks) {
    }
}
