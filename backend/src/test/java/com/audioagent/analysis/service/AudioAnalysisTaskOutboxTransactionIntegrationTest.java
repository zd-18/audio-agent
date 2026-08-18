package com.audioagent.analysis.service;

import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.enums.AnalysisTaskStatus;
import com.audioagent.analysis.mapper.AudioAnalysisResultMapper;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.analysis.loudness.LoudnessEvaluator;
import com.audioagent.analysis.outbox.AudioAnalysisTaskDispatchEvent;
import com.audioagent.analysis.outbox.AudioAnalysisTaskDispatchOutboxService;
import com.audioagent.analysis.service.impl.AudioAnalysisTaskServiceImpl;
import com.audioagent.analysis.vo.TaskVO;
import com.audioagent.auth.service.AudioResourceOwnershipService;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.audioagent.outbox.entity.OutboxEvent;
import com.audioagent.outbox.mapper.OutboxEventMapper;
import com.audioagent.outbox.model.OutboxEventStatus;
import com.audioagent.outbox.payload.OutboxPayloadCodec;
import com.audioagent.outbox.service.OutboxEventService;
import com.audioagent.outbox.service.impl.OutboxEventServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(
        classes = AudioAnalysisTaskOutboxTransactionIntegrationTest
                .TestConfiguration.class)
class AudioAnalysisTaskOutboxTransactionIntegrationTest {

    @jakarta.annotation.Resource
    private JdbcTemplate jdbc;
    @jakarta.annotation.Resource
    private AudioAnalysisTaskService taskService;
    @jakarta.annotation.Resource
    private AudioFileMapper audioFileMapper;
    @jakarta.annotation.Resource
    private OutboxEventMapper outboxEventMapper;

    @BeforeEach
    void setUpSchema() {
        jdbc.execute("DROP TABLE IF EXISTS outbox_event");
        jdbc.execute("DROP TABLE IF EXISTS audio_analysis_task");
        jdbc.execute("""
                CREATE TABLE audio_analysis_task (
                    id BIGINT PRIMARY KEY,
                    audio_file_id BIGINT NOT NULL,
                    source_event_id BIGINT UNIQUE,
                    analysis_type VARCHAR(50),
                    status VARCHAR(20) NOT NULL,
                    progress INT,
                    error_message VARCHAR(1000),
                    retry_count INT,
                    max_retry_count INT,
                    next_retry_at TIMESTAMP,
                    last_error_code VARCHAR(100),
                    last_message_id VARCHAR(128),
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP,
                    started_at TIMESTAMP,
                    finished_at TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE outbox_event (
                    id BIGINT PRIMARY KEY,
                    aggregate_type VARCHAR(64) NOT NULL,
                    aggregate_id VARCHAR(128) NOT NULL,
                    event_type VARCHAR(128) NOT NULL,
                    payload CLOB NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    retry_count INT NOT NULL,
                    next_retry_at TIMESTAMP,
                    locked_at TIMESTAMP,
                    lock_owner VARCHAR(64),
                    last_error VARCHAR(1000),
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    published_at TIMESTAMP,
                    UNIQUE (aggregate_type, aggregate_id, event_type)
                )
                """);
        reset(audioFileMapper);
        AudioFile file = new AudioFile();
        file.setId(11L);
        file.setUserId(7L);
        file.setDeleted(0);
        when(audioFileMapper.selectById(11L)).thenReturn(file);
    }

    @Test
    void taskAndInitialDispatchOutboxCommitTogether() {
        TaskVO created = taskService.createTaskFromUploadedFile(
                11L, 7L, 501L);

        assertEquals(1, count("audio_analysis_task"));
        assertEquals(1, count("outbox_event"));
        OutboxEvent event = outboxEventMapper.selectByAggregateAndType(
                AudioAnalysisTaskDispatchEvent.AGGREGATE_TYPE,
                created.getTaskId().toString(),
                AudioAnalysisTaskDispatchEvent.EVENT_TYPE);
        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertTrue(event.getPayload().contains(
                "\"taskId\":" + created.getTaskId()));
    }

    @Test
    void outboxInsertFailureRollsBackTaskInsert() {
        jdbc.execute("ALTER TABLE outbox_event ADD CONSTRAINT "
                + "reject_analysis_outbox CHECK "
                + "(aggregate_type <> 'AUDIO_ANALYSIS_TASK')");

        assertThrows(RuntimeException.class,
                () -> taskService.createTaskFromUploadedFile(
                        11L, 7L, 502L));

        assertEquals(0, count("audio_analysis_task"));
        assertEquals(0, count("outbox_event"));
    }

    @Test
    void taskInsertFailureDoesNotLeaveOrphanOutbox() {
        jdbc.execute("ALTER TABLE audio_analysis_task ADD CONSTRAINT "
                + "reject_task CHECK (id < 0)");

        assertThrows(RuntimeException.class,
                () -> taskService.createTaskFromUploadedFile(
                        11L, 7L, 503L));

        assertEquals(0, count("audio_analysis_task"));
        assertEquals(0, count("outbox_event"));
    }

