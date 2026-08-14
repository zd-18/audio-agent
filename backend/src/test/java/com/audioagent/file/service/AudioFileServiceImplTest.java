package com.audioagent.file.service;

import com.audioagent.common.api.PageResult;
import com.audioagent.common.enums.FileStatus;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.file.service.impl.AudioFileServiceImpl;
import com.audioagent.file.vo.AudioFileListVO;
import com.audioagent.infrastructure.ffprobe.AudioMetadataService;
import com.audioagent.infrastructure.minio.MinioProperties;
import com.audioagent.infrastructure.minio.MinioStorageService;
import com.audioagent.transcription.mapper.AudioTranscriptionTaskMapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AudioFileServiceImplTest {

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                AudioFile.class
        );
    }

    @Mock
    private AudioFileMapper audioFileMapper;
    @Mock
    private MinioStorageService minioStorageService;
    @Mock
    private MinioProperties minioProperties;
    @Mock
    private AudioMetadataService audioMetadataService;
    @Mock
    private AudioTranscriptionTaskMapper transcriptionTaskMapper;
    @InjectMocks
    private AudioFileServiceImpl service;

    @Test
    void listFilesUsesDefaultPageAndReturnsVo() {
        AudioFile file = audioFile(11L, "meeting.wav", FileStatus.AVAILABLE);
        mockPage(List.of(file), 1);

        PageResult<AudioFileListVO> result = service.listFiles(
                7L, 1, 10, null, null);

        assertEquals(1, result.getCurrent());
        assertEquals(10, result.getSize());
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getPages());
        assertEquals(11L, result.getRecords().getFirst().getAudioFileId());
        assertEquals("meeting.wav",
                result.getRecords().getFirst().getOriginalFileName());

        ArgumentCaptor<Page<AudioFile>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<Wrapper<AudioFile>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(audioFileMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertEquals(1, pageCaptor.getValue().getCurrent());
        assertEquals(10, pageCaptor.getValue().getSize());
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("user_id"));
        assertTrue(sql.contains("created_at"));
        assertFalse(sql.contains("original_name"));
        assertFalse(sql.contains("file_status"));
    }

    @Test
    void listFilesAddsKeywordConditionOnlyWhenProvided() {
        mockPage(List.of(), 0);

        service.listFiles(7L, 1, 10, " meeting ", null);

        ArgumentCaptor<Wrapper<AudioFile>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(audioFileMapper).selectPage(any(Page.class), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("original_name LIKE"));
        assertTrue(parameters(captor.getValue()).containsValue("%meeting%"));
    }

    @Test
    void listFilesFiltersByStatus() {
        mockPage(List.of(), 0);

        service.listFiles(7L, 1, 10, null, "failed");

        ArgumentCaptor<Wrapper<AudioFile>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(audioFileMapper).selectPage(any(Page.class), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("file_status"));
        assertTrue(parameters(captor.getValue()).containsValue(FileStatus.FAILED));
    }

    @Test
    void listFilesReturnsEmptyRecordsWhenNothingMatches() {
        mockPage(List.of(), 0);

        PageResult<AudioFileListVO> result = service.listFiles(
                7L, 2, 10, "missing", null);

        assertNotNull(result.getRecords());
        assertTrue(result.getRecords().isEmpty());
        assertEquals(0, result.getTotal());
        assertEquals(0, result.getPages());
    }

    @Test
    void listFilesRejectsSizeAboveMaximum() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.listFiles(7L, 1, 101, null, null));

        assertEquals(40000, exception.getCode());
        assertTrue(exception.getMessage().contains("between 1 and 100"));
        verifyNoInteractions(audioFileMapper);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void mockPage(List<AudioFile> records, long total) {
        when(audioFileMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenAnswer(invocation -> {
                    Page<AudioFile> page = invocation.getArgument(0);
                    page.setRecords(records);
                    page.setTotal(total);
                    return page;
                });
    }

    private static AudioFile audioFile(Long id, String name,
                                       FileStatus status) {
        AudioFile file = new AudioFile();
        file.setId(id);
        file.setOriginalName(name);
        file.setSizeBytes(1024L);
        file.setMimeType("audio/wav");
        file.setDurationMs(3000L);
        file.setFileStatus(status);
        file.setSha256("abc");
        file.setCreatedAt(LocalDateTime.of(2026, 7, 16, 10, 0));
        return file;
    }

    private static java.util.Map<String, Object> parameters(
            Wrapper<AudioFile> wrapper) {
        return ((AbstractWrapper<AudioFile, ?, ?>) wrapper)
                .getParamNameValuePairs();
    }
}
