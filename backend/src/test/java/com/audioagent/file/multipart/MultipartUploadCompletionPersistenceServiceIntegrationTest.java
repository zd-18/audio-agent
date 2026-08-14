package com.audioagent.file.multipart;

import com.audioagent.common.enums.FileRole;
import com.audioagent.common.enums.FileStatus;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.outbox.entity.OutboxEvent;
import com.audioagent.outbox.mapper.OutboxEventMapper;
import com.audioagent.outbox.model.OutboxEventStatus;
import com.audioagent.outbox.payload.OutboxPayloadCodec;
import com.audioagent.outbox.publisher.OutboxPublisher;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringJUnitConfig(
        MultipartUploadCompletionPersistenceServiceIntegrationTest.Config.class)
class MultipartUploadCompletionPersistenceServiceIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private MultipartUploadCompletionPersistenceService service;
    @org.springframework.beans.factory.annotation.Autowired
    private JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired
    private TestOutboxPublisher publisher;

    @BeforeEach
    void setUpSchema() {
        publisher.setFail(false);
        jdbc.execute("DROP TABLE IF EXISTS outbox_event");
        jdbc.execute("DROP TABLE IF EXISTS audio_file");
        jdbc.execute("""
                CREATE TABLE audio_file (
                    id BIGINT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    source_file_id BIGINT NULL,
                    root_audio_file_id BIGINT NULL,
                    version_no INT NOT NULL DEFAULT 0,
                    version_summary VARCHAR(200),
                    source_execution_id BIGINT NULL,
                    file_role INT NOT NULL,
                    original_name VARCHAR(255) NOT NULL,
                    extension VARCHAR(20),
                    mime_type VARCHAR(100),
                    bucket_name VARCHAR(100) NOT NULL,
                    object_key VARCHAR(512) NOT NULL,
                    size_bytes BIGINT NOT NULL,
                    sha256 CHAR(64),
                    duration_ms BIGINT,
                    sample_rate INT,
                    channels INT,
                    bit_rate INT,
                    file_status INT NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    deleted INT NOT NULL,
                    UNIQUE (bucket_name, object_key)
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
                    next_retry_at TIMESTAMP NULL,
                    locked_at TIMESTAMP NULL,
                    lock_owner VARCHAR(64) NULL,
                    last_error VARCHAR(1000) NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    published_at TIMESTAMP NULL,
                    UNIQUE (aggregate_type, aggregate_id, event_type)
                )
                """);
    }

    @Test
    void audioFileAndOutboxCommitInSameTransaction() {
        service.persistCompletedUpload(audioFile(101L));

        assertEquals(1, count("audio_file"));
        assertEquals(1, count("outbox_event"));
        assertEquals("AUDIO_FILE_UPLOADED", jdbc.queryForObject(
                "SELECT event_type FROM outbox_event", String.class));
        assertEquals("{\"audioFileId\":101,\"userId\":7,\"eventVersion\":1}",
                jdbc.queryForObject(
                        "SELECT payload FROM outbox_event", String.class));
    }

    @Test
    void outboxFailureRollsBackAudioFile() {
        publisher.setFail(true);

        assertThrows(IllegalStateException.class,
                () -> service.persistCompletedUpload(audioFile(102L)));

        assertEquals(0, count("audio_file"));
        assertEquals(0, count("outbox_event"));
    }

    @Test
    void repeatedCompletionDoesNotCreateAnotherFileOrEvent() {
        service.persistCompletedUpload(audioFile(103L));
        service.persistCompletedUpload(audioFile(104L));

        assertEquals(1, count("audio_file"));
        assertEquals(1, count("outbox_event"));
    }

    private int count(String table) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private AudioFile audioFile(Long id) {
        LocalDateTime now = LocalDateTime.now();
        AudioFile file = new AudioFile();
        file.setId(id);
        file.setUserId(7L);
        file.setFileRole(FileRole.ORIGINAL);
        file.setOriginalName("meeting.wav");
        file.setExtension("wav");
        file.setMimeType("audio/wav");
        file.setBucketName("audio-agent");
        file.setObjectKey("original/7/meeting.wav");
        file.setSizeBytes(512L);
        file.setSha256("a".repeat(64));
        file.setDurationMs(1_000L);
        file.setFileStatus(FileStatus.AVAILABLE);
        file.setCreatedAt(now);
        file.setUpdatedAt(now);
        file.setDeleted(0);
        return file;
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {

        @Bean
        DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:upload-completion;MODE=MySQL;"
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
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) {
            Environment environment = new Environment(
                    "test", new SpringManagedTransactionFactory(), dataSource);
            MybatisConfiguration configuration =
                    new MybatisConfiguration(environment);
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(AudioFileMapper.class);
            configuration.addMapper(OutboxEventMapper.class);
            return new MybatisSqlSessionFactoryBuilder().build(configuration);
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(
                SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory);
        }

        @Bean
        AudioFileMapper audioFileMapper(SqlSessionTemplate template) {
            return template.getMapper(AudioFileMapper.class);
        }

        @Bean
        OutboxEventMapper outboxEventMapper(SqlSessionTemplate template) {
            return template.getMapper(OutboxEventMapper.class);
        }

        @Bean
        TestOutboxPublisher outboxPublisher(OutboxEventMapper mapper) {
            return new TestOutboxPublisher(mapper,
                    new OutboxPayloadCodec(new ObjectMapper()));
        }

        @Bean
        MultipartUploadCompletionPersistenceService completionService(
                AudioFileMapper audioFileMapper,
                TestOutboxPublisher publisher) {
            return new MultipartUploadCompletionPersistenceService(
                    audioFileMapper, publisher);
        }
    }

    static final class TestOutboxPublisher implements OutboxPublisher {
        private final OutboxEventMapper mapper;
        private final OutboxPayloadCodec codec;
        private final AtomicLong ids = new AtomicLong(900L);
        private boolean fail;

        TestOutboxPublisher(OutboxEventMapper mapper,
                            OutboxPayloadCodec codec) {
            this.mapper = mapper;
            this.codec = codec;
        }

        void setFail(boolean fail) {
            this.fail = fail;
        }

        @Override
        public OutboxEvent publish(String aggregateType, String aggregateId,
                                   String eventType, Object payload) {
            if (fail) {
                throw new IllegalStateException("simulated outbox failure");
            }
            OutboxEvent existing = mapper.selectByAggregateAndType(
                    aggregateType, aggregateId, eventType);
            if (existing != null) {
                return existing;
            }
            LocalDateTime now = LocalDateTime.now();
            OutboxEvent event = new OutboxEvent();
            event.setId(ids.incrementAndGet());
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(codec.serialize(payload));
            event.setStatus(OutboxEventStatus.PENDING);
            event.setRetryCount(0);
            event.setCreatedAt(now);
            event.setUpdatedAt(now);
            mapper.insert(event);
            return event;
        }
    }
}
