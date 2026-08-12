package com.audioagent.contentanalysis.service;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.contentanalysis.entity.AudioContentAnalysisResult;
import com.audioagent.contentanalysis.entity.AudioContentAnalysisTask;
import com.audioagent.contentanalysis.exception.ContentAnalysisException;
import com.audioagent.contentanalysis.executor.ContentAnalysisExecution;
import com.audioagent.contentanalysis.mapper.AudioContentAnalysisResultMapper;
import com.audioagent.contentanalysis.mapper.AudioContentAnalysisTaskMapper;
import com.audioagent.contentanalysis.model.ContentAnalysisOutput;
import com.audioagent.contentanalysis.model.ContentAnalysisTaskStatus;
import com.audioagent.transcript.mapper.AudioTranscriptSegmentMapper;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "spring.datasource.url="
                + "jdbc:h2:mem:contentanalysis;"
                + "MODE=MySQL;DB_CLOSE_DELAY=-1;"
                + "DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations="
                + "classpath:content-analysis-test-schema.sql",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "audio-agent.ai.deepseek.enabled=false",
        "audio.analysis.dispatch-mode=local",
        "audio.processing.enabled=false",
        "mybatis-plus.configuration.map-underscore-to-camel-case=true"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ContentAnalysisPersistenceIntegrationTest {

    @Autowired
    private AudioContentAnalysisTaskMapper taskMapper;

    @Autowired
    private AudioContentAnalysisResultMapper resultMapper;

    @Autowired
    private ContentAnalysisResultPersistenceService persistenceService;

    @Autowired
    private AudioTranscriptSegmentMapper segmentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private MinioClient minioClient;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM audio_content_analysis_result");
        jdbcTemplate.update("DELETE FROM audio_content_analysis_task");
        jdbcTemplate.update("DELETE FROM audio_transcript_segment");
    }

    @Test
    void selectByIdAndOwnedQueryPopulateAnalysisTypesJson() {
        AudioContentAnalysisTask source =
                task(101L, ContentAnalysisTaskStatus.FAILED);
        assertEquals(1, taskMapper.insert(source));

        AudioContentAnalysisTask byId = taskMapper.selectById(101L);
        AudioContentAnalysisTask owned =
                taskMapper.selectOwned(7L, 101L);

        assertEquals("[\"SUMMARY\"]",
                byId.getAnalysisTypesJson());
        assertEquals("[\"SUMMARY\"]",
                owned.getAnalysisTypesJson());
        assertEquals(ContentAnalysisTaskStatus.FAILED,
                owned.getStatus());
    }

    @Test
    void contentSourceQueryUsesCanonicalSpeakerLabelColumn() {
        jdbcTemplate.update("""
                INSERT INTO audio_transcript_segment (
                    id, user_id, transcript_id, segment_order,
                    start_ms, end_ms, speaker_label, text,
                    confidence, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                301L, 7L, 81L, 1,
                0L, 1000L, "speaker-1", "text",
                null, LocalDateTime.now());

        var segments = segmentMapper.selectOwnedAll(7L, 81L);

        assertEquals(1, segments.size());
        assertEquals("speaker-1",
                segments.getFirst().getSpeakerLabel());
    }

    @Test
    void explicitResultUpsertStoresJsonAndNullableTokensOncePerTask() {
        assertEquals(1, taskMapper.insert(
                task(102L, ContentAnalysisTaskStatus.RUNNING)));
        AudioContentAnalysisResult first = result(
                201L, 102L, "[]");

        assertEquals(1, resultMapper.upsert(first));
        AudioContentAnalysisResult stored =
                resultMapper.selectByTask(102L);

        assertNotNull(stored);
        assertEquals("{\"oneSentence\":\"summary\"}",
                stored.getSummaryJson());
        assertEquals("[{\"title\":\"point\"}]",
                stored.getKeyPointsJson());
        assertEquals("[{\"title\":\"chapter\"}]",
                stored.getChaptersJson());
        assertEquals("[]", stored.getSpeechIssuesJson());
        assertNull(stored.getPromptTokens());
        assertNull(stored.getCompletionTokens());
        assertNull(stored.getTotalTokens());

        AudioContentAnalysisResult duplicate = result(
                202L, 102L, "[{\"type\":\"FILLER_WORD\"}]");
        resultMapper.upsert(duplicate);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audio_content_analysis_result "
                        + "WHERE task_id = 102",
                Integer.class);
        assertEquals(1, count);
        assertEquals("[{\"type\":\"FILLER_WORD\"}]",
                resultMapper.selectByTask(102L)
                        .getSpeechIssuesJson());
    }

    @Test
    void saveSuccessCommitsResultAndCompletesTask() {
        AudioContentAnalysisTask task =
                task(103L, ContentAnalysisTaskStatus.RUNNING);
        assertEquals(1, taskMapper.insert(task));

        persistenceService.saveSuccess(task, execution());

        AudioContentAnalysisResult stored =
                resultMapper.selectByTask(103L);
        AudioContentAnalysisTask completed =
                taskMapper.selectById(103L);
        assertNotNull(stored);
        assertEquals("[]", stored.getSpeechIssuesJson());
        assertEquals(11, stored.getPromptTokens());
        assertEquals(ContentAnalysisTaskStatus.SUCCESS,
                completed.getStatus());
        assertEquals(100, completed.getProgressPercent());
        assertNotNull(completed.getFinishedAt());
    }

    @Test
    void taskCompletionFailureRollsBackInsertedResult() {
        AudioContentAnalysisTask task =
                task(104L, ContentAnalysisTaskStatus.FAILED);
        assertEquals(1, taskMapper.insert(task));

        ContentAnalysisException failure = assertThrows(
                ContentAnalysisException.class,
                () -> persistenceService.saveSuccess(task, execution()));

        assertEquals(ErrorCode.AI_RESULT_PERSISTENCE_FAILED,
                failure.getErrorCode());
        assertFalse(failure.isRetryable());
        assertNull(resultMapper.selectByTask(104L));
        assertEquals(ContentAnalysisTaskStatus.FAILED,
                taskMapper.selectById(104L).getStatus());
    }

    private AudioContentAnalysisTask task(
            long id, ContentAnalysisTaskStatus status) {
        LocalDateTime now = LocalDateTime.now();
        AudioContentAnalysisTask task =
                new AudioContentAnalysisTask();
        task.setId(id);
        task.setUserId(7L);
        task.setTranscriptId(81L);
        task.setStatus(status);
        task.setAnalysisTypesJson("[\"SUMMARY\"]");
        task.setSummaryStyle("STANDARD");
        task.setProgressPercent(0);
        task.setModelName("deepseek-test");
        task.setPromptVersion("content-analysis-v1");
        task.setRetryCount(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }

    private AudioContentAnalysisResult result(
            long id, long taskId, String speechIssuesJson) {
        LocalDateTime now = LocalDateTime.now();
        AudioContentAnalysisResult result =
                new AudioContentAnalysisResult();
        result.setId(id);
        result.setUserId(7L);
        result.setTaskId(taskId);
        result.setTranscriptId(81L);
        result.setSummaryJson(
                "{\"oneSentence\":\"summary\"}");
        result.setKeyPointsJson("[{\"title\":\"point\"}]");
        result.setChaptersJson("[{\"title\":\"chapter\"}]");
        result.setSpeechIssuesJson(speechIssuesJson);
        result.setModelName("deepseek-test");
        result.setPromptVersion("content-analysis-v1");
        result.setCreatedAt(now);
        result.setUpdatedAt(now);
        return result;
    }

    private ContentAnalysisExecution execution() {
        ContentAnalysisOutput output =
                new ContentAnalysisOutput();
        output.setSummary(new ContentAnalysisOutput.Summary(
                "summary", "details", List.of("topic")));
        output.setKeyPoints(List.of());
        output.setChapters(List.of());
        output.setSpeechIssues(List.of());
        return new ContentAnalysisExecution(
                output, "deepseek-test", 11, 12, 23, 1);
    }
}
