package com.audioagent.transcription.service.impl;

import com.audioagent.transcript.entity.AudioTranscriptSegment;
import com.audioagent.transcript.mapper.AudioTranscriptSegmentMapper;
import com.audioagent.transcription.config.TranscriptionProperties;
import com.audioagent.transcription.dto.AsrSegmentResponse;
import com.audioagent.transcription.dto.AsrTranscriptionResponse;
import com.audioagent.transcription.entity.AudioTranscript;
import com.audioagent.transcription.entity.AudioTranscriptionTask;
import com.audioagent.transcription.mapper.AudioTranscriptMapper;
import com.audioagent.transcription.mapper.AudioTranscriptionTaskMapper;
import com.audioagent.transcription.model.TranscriptionTaskStatus;
import com.audioagent.transcription.service.TranscriptionResultPersistenceService;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.sql.DatabaseMetaData;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringJUnitConfig(TranscriptionPersistenceIntegrationTest.TestConfig.class)
class TranscriptionPersistenceIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TranscriptionResultPersistenceService persistenceService;

    @Autowired
    private AudioTranscriptMapper transcriptMapper;

    @Autowired
    private AudioTranscriptSegmentMapper segmentMapper;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUpSchema() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS audio_transcript_segment");
        jdbc.execute("DROP TABLE IF EXISTS audio_transcript");
        jdbc.execute("DROP TABLE IF EXISTS audio_transcription_task");
        jdbc.execute("""
                CREATE TABLE audio_transcription_task (
                    id BIGINT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    audio_file_id BIGINT NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    language VARCHAR(32) NOT NULL,
                    enable_speaker_diarization TINYINT NOT NULL,
                    progress_percent INT NOT NULL,
                    provider VARCHAR(64),
                    model_name VARCHAR(100),
                    retry_count INT NOT NULL,
                    failure_code VARCHAR(100),
                    failure_message VARCHAR(500),
                    started_at TIMESTAMP,
                    finished_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE audio_transcript (
                    id BIGINT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    audio_file_id BIGINT NOT NULL,
                    transcription_task_id BIGINT NOT NULL,
                    language VARCHAR(32) NOT NULL,
                    full_text CLOB NOT NULL,
                    duration_ms BIGINT,
                    speaker_count INT,
                    segment_count INT NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    CONSTRAINT uk_transcript_task
                        UNIQUE (transcription_task_id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE audio_transcript_segment (
                    id BIGINT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    transcript_id BIGINT NOT NULL,
                    segment_order INT NOT NULL,
                    start_ms BIGINT NOT NULL,
                    end_ms BIGINT NOT NULL,
                    speaker_label VARCHAR(64),
                    text VARCHAR(4000) NOT NULL,
                    confidence DECIMAL(6,5),
                    created_at TIMESTAMP NOT NULL,
                    CONSTRAINT uk_transcript_segment_order
                        UNIQUE (transcript_id, segment_order),
                    CONSTRAINT chk_transcript_segment_time
                        CHECK (start_ms >= 0 AND end_ms > start_ms)
                )
                """);
        insertRunningTask();
    }

    @Test
    void entityFieldsMatchTableAndInsertPersistsUserId() throws Exception {
        Set<String> entityColumns = Arrays.stream(
                        AudioTranscriptSegment.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(field -> camelToSnake(field.getName()))
                .collect(Collectors.toSet());
        Set<String> tableColumns;
        try (var connection = dataSource.getConnection();
             var columns = connection.getMetaData().getColumns(
                     null, null, "audio_transcript_segment", null)) {
            var builder = new java.util.HashSet<String>();
            while (columns.next()) {
                builder.add(columns.getString("COLUMN_NAME")
                        .toLowerCase(Locale.ROOT));
                if ("user_id".equalsIgnoreCase(
                        columns.getString("COLUMN_NAME"))) {
                    assertEquals(DatabaseMetaData.columnNoNulls,
                            columns.getInt("NULLABLE"));
                    assertEquals("BIGINT",
                            columns.getString("TYPE_NAME")
                                    .toUpperCase(Locale.ROOT));
                }
            }
            tableColumns = Set.copyOf(builder);
        }

        assertEquals(tableColumns, entityColumns);

        persistenceService.save(task(), response(1000L));

        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audio_transcript_segment "
                        + "WHERE user_id = 7",
                Integer.class));
        assertEquals(1, count("audio_transcript"));
        assertEquals(2, count("audio_transcript_segment"));
        var savedSegments = segmentMapper.selectOwnedAll(
                7L, jdbc.queryForObject(
                        "SELECT id FROM audio_transcript",
                        Long.class));
        assertEquals(List.of(1, 2), savedSegments.stream()
                .map(AudioTranscriptSegment::getSegmentOrder).toList());
        assertEquals(List.of(0L, 500L), savedSegments.stream()
                .map(AudioTranscriptSegment::getStartMs).toList());
        assertEquals(List.of(400L, 1000L), savedSegments.stream()
                .map(AudioTranscriptSegment::getEndMs).toList());
        assertEquals("SUCCESS", jdbc.queryForObject(
                "SELECT status FROM audio_transcription_task WHERE id = 90",
                String.class));
        assertEquals(100, jdbc.queryForObject(
                "SELECT progress_percent FROM audio_transcription_task "
                        + "WHERE id = 90",
                Integer.class));
    }

    @Test
    void segmentFailureRollsBackTranscriptAndTaskCompletion() {
        assertThrows(RuntimeException.class,
                () -> persistenceService.save(task(), response(0L)));

        assertEquals(0, count("audio_transcript"));
        assertEquals(0, count("audio_transcript_segment"));
        assertEquals("RUNNING", jdbc.queryForObject(
                "SELECT status FROM audio_transcription_task WHERE id = 90",
                String.class));
        assertEquals(85, jdbc.queryForObject(
                "SELECT progress_percent FROM audio_transcription_task "
                        + "WHERE id = 90",
                Integer.class));
    }

    @Test
    void repeatedTaskDoesNotCreateDuplicateTranscript() {
        persistenceService.save(task(), response(1000L));
        persistenceService.save(task(), response(1000L));

        assertEquals(1, count("audio_transcript"));
        assertEquals(2, count("audio_transcript_segment"));
    }

    @Test
    void transcriptQueriesAreRestrictedToCurrentUser() {
        persistenceService.save(task(), response(1000L));
        AudioTranscript transcript =
                transcriptMapper.selectByTaskAndUser(90L, 7L);
        assertNotNull(transcript);
        assertNotNull(transcriptMapper.selectOwned(
                7L, transcript.getId()));
        assertNull(transcriptMapper.selectOwned(
                8L, transcript.getId()));

        var ownerPage = segmentMapper.selectOwnedPage(
                new Page<>(1, 10, false),
                7L, transcript.getId(), null);
        var otherUserPage = segmentMapper.selectOwnedPage(
                new Page<>(1, 10, false),
                8L, transcript.getId(), null);

        assertEquals(2, ownerPage.getRecords().size());
        assertEquals(0, otherUserPage.getRecords().size());
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table,
                Integer.class);
    }

    private void insertRunningTask() {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
                INSERT INTO audio_transcription_task (
                    id, user_id, audio_file_id, status, language,
                    enable_speaker_diarization, progress_percent,
                    retry_count, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, 90L, 7L, 20L, "RUNNING", "zh",
                0, 85, 0, now, now);
    }

    private static AudioTranscriptionTask task() {
        AudioTranscriptionTask task = new AudioTranscriptionTask();
        task.setId(90L);
        task.setUserId(7L);
        task.setAudioFileId(20L);
        task.setStatus(TranscriptionTaskStatus.RUNNING);
        return task;
    }

    private static AsrTranscriptionResponse response(long endMs) {
        AsrSegmentResponse first = new AsrSegmentResponse();
        first.setOrder(1);
        first.setStartMs(0L);
        first.setEndMs(endMs <= 0 ? endMs : 400L);
        first.setText("test transcript");
        first.setConfidence(new BigDecimal("0.90000"));

        List<AsrSegmentResponse> segments;
        if (endMs <= 0) {
            segments = List.of(first);
        } else {
            AsrSegmentResponse second = new AsrSegmentResponse();
            second.setOrder(2);
            second.setStartMs(500L);
            second.setEndMs(endMs);
            second.setText("segment");
            segments = List.of(first, second);
        }

        AsrTranscriptionResponse response = new AsrTranscriptionResponse();
        response.setLanguage("zh");
        response.setDurationMs(1000L);
        response.setFullText("test transcript segment");
        response.setSegments(segments);
        return response;
    }

    private static String camelToSnake(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT);
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:transcription_persistence;"
                    + "MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
            return dataSource;
        }

        @Bean
        PlatformTransactionManager transactionManager(
                DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) {
            Environment environment = new Environment(
                    "test",
                    new SpringManagedTransactionFactory(),
                    dataSource);
            MybatisConfiguration configuration =
                    new MybatisConfiguration(environment);
            configuration.setMapUnderscoreToCamelCase(true);
            MybatisPlusInterceptor interceptor =
                    new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(
                    new PaginationInnerInterceptor(DbType.H2));
            configuration.addInterceptor(interceptor);
            configuration.addMapper(AudioTranscriptMapper.class);
            configuration.addMapper(AudioTranscriptSegmentMapper.class);
            configuration.addMapper(AudioTranscriptionTaskMapper.class);
            return new MybatisSqlSessionFactoryBuilder()
                    .build(configuration);
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(
                SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory);
        }

        @Bean
        AudioTranscriptMapper transcriptMapper(
                SqlSessionTemplate sqlSessionTemplate) {
            return sqlSessionTemplate.getMapper(
                    AudioTranscriptMapper.class);
        }

        @Bean
        AudioTranscriptSegmentMapper segmentMapper(
                SqlSessionTemplate sqlSessionTemplate) {
            return sqlSessionTemplate.getMapper(
                    AudioTranscriptSegmentMapper.class);
        }

        @Bean
        AudioTranscriptionTaskMapper taskMapper(
                SqlSessionTemplate sqlSessionTemplate) {
            return sqlSessionTemplate.getMapper(
                    AudioTranscriptionTaskMapper.class);
        }

        @Bean
        TranscriptionProperties transcriptionProperties() {
            return new TranscriptionProperties();
        }

        @Bean
        TranscriptionResultPersistenceService persistenceService(
                AudioTranscriptMapper transcriptMapper,
                AudioTranscriptSegmentMapper segmentMapper,
                AudioTranscriptionTaskMapper taskMapper,
                TranscriptionProperties properties) {
            return new TranscriptionResultPersistenceServiceImpl(
                    transcriptMapper, segmentMapper, taskMapper,
                    properties);
        }
    }
}
