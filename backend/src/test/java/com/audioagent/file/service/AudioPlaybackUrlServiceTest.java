package com.audioagent.file.service;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.enums.FileStatus;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.file.service.impl.AudioFileServiceImpl;
import com.audioagent.file.vo.AudioPlaybackUrlVO;
import com.audioagent.infrastructure.ffprobe.AudioMetadataService;
import com.audioagent.infrastructure.minio.MinioProperties;
import com.audioagent.infrastructure.minio.MinioStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudioPlaybackUrlServiceTest {

    private static final long USER_ID = 7L;
    private static final long FILE_ID = 2078123456789012345L;
    private static final String BUCKET = "audio-agent";
    private static final String OBJECT_KEY = "original/2026/07/17/interview.wav";

    @Mock
    private AudioFileMapper audioFileMapper;
    @Mock
    private MinioStorageService minioStorageService;
    @Mock
    private MinioProperties minioProperties;
    @Mock
    private AudioMetadataService audioMetadataService;
    @InjectMocks
    private AudioFileServiceImpl service;

    @Test
    void generatesPlaybackUrlForOwnedAvailableFile() {
        AudioFile file = availableFile();
        when(audioFileMapper.selectById(FILE_ID)).thenReturn(file);
        when(minioProperties.isPresignedUrlEnabled()).thenReturn(true);
        when(minioProperties.getPresignedUrlExpireSeconds()).thenReturn(600);
        when(minioStorageService.exists(BUCKET, OBJECT_KEY)).thenReturn(true);
        when(minioStorageService.generatePresignedUrl(BUCKET, OBJECT_KEY, 600))
                .thenReturn("http://localhost:9000/audio-agent/object?X-Amz-Signature=test");

        AudioPlaybackUrlVO result = service.getPlaybackUrl(USER_ID, FILE_ID);

        assertEquals(Long.toString(FILE_ID), result.getFileId());
        assertEquals("interview.wav", result.getFileName());
        assertEquals("audio/wav", result.getMimeType());
        assertEquals(600, result.getExpiresInSeconds());
        assertTrue(result.getExpiresAt().isAfter(java.time.OffsetDateTime.now()));
        verify(minioStorageService).exists(BUCKET, OBJECT_KEY);
        verify(minioStorageService).generatePresignedUrl(BUCKET, OBJECT_KEY, 600);
    }

    @Test
    void reportsFileNotFound() {
        when(audioFileMapper.selectById(FILE_ID)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getPlaybackUrl(USER_ID, FILE_ID));

        assertEquals(ErrorCode.AUDIO_FILE_NOT_FOUND.getCode(), exception.getCode());
        verifyNoInteractions(minioStorageService);
    }

    @Test
    void deniesPlaybackForAnotherUser() {
        AudioFile file = availableFile();
        file.setUserId(99L);
        when(audioFileMapper.selectById(FILE_ID)).thenReturn(file);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getPlaybackUrl(USER_ID, FILE_ID));

        assertEquals(ErrorCode.AUDIO_FILE_ACCESS_DENIED.getCode(), exception.getCode());
        verifyNoInteractions(minioStorageService);
    }

    @Test
    void reportsUnavailableFileStatus() {
        AudioFile file = availableFile();
        file.setFileStatus(FileStatus.PROCESSING);
        when(audioFileMapper.selectById(FILE_ID)).thenReturn(file);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getPlaybackUrl(USER_ID, FILE_ID));

        assertEquals(ErrorCode.AUDIO_FILE_NOT_AVAILABLE.getCode(), exception.getCode());
        verifyNoInteractions(minioStorageService);
    }

    @Test
    void reportsUnavailableWhenObjectKeyIsMissing() {
        AudioFile file = availableFile();
        file.setObjectKey(" ");
        when(audioFileMapper.selectById(FILE_ID)).thenReturn(file);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getPlaybackUrl(USER_ID, FILE_ID));

        assertEquals(ErrorCode.AUDIO_FILE_NOT_AVAILABLE.getCode(), exception.getCode());
        verifyNoInteractions(minioStorageService);
    }

    @Test
    void reportsUnavailableWhenMinioObjectDoesNotExist() {
        AudioFile file = availableFile();
        when(audioFileMapper.selectById(FILE_ID)).thenReturn(file);
        when(minioProperties.isPresignedUrlEnabled()).thenReturn(true);
        when(minioStorageService.exists(BUCKET, OBJECT_KEY)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getPlaybackUrl(USER_ID, FILE_ID));

        assertEquals(ErrorCode.AUDIO_FILE_NOT_AVAILABLE.getCode(), exception.getCode());
        verify(minioStorageService, never())
                .generatePresignedUrl(BUCKET, OBJECT_KEY, 600);
    }

    @Test
    void mapsMinioExceptionToPlaybackBusinessError() {
        AudioFile file = availableFile();
        when(audioFileMapper.selectById(FILE_ID)).thenReturn(file);
        when(minioProperties.isPresignedUrlEnabled()).thenReturn(true);
        when(minioStorageService.exists(BUCKET, OBJECT_KEY)).thenReturn(true);
        when(minioProperties.getPresignedUrlExpireSeconds()).thenReturn(600);
        when(minioStorageService.generatePresignedUrl(BUCKET, OBJECT_KEY, 600))
                .thenThrow(new BusinessException(ErrorCode.MINIO_PRESIGNED_URL_FAILED));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getPlaybackUrl(USER_ID, FILE_ID));

        assertEquals(ErrorCode.PLAYBACK_URL_GENERATION_FAILED.getCode(), exception.getCode());
    }

    @Test
    void serializesLongFileIdAsStringWithoutCredentials() throws Exception {
        AudioPlaybackUrlVO vo = AudioPlaybackUrlVO.builder()
                .fileId(Long.toString(FILE_ID))
                .fileName("interview.wav")
                .mimeType("audio/wav")
                .playbackUrl("http://localhost:9000/audio-agent/object?signature=test")
                .expiresAt(java.time.OffsetDateTime.now().plusMinutes(10))
                .expiresInSeconds(600)
                .build();

        String json = new ObjectMapper().findAndRegisterModules()
                .writeValueAsString(vo);
        JsonNode root = new ObjectMapper().readTree(json);

        assertTrue(root.get("fileId").isTextual());
        assertEquals(Long.toString(FILE_ID), root.get("fileId").asText());
        assertFalse(json.contains("accessKey"));
        assertFalse(json.contains("secretKey"));
    }

    private static AudioFile availableFile() {
        AudioFile file = new AudioFile();
        file.setId(FILE_ID);
        file.setUserId(USER_ID);
        file.setOriginalName("interview.wav");
        file.setMimeType("audio/wav");
        file.setBucketName(BUCKET);
        file.setObjectKey(OBJECT_KEY);
        file.setFileStatus(FileStatus.AVAILABLE);
        file.setDeleted(0);
        return file;
    }
}
