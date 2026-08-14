package com.audioagent.file.multipart;

import com.audioagent.common.enums.FileStatus;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.file.multipart.dto.MultipartUploadInitRequest;
import com.audioagent.infrastructure.ffprobe.AudioMetadata;
import com.audioagent.infrastructure.ffprobe.AudioMetadataService;
import com.audioagent.infrastructure.minio.MinioProperties;
import com.audioagent.infrastructure.minio.MinioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultipartUploadServiceTest {

    private static final long CHUNK_SIZE = 5L * 1024 * 1024;

    @Mock
    private MultipartUploadStateStore stateStore;
    @Mock
    private AudioFileMapper audioFileMapper;
    @Mock
    private MinioStorageService minioStorageService;
    @Mock
    private MinioProperties minioProperties;
    @Mock
    private AudioMetadataService audioMetadataService;
    @Mock
    private MultipartUploadCompletionPersistenceService
            completionPersistenceService;

    private MultipartUploadService service;

    @BeforeEach
    void setUp() {
        service = new MultipartUploadService(stateStore, audioFileMapper,
                minioStorageService, minioProperties, audioMetadataService,
                completionPersistenceService);
    }

    @Test
    void initializeReturnsInstantUploadForExistingOwnedHash() {
        AudioFile duplicate = availableFile(41L, 7L, hashOf(new byte[]{1}));
        when(audioFileMapper.selectFirstAvailableByHash(
                7L, duplicate.getSha256(), FileStatus.AVAILABLE.getCode()))
                .thenReturn(duplicate);
        when(minioStorageService.exists("audio-agent", "original/meeting.wav"))
                .thenReturn(true);

        var result = service.initialize(7L,
                initRequest("meeting.wav", CHUNK_SIZE,
                        duplicate.getSha256()));

        assertTrue(result.isInstantUpload());
        assertEquals(41L, result.getAudioFile().getFileId());
        verify(stateStore, never()).create(any());
    }

    @Test
    void initializeIsIdempotentAndReturnsUploadedChunks() {
        String sha = hashOf(new byte[]{2});
        MultipartUploadState state = state("abc123", 7L, sha,
                MultipartUploadStatus.UPLOADING);
        when(stateStore.find(anyString())).thenReturn(Optional.of(state));
        when(stateStore.uploadedChunks(state.getUploadId()))
                .thenReturn(List.of(0));

        var result = service.initialize(7L,
                initRequest("meeting.wav", CHUNK_SIZE, sha));

        assertFalse(result.isInstantUpload());
        assertEquals(List.of(0), result.getUploadedChunks());
        verify(stateStore, never()).create(any());
    }

    @Test
    void progressRejectsAnotherUserUploadId() {
        MultipartUploadState state = state("a".repeat(32), 7L,
                hashOf(new byte[]{3}), MultipartUploadStatus.UPLOADING);
        when(stateStore.find(state.getUploadId()))
                .thenReturn(Optional.of(state));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.progress(8L, state.getUploadId()));

        assertEquals(40009, error.getCode());
    }

    @Test
    void repeatedChunkUploadDoesNotWriteAnotherObject() {
        MultipartUploadState state = state("b".repeat(32), 7L,
                hashOf(new byte[]{4}), MultipartUploadStatus.UPLOADING);
        when(stateStore.find(state.getUploadId()))
                .thenReturn(Optional.of(state));
        when(stateStore.isChunkUploaded(state.getUploadId(), 0))
                .thenReturn(true);
        when(minioStorageService.exists(anyString())).thenReturn(true);
        when(stateStore.uploadedChunks(state.getUploadId()))
                .thenReturn(List.of(0));
        MockMultipartFile chunk = new MockMultipartFile("chunk",
                new byte[(int) CHUNK_SIZE]);

        var result = service.uploadChunk(7L, state.getUploadId(), 0,
                CHUNK_SIZE, chunk);

        assertEquals(1, result.getUploadedCount());
        verify(minioStorageService, never()).upload(
                anyString(), any(), anyLong(), anyString());
    }

    @Test
    void completeRejectsMissingChunksBeforeMerge() {
        MultipartUploadState state = state("c".repeat(32), 7L,
                hashOf(new byte[]{5}), MultipartUploadStatus.UPLOADING);
        when(stateStore.find(state.getUploadId()))
                .thenReturn(Optional.of(state));
        when(stateStore.uploadedChunks(state.getUploadId()))
                .thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.complete(7L, state.getUploadId()));

        assertEquals(40012, error.getCode());
        verify(minioStorageService, never()).compose(
                anyString(), any(), anyString());
    }

    @Test
    void completeMergesVerifiesAndCreatesAudioFileOnce() throws Exception {
        byte[] content = new byte[(int) CHUNK_SIZE + 4];
        content[0] = 10;
        content[content.length - 1] = 40;
        String sha = hashOf(content);
        MultipartUploadState state = state("d".repeat(32), 7L, sha,
                MultipartUploadStatus.UPLOADING);
        state.setSizeBytes((long) content.length);
        state.setChunkSize(CHUNK_SIZE);
        state.setTotalChunks(2);
        when(stateStore.find(state.getUploadId()))
                .thenReturn(Optional.of(state));
        when(stateStore.uploadedChunks(state.getUploadId()))
                .thenReturn(List.of(0, 1));
        when(stateStore.acquireMergeLock(anyString(), anyString()))
                .thenReturn(true);
        when(minioStorageService.getObject(anyString()))
                .thenReturn(new ByteArrayInputStream(content));
        when(minioStorageService.exists(state.getFinalObjectKey()))
                .thenReturn(false);
        when(minioProperties.getBucketName()).thenReturn("audio-agent");
        when(audioMetadataService.extractMetadata(any()))
                .thenReturn(AudioMetadata.builder().durationMs(1234L).build());
        when(completionPersistenceService.persistCompletedUpload(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.complete(7L, state.getUploadId());

        ArgumentCaptor<AudioFile> fileCaptor =
                ArgumentCaptor.forClass(AudioFile.class);
        verify(completionPersistenceService)
                .persistCompletedUpload(fileCaptor.capture());
        AudioFile persisted = fileCaptor.getValue();
        assertEquals(persisted.getId(), result.getAudioFile().getFileId());
        assertEquals("COMPLETED", result.getStatus());
        verify(minioStorageService).compose(anyString(),
                eq(List.of("temporary/chunk-uploads/7/" + state.getUploadId()
                        + "/chunks/0", "temporary/chunk-uploads/7/"
                        + state.getUploadId() + "/chunks/1")), eq("audio/wav"));
        verify(minioStorageService).copy(anyString(),
                eq(state.getFinalObjectKey()));
        assertEquals(persisted.getId(), persisted.getRootAudioFileId());
        assertEquals(0, persisted.getVersionNo());
        assertEquals("原始版本", persisted.getVersionSummary());
        assertEquals(null, persisted.getSourceFileId());
        verify(stateStore).markCompleted(state, persisted.getId());
    }

    @Test
    void completeRejectsServerHashMismatchAndPublishesNothing() {
        MultipartUploadState state = state("e".repeat(32), 7L,
                hashOf(new byte[]{1}), MultipartUploadStatus.UPLOADING);
        state.setSizeBytes(1L);
        state.setTotalChunks(1);
        when(stateStore.find(state.getUploadId()))
                .thenReturn(Optional.of(state));
        when(stateStore.uploadedChunks(state.getUploadId()))
                .thenReturn(List.of(0));
        when(stateStore.acquireMergeLock(anyString(), anyString()))
                .thenReturn(true);
        when(minioStorageService.getObject(anyString()))
                .thenReturn(new ByteArrayInputStream(new byte[]{2}));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.complete(7L, state.getUploadId()));

        assertEquals(40013, error.getCode());
        verify(minioStorageService, never()).copy(anyString(), anyString());
        verify(completionPersistenceService, never())
                .persistCompletedUpload(any(AudioFile.class));
        verify(stateStore).clearUploadedChunks(state.getUploadId());
    }

    @Test
    void redisCompletionFailureDoesNotDeleteCommittedFinalObject()
            throws Exception {
        byte[] content = new byte[(int) CHUNK_SIZE];
        String sha = hashOf(content);
        MultipartUploadState state = state("1".repeat(32), 7L, sha,
                MultipartUploadStatus.UPLOADING);
        when(stateStore.find(state.getUploadId()))
                .thenReturn(Optional.of(state));
        when(stateStore.uploadedChunks(state.getUploadId()))
                .thenReturn(List.of(0));
        when(stateStore.acquireMergeLock(anyString(), anyString()))
                .thenReturn(true);
        when(minioStorageService.getObject(anyString()))
                .thenReturn(new ByteArrayInputStream(content));
        when(minioStorageService.exists(state.getFinalObjectKey()))
                .thenReturn(false);
        when(minioProperties.getBucketName()).thenReturn("audio-agent");
        when(audioMetadataService.extractMetadata(any()))
                .thenReturn(AudioMetadata.builder().durationMs(5L).build());
        when(completionPersistenceService.persistCompletedUpload(any()))
                .thenAnswer(invocation -> {
                    AudioFile file = invocation.getArgument(0);
                    file.setId(123L);
                    return file;
                });
        doThrow(new IllegalStateException("redis down"))
                .when(stateStore).markCompleted(state, 123L);

        var result = service.complete(7L, state.getUploadId());

        assertEquals(123L, result.getAudioFile().getFileId());
        assertEquals(MultipartUploadStatus.COMPLETED, state.getStatus());
        verify(minioStorageService, never())
                .delete(state.getFinalObjectKey());
    }

    @Test
    void repeatedCompleteReturnsExistingFileWithoutAnotherMerge() {
        String sha = hashOf(new byte[]{9});
        MultipartUploadState state = state("f".repeat(32), 7L, sha,
                MultipartUploadStatus.COMPLETED);
        state.setAudioFileId(77L);
        AudioFile file = availableFile(77L, 7L, sha);
        when(stateStore.find(state.getUploadId()))
                .thenReturn(Optional.of(state));
        when(audioFileMapper.selectFirstAvailableByHash(
                7L, sha, FileStatus.AVAILABLE.getCode()))
                .thenReturn(file);
        when(minioStorageService.exists("audio-agent", "original/meeting.wav"))
                .thenReturn(true);

        var result = service.complete(7L, state.getUploadId());

        assertEquals(77L, result.getAudioFile().getFileId());
        verify(minioStorageService, never()).compose(
                anyString(), any(), anyString());
        verify(completionPersistenceService)
                .ensureUploadedEvent(file);
        verify(completionPersistenceService, never())
                .persistCompletedUpload(any(AudioFile.class));
    }

    private static MultipartUploadInitRequest initRequest(
            String name, long size, String sha) {
        MultipartUploadInitRequest request = new MultipartUploadInitRequest();
        request.setOriginalName(name);
        request.setMimeType("audio/wav");
        request.setSizeBytes(size);
        request.setSha256(sha);
        request.setChunkSize(CHUNK_SIZE);
        request.setTotalChunks(1);
        return request;
    }

    private static MultipartUploadState state(String uploadId, Long userId,
                                               String sha,
                                               MultipartUploadStatus status) {
        return MultipartUploadState.builder()
                .uploadId(uploadId)
                .userId(userId)
                .originalName("meeting.wav")
                .extension("wav")
                .mimeType("audio/wav")
                .sizeBytes(CHUNK_SIZE)
                .sha256(sha)
                .chunkSize(CHUNK_SIZE)
                .totalChunks(1)
                .finalObjectKey("original/7/meeting.wav")
                .status(status)
                .createdAt(1L)
                .updatedAt(1L)
                .build();
    }

    private static AudioFile availableFile(Long id, Long userId,
                                           String sha) {
        AudioFile file = new AudioFile();
        file.setId(id);
        file.setUserId(userId);
        file.setSha256(sha);
        file.setOriginalName("meeting.wav");
        file.setBucketName("audio-agent");
        file.setObjectKey("original/meeting.wav");
        file.setFileStatus(FileStatus.AVAILABLE);
        file.setDeleted(0);
        return file;
    }

    private static String hashOf(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
