package com.audioagent.file.service.impl;

import com.audioagent.common.api.PageResult;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.enums.FileRole;
import com.audioagent.common.enums.FileStatus;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.file.service.AudioFileService;
import com.audioagent.file.vo.AudioFileVO;
import com.audioagent.file.vo.AudioFileListVO;
import com.audioagent.file.vo.AudioPlaybackUrlVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.audioagent.infrastructure.ffprobe.AudioMetadata;
import com.audioagent.infrastructure.ffprobe.AudioMetadataService;
import com.audioagent.infrastructure.minio.MinioProperties;
import com.audioagent.infrastructure.minio.MinioStorageService;
import com.audioagent.transcription.entity.AudioTranscriptionTask;
import com.audioagent.transcription.mapper.AudioTranscriptionTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioFileServiceImpl implements AudioFileService {

    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 普通上传阶段暂时限制为500MB。
     * 后续实现分片上传后可以提高限制。
     */
    private static final long MAX_FILE_SIZE =
            500L * 1024 * 1024;

    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of("mp3", "wav", "m4a", "mp4");

    private final AudioFileMapper audioFileMapper;
    private final MinioStorageService minioStorageService;
    private final MinioProperties minioProperties;
    private final AudioMetadataService audioMetadataService;
    private final AudioTranscriptionTaskMapper transcriptionTaskMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResult<AudioFileListVO> listFiles(Long userId, int current,
                                                 int size, String keyword,
                                                 String status) {
        validateUserId(userId);
        validatePage(current, size);

        LambdaQueryWrapper<AudioFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AudioFile::getUserId, userId)
                .like(StringUtils.hasText(keyword), AudioFile::getOriginalName,
                        StringUtils.hasText(keyword) ? keyword.trim() : null)
                .eq(StringUtils.hasText(status), AudioFile::getFileStatus,
                        resolveFileStatus(status))
                .orderByDesc(AudioFile::getCreatedAt);

        Page<AudioFile> page = audioFileMapper.selectPage(
                new Page<>(current, size), wrapper);
        var records = page.getRecords().stream()
                .map(AudioFileListVO::from)
                .toList();
        applyLatestTranscriptions(userId, records);

        log.info("Audio file page queried, userId={}, current={}, size={}, keyword={}, status={}, total={}",
                userId, current, size, keyword, status, page.getTotal());
        return PageResult.of(records, page.getCurrent(), page.getSize(),
                page.getTotal());
    }

    private void applyLatestTranscriptions(
            Long userId, java.util.List<AudioFileListVO> records) {
        if (records.isEmpty()) {
            return;
        }
        var fileIds = records.stream()
                .map(AudioFileListVO::getAudioFileId)
                .toList();
        var latest = transcriptionTaskMapper.selectLatestForAudioFiles(
                userId, fileIds);
        Map<Long, AudioTranscriptionTask> tasks = (latest == null
                ? Collections.<AudioTranscriptionTask>emptyList() : latest)
                .stream()
                .collect(Collectors.toMap(
                        AudioTranscriptionTask::getAudioFileId,
                        Function.identity(),
                        (left, right) -> left));
        records.forEach(record -> record.applyTranscription(
                tasks.get(record.getAudioFileId())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AudioFileVO upload(Long userId, MultipartFile file) {
        validateUserId(userId);
        validateFile(file);

        String originalName = cleanFileName(file.getOriginalFilename());
        String extension = extractExtension(originalName);
        String objectKey = generateObjectKey(extension);

        Path tempFile = null;
        boolean uploadedToMinio = false;

        try {
            /*
             * 创建带原始扩展名的本地临时文件，供FFprobe分析。
             * 临时文件名不使用原始文件名，避免非法字符问题。
             */
            tempFile = Files.createTempFile("audio-upload-", "." + extension);
            file.transferTo(tempFile.toFile());

            /*
             * 对临时文件执行FFprobe元数据提取。
             * 提取失败时durationMs为null，不中断上传流程。
             */
            AudioMetadata metadata =
                    audioMetadataService.extractMetadata(tempFile);

            MessageDigest messageDigest =
                    MessageDigest.getInstance("SHA-256");

            String sha256;

            /*
             * 从临时文件读取流，通过DigestInputStream在上传MinIO的同时计算SHA-256，
             * 避免为了计算摘要再次完整读取文件。
             */
            try (
                    InputStream inputStream =
                            Files.newInputStream(tempFile);
                    DigestInputStream digestInputStream =
                            new DigestInputStream(
                                    inputStream,
                                    messageDigest
                            )
            ) {
                minioStorageService.upload(
                        objectKey,
                        digestInputStream,
                        file.getSize(),
                        file.getContentType()
                );

                uploadedToMinio = true;
                sha256 = HexFormat.of()
                        .formatHex(messageDigest.digest());
            }

            LocalDateTime now = LocalDateTime.now();

            AudioFile audioFile = new AudioFile();
            audioFile.setId(IdWorker.getId());
            audioFile.setUserId(userId);
            audioFile.setSourceFileId(null);
            audioFile.setRootAudioFileId(audioFile.getId());
            audioFile.setVersionNo(0);
            audioFile.setVersionSummary("原始版本");
            audioFile.setSourceExecutionId(null);
            audioFile.setFileRole(FileRole.ORIGINAL);
            audioFile.setOriginalName(originalName);
            audioFile.setExtension(extension);
            audioFile.setMimeType(resolveContentType(file));
            audioFile.setBucketName(minioProperties.getBucketName());
            audioFile.setObjectKey(objectKey);
            audioFile.setSizeBytes(file.getSize());
            audioFile.setSha256(sha256);
            audioFile.setFileStatus(FileStatus.AVAILABLE);
            audioFile.setDurationMs(metadata.getDurationMs());
            audioFile.setCreatedAt(now);
            audioFile.setUpdatedAt(now);
            audioFile.setDeleted(0);

            int insertedRows = audioFileMapper.insert(audioFile);

            if (insertedRows != 1 || audioFile.getId() == null) {
                throw new BusinessException(
                        ErrorCode.AUDIO_FILE_UPLOAD_FAILED,
                        "文件元数据保存失败"
                );
            }

            log.info(
                    "Audio file uploaded successfully, fileId={}, userId={}, objectKey={}, durationMs={}",
                    audioFile.getId(),
                    userId,
                    objectKey,
                    metadata.getDurationMs()
            );

            return AudioFileVO.from(audioFile);

        } catch (BusinessException e) {
            if (uploadedToMinio) {
                compensateDelete(objectKey);
            }
            throw e;

        } catch (Exception e) {
            if (uploadedToMinio) {
                compensateDelete(objectKey);
            }

            log.error(
                    "Audio file upload failed, userId={}, fileName={}",
                    userId,
                    originalName,
                    e
            );

            throw new BusinessException(
                    ErrorCode.AUDIO_FILE_UPLOAD_FAILED,
                    "文件上传失败"
            );
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn(
                            "Failed to delete temp file: {}",
                            tempFile,
                            e
                    );
                }
            }
        }
    }

@Override
    @Transactional(readOnly = true)
    public AudioFileVO getFileDetail(Long userId, Long fileId) {
        validateUserId(userId);

        if (fileId == null || fileId <= 0) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    "文件ID不能为空"
            );
        }

        AudioFile audioFile =
                audioFileMapper.selectById(fileId);

        if (audioFile == null
                || !userId.equals(audioFile.getUserId())
                || Integer.valueOf(1).equals(audioFile.getDeleted())) {

            throw new BusinessException(
                    ErrorCode.AUDIO_FILE_NOT_FOUND,
                    "资源不存在或不可访问"
            );
        }

        return AudioFileVO.from(audioFile);
    }



    @Override
    @Transactional(readOnly = true)
    public AudioFile getFileForDownload(Long userId, Long fileId) {
        validateUserId(userId);

        if (fileId == null || fileId <= 0) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    "文件ID不能为空"
            );
        }

        AudioFile audioFile =
                audioFileMapper.selectById(fileId);

        if (audioFile == null
                || !userId.equals(audioFile.getUserId())
                || Integer.valueOf(1).equals(audioFile.getDeleted())) {

            throw new BusinessException(
                    ErrorCode.AUDIO_FILE_NOT_FOUND,
                    "资源不存在或不可访问"
            );
        }

        if (audioFile.getFileStatus() != FileStatus.AVAILABLE) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    "文件当前不可下载"
            );
        }

        return audioFile;
    }

    @Override
    @Transactional(readOnly = true)
    public AudioPlaybackUrlVO getPlaybackUrl(Long userId, Long fileId) {
        long startedAt = System.nanoTime();
        validateUserId(userId);

        if (fileId == null || fileId <= 0) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    "文件ID不能为空"
            );
        }

        AudioFile audioFile = audioFileMapper.selectById(fileId);
        if (audioFile == null) {
            throw new BusinessException(
                    ErrorCode.AUDIO_FILE_NOT_FOUND,
                    "音频文件不存在"
            );
        }
        if (!userId.equals(audioFile.getUserId())) {
            throw new BusinessException(
                    ErrorCode.AUDIO_FILE_ACCESS_DENIED,
                    "无权访问该音频文件"
            );
        }
        if (Integer.valueOf(1).equals(audioFile.getDeleted())
                || audioFile.getFileStatus() != FileStatus.AVAILABLE
                || !StringUtils.hasText(audioFile.getBucketName())
                || !StringUtils.hasText(audioFile.getObjectKey())
                || !minioProperties.isPresignedUrlEnabled()) {
            throw new BusinessException(
                    ErrorCode.AUDIO_FILE_NOT_AVAILABLE,
                    "当前音频暂时无法播放"
            );
        }

        boolean objectExists;
        try {
            objectExists = minioStorageService.exists(
                    audioFile.getBucketName(),
                    audioFile.getObjectKey()
            );
        } catch (RuntimeException e) {
            throw playbackUrlGenerationFailed(userId, fileId, e);
        }
        if (!objectExists) {
            throw new BusinessException(
                    ErrorCode.AUDIO_FILE_NOT_AVAILABLE,
                    "当前音频文件对象不存在或不可用"
            );
        }

        int expirySeconds = minioProperties.getPresignedUrlExpireSeconds();
        String playbackUrl;
        try {
            playbackUrl = minioStorageService.generatePresignedUrl(
                    audioFile.getBucketName(),
                    audioFile.getObjectKey(),
                    expirySeconds
            );
        } catch (RuntimeException e) {
            throw playbackUrlGenerationFailed(userId, fileId, e);
        }
        if (!StringUtils.hasText(playbackUrl)) {
            throw playbackUrlGenerationFailed(
                    userId,
                    fileId,
                    new IllegalStateException("MinIO returned an empty presigned URL")
            );
        }

        OffsetDateTime expiresAt = OffsetDateTime.now()
                .plusSeconds(expirySeconds);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info(
                "Audio playback URL generated, userId={}, fileId={}, expirySeconds={}, elapsedMs={}",
                userId,
                fileId,
                expirySeconds,
                elapsedMs
        );

        return AudioPlaybackUrlVO.builder()
                .fileId(fileId.toString())
                .fileName(audioFile.getOriginalName())
                .mimeType(audioFile.getMimeType())
                .playbackUrl(playbackUrl)
                .expiresAt(expiresAt)
                .expiresInSeconds(expirySeconds)
                .build();
    }

    private BusinessException playbackUrlGenerationFailed(
            Long userId,
            Long fileId,
            RuntimeException cause
    ) {
        log.error(
                "Audio playback URL generation failed, userId={}, fileId={}",
                userId,
                fileId,
                cause
        );
        return new BusinessException(
                ErrorCode.PLAYBACK_URL_GENERATION_FAILED,
                "音频加载失败，请稍后重试"
        );
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    "用户ID不能为空"
            );
        }
    }

    private void validatePage(int current, int size) {
        if (current < 1) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "current must be greater than or equal to 1");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "size must be between 1 and 100");
        }
    }

    private FileStatus resolveFileStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return FileStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "Unsupported file status: " + status);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    "上传文件不能为空"
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(
                    ErrorCode.AUDIO_FILE_TOO_LARGE,
                    "普通上传暂不支持超过500MB的文件"
            );
        }

        String originalName = cleanFileName(file.getOriginalFilename());
        String extension = extractExtension(originalName);

        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(
                    ErrorCode.AUDIO_FILE_FORMAT_UNSUPPORTED,
                    "当前只支持MP3、WAV、M4A和MP4文件"
            );
        }
    }

    private String cleanFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    "文件名不能为空"
            );
        }

        String cleanedName = StringUtils.cleanPath(fileName);

        if (cleanedName.contains("..")) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    "文件名不合法"
            );
        }

        return cleanedName;
    }

    private String extractExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');

        if (lastDotIndex < 0 || lastDotIndex == fileName.length() - 1) {
            throw new BusinessException(
                    ErrorCode.AUDIO_FILE_FORMAT_UNSUPPORTED,
                    "文件缺少有效扩展名"
            );
        }

        return fileName.substring(lastDotIndex + 1)
                .toLowerCase(Locale.ROOT);
    }

    private String generateObjectKey(String extension) {
        String datePath = LocalDate.now().format(
                DateTimeFormatter.ofPattern("yyyy/MM/dd")
        );

        String objectName = UUID.randomUUID()
                .toString()
                .replace("-", "");

        return String.format(
                "original/%s/%s.%s",
                datePath,
                objectName,
                extension
        );
    }

    private String resolveContentType(MultipartFile file) {
        return StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : "application/octet-stream";
    }

    /**
     * 数据库写入失败时，删除已经上传到MinIO的对象。
     *
     * Spring事务只能回滚MySQL，不能自动回滚MinIO，
     * 因此需要手动执行补偿删除。
     */
    private void compensateDelete(String objectKey) {
        try {
            minioStorageService.delete(objectKey);
        } catch (Exception deleteException) {
            log.error(
                    "Failed to compensate MinIO object, objectKey={}",
                    objectKey,
                    deleteException
            );
        }
    }
}