    @Test
    void duplicateSourceEventKeepsOneTaskAndOneDispatchOutbox() {
        TaskVO first = taskService.createTaskFromUploadedFile(
                11L, 7L, 504L);
        TaskVO duplicate = taskService.createTaskFromUploadedFile(
                11L, 7L, 504L);

        assertEquals(first.getTaskId(), duplicate.getTaskId());
        assertEquals(1, count("audio_analysis_task"));
        assertEquals(1, count("outbox_event"));
    }

    @Test
    void manualRetryReactivatesSameDispatchOutboxInTaskTransaction() {
        TaskVO created = taskService.createTaskFromUploadedFile(
                11L, 7L, 505L);
        OutboxEvent initial = outboxEventMapper.selectByAggregateAndType(
                AudioAnalysisTaskDispatchEvent.AGGREGATE_TYPE,
                created.getTaskId().toString(),
                AudioAnalysisTaskDispatchEvent.EVENT_TYPE);
        jdbc.update("UPDATE audio_analysis_task SET status='FAILED' "
                + "WHERE id=?", created.getTaskId());
        jdbc.update("UPDATE outbox_event SET status='PUBLISHED', "
                + "published_at=CURRENT_TIMESTAMP, retry_count=3 "
                + "WHERE id=?", initial.getId());

        TaskVO retried = taskService.retryTask(7L, created.getTaskId());

        OutboxEvent reactivated = outboxEventMapper.selectById(
                initial.getId());
        assertEquals("PENDING", retried.getStatus());
        assertEquals(initial.getId(), reactivated.getId());
        assertEquals(OutboxEventStatus.PENDING, reactivated.getStatus());
        assertEquals(0, reactivated.getRetryCount());
        assertEquals(1, count("outbox_event"));
    }

    @Test
    void manualRetryRollsBackTaskWhenDispatchOutboxCannotReactivate() {
        TaskVO created = taskService.createTaskFromUploadedFile(
                11L, 7L, 506L);
        OutboxEvent initial = outboxEventMapper.selectByAggregateAndType(
                AudioAnalysisTaskDispatchEvent.AGGREGATE_TYPE,
                created.getTaskId().toString(),
                AudioAnalysisTaskDispatchEvent.EVENT_TYPE);
        jdbc.update("UPDATE audio_analysis_task SET status='FAILED' "
                + "WHERE id=?", created.getTaskId());
        jdbc.update("UPDATE outbox_event SET status='PROCESSING', "
                + "lock_owner='worker', locked_at=CURRENT_TIMESTAMP "
                + "WHERE id=?", initial.getId());

        assertThrows(IllegalStateException.class,
                () -> taskService.retryTask(7L, created.getTaskId()));

        assertEquals("FAILED", jdbc.queryForObject(
                "SELECT status FROM audio_analysis_task WHERE id=?",
                String.class, created.getTaskId()));
        assertEquals(OutboxEventStatus.PROCESSING,
                outboxEventMapper.selectById(initial.getId()).getStatus());
    }

    private int count(String table) {
        Integer result = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table, Integer.class);
        return result == null ? 0 : result;
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {

        @Bean
        DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:analysis-outbox;MODE=MySQL;"
                    + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
            return dataSource;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(DataSource dataSource) {
            Environment environment = new Environment(
                    "test", new SpringManagedTransactionFactory(), dataSource);
            MybatisConfiguration configuration =
                    new MybatisConfiguration(environment);
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(AudioAnalysisTaskMapper.class);
            configuration.addMapper(OutboxEventMapper.class);
            SqlSessionFactory factory =
                    new MybatisSqlSessionFactoryBuilder()
                            .build(configuration);
            return new SqlSessionTemplate(factory);
        }

        @Bean
        AudioAnalysisTaskMapper audioAnalysisTaskMapper(
                SqlSessionTemplate template) {
            return template.getMapper(AudioAnalysisTaskMapper.class);
        }

        @Bean
        OutboxEventMapper outboxEventMapper(SqlSessionTemplate template) {
            return template.getMapper(OutboxEventMapper.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        OutboxEventService outboxEventService(
                OutboxEventMapper mapper, ObjectMapper objectMapper) {
            return new OutboxEventServiceImpl(
                    mapper, new OutboxPayloadCodec(objectMapper));
        }

        @Bean
        AudioAnalysisTaskDispatchOutboxService dispatchOutboxService(
                OutboxEventService service, OutboxEventMapper mapper,
                ObjectMapper objectMapper) {
            return new AudioAnalysisTaskDispatchOutboxService(
                    service, mapper, objectMapper);
        }

        @Bean
        AnalysisProperties analysisProperties() {
            AnalysisProperties properties = new AnalysisProperties();
            properties.setDispatchMode("rabbit");
            return properties;
        }

        @Bean
        AudioFileMapper audioFileMapper() {
            return mock(AudioFileMapper.class);
        }

        @Bean
        AudioAnalysisTaskService audioAnalysisTaskService(
                AudioAnalysisTaskMapper taskMapper,
                AudioFileMapper fileMapper,
                AudioAnalysisTaskDispatchOutboxService dispatchOutbox,
                AnalysisProperties properties) {
            return new AudioAnalysisTaskServiceImpl(
                    taskMapper, mock(AudioAnalysisResultMapper.class),
                    fileMapper, mock(ApplicationEventPublisher.class),
                    mock(LoudnessEvaluator.class),
                    mock(AudioResourceOwnershipService.class),
                    dispatchOutbox, properties);
        }
    }
}
