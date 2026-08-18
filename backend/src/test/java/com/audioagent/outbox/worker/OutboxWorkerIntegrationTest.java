package com.audioagent.outbox.worker;

import com.audioagent.outbox.config.OutboxProperties;
import com.audioagent.outbox.entity.OutboxEvent;
import com.audioagent.outbox.mapper.OutboxEventMapper;
import com.audioagent.outbox.model.OutboxEventStatus;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxWorkerIntegrationTest {

    private OutboxEventMapper mapper;
    private JdbcTemplate jdbc;
    private OutboxProperties properties;
    private StubSender sender;
    private OutboxWorker worker;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:outbox;MODE=MySQL;"
                + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS audio_analysis_task");
        jdbc.execute("DROP TABLE IF EXISTS outbox_event");
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
                    published_at TIMESTAMP NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE audio_analysis_task (
                    id BIGINT PRIMARY KEY,
                    status VARCHAR(20) NOT NULL
                )
                """);

        Environment environment = new Environment(
                "test", new SpringManagedTransactionFactory(), dataSource);
        MybatisConfiguration configuration =
                new MybatisConfiguration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(OutboxEventMapper.class);
        SqlSessionFactory factory =
                new MybatisSqlSessionFactoryBuilder().build(configuration);
        mapper = new SqlSessionTemplate(factory)
                .getMapper(OutboxEventMapper.class);

        properties = new OutboxProperties();
        properties.setBatchSize(10);
        properties.setMaxRetryCount(3);
        properties.setInitialRetryDelayMs(100);
        properties.setMaxRetryDelayMs(1_000);
        properties.setConfirmTimeoutMs(5_000);
        properties.setLockTimeoutMs(60_000);
        sender = new StubSender();
        worker = new OutboxWorker(mapper, sender, properties, "worker-a");
    }

    @Test
    void publishesPendingEventNormally() {
        insertPending(1L);
        sender.enqueueCompleted(OutboxBrokerConfirmation.ack());

        worker.scanAndPublish();

        OutboxEvent saved = mapper.selectById(1L);
        assertEquals(OutboxEventStatus.PUBLISHED, saved.getStatus());
        assertNotNull(saved.getPublishedAt());
        assertEquals(List.of(1L), sender.sentIds());
    }

    @Test
    void retriesAfterMqFailureThenPublishes() {
        insertPending(2L);
        sender.enqueueFailure(new IllegalStateException("broker down"));

        worker.scanAndPublish();

        OutboxEvent failedAttempt = mapper.selectById(2L);
        assertEquals(OutboxEventStatus.PENDING,
                failedAttempt.getStatus());
        assertEquals(1, failedAttempt.getRetryCount());
        assertNotNull(failedAttempt.getNextRetryAt());
        assertTrue(failedAttempt.getLastError().contains("broker down"));

        makeRetryDue(2L);
        sender.enqueueCompleted(OutboxBrokerConfirmation.ack());
        worker.scanAndPublish();

        assertEquals(OutboxEventStatus.PUBLISHED,
                mapper.selectById(2L).getStatus());
        assertEquals(List.of(2L, 2L), sender.sentIds());
    }

    @Test
    void updatesStatusOnlyAfterBrokerConfirm() {
        insertPending(3L);
        CompletableFuture<OutboxBrokerConfirmation> confirm =
                new CompletableFuture<>();
        sender.enqueue(confirm);

        worker.scanAndPublish();

        assertEquals(OutboxEventStatus.PROCESSING,
                mapper.selectById(3L).getStatus());
        confirm.complete(OutboxBrokerConfirmation.ack());
        assertEquals(OutboxEventStatus.PUBLISHED,
                mapper.selectById(3L).getStatus());
    }

    @Test
    void repeatedScanDoesNotRepublishPublishedEvent() {
        insertPending(4L);
        sender.enqueueCompleted(OutboxBrokerConfirmation.ack());

        worker.scanAndPublish();
        worker.scanAndPublish();

        assertEquals(OutboxEventStatus.PUBLISHED,
                mapper.selectById(4L).getStatus());
        assertEquals(List.of(4L), sender.sentIds());
    }

    @Test
    void marksFailedAfterMaximumRetryCountAndRetainsEvent() {
        insertPending(5L);
        for (int attempt = 0; attempt < 3; attempt++) {
            sender.enqueueCompleted(
                    OutboxBrokerConfirmation.nack("nack-" + attempt));
            worker.scanAndPublish();
            if (attempt < 2) {
                makeRetryDue(5L);
            }
        }

        OutboxEvent saved = mapper.selectById(5L);
        assertNotNull(saved);
        assertEquals(OutboxEventStatus.FAILED, saved.getStatus());
        assertEquals(3, saved.getRetryCount());
        assertEquals("nack-2", saved.getLastError());
        assertNull(saved.getNextRetryAt());

        worker.scanAndPublish();
        assertEquals(List.of(5L, 5L, 5L), sender.sentIds());
    }

    @Test
    void manualRetryCasClearsFailureAndStartsNewRetryCycle() {
        insertPending(7L);
        LocalDateTime lockedAt = LocalDateTime.now().minusMinutes(2);
        jdbc.update("""
                        UPDATE outbox_event
                        SET status = 'FAILED', retry_count = 5,
                            lock_owner = 'old-worker', locked_at = ?,
                            last_error = 'broker unavailable',
                            next_retry_at = NULL
                        WHERE id = 7
                        """, lockedAt);

        int updated = mapper.retryFailed(7L, LocalDateTime.now());

        assertEquals(1, updated);
        OutboxEvent retried = mapper.selectById(7L);
        assertEquals(OutboxEventStatus.PENDING, retried.getStatus());
        assertEquals(0, retried.getRetryCount());
        assertNull(retried.getLockOwner());
        assertNull(retried.getLockedAt());
        assertNull(retried.getLastError());
        assertNotNull(retried.getNextRetryAt());
    }

    @Test
    void manualRetryCasCannotTakePublishedOrProcessingEvents() {
        insertPending(8L);
        insertPending(9L);
        jdbc.update("UPDATE outbox_event SET status = 'PUBLISHED' "
                + "WHERE id = 8");
        jdbc.update("UPDATE outbox_event SET status = 'PROCESSING' "
                + "WHERE id = 9");

        assertEquals(0, mapper.retryFailed(8L, LocalDateTime.now()));
        assertEquals(0, mapper.retryFailed(9L, LocalDateTime.now()));
        assertEquals(OutboxEventStatus.PUBLISHED,
                mapper.selectById(8L).getStatus());
        assertEquals(OutboxEventStatus.PROCESSING,
                mapper.selectById(9L).getStatus());
    }

    @Test
    void twoWorkersCannotClaimSameEventConcurrently() {
        insertPending(6L);
        CompletableFuture<OutboxBrokerConfirmation> confirm =
                new CompletableFuture<>();
        sender.enqueue(confirm);
        OutboxWorker second = new OutboxWorker(
                mapper, sender, properties, "worker-b");

        worker.scanAndPublish();
        second.scanAndPublish();

        assertEquals(List.of(6L), sender.sentIds());
        assertEquals("worker-a", mapper.selectById(6L).getLockOwner());
        confirm.complete(OutboxBrokerConfirmation.ack());
        assertEquals(OutboxEventStatus.PUBLISHED,
                mapper.selectById(6L).getStatus());
    }

    @Test
    void analysisDispatchAckMarksOutboxPublishedAndLeavesTaskPending() {
        insertAnalysisDispatch(31L, 10L);
        sender.enqueueCompleted(OutboxBrokerConfirmation.ack());

        worker.scanAndPublish();

        assertEquals(OutboxEventStatus.PUBLISHED,
                mapper.selectById(10L).getStatus());
        assertEquals("PENDING", taskStatus(31L));
    }

    @Test
    void analysisDispatchNackSchedulesOutboxRetryAndLeavesTaskPending() {
        insertAnalysisDispatch(32L, 11L);
        sender.enqueueCompleted(
                OutboxBrokerConfirmation.nack("returned unroutable"));

        worker.scanAndPublish();

        OutboxEvent event = mapper.selectById(11L);
        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertEquals(1, event.getRetryCount());
        assertNotNull(event.getNextRetryAt());
        assertEquals("PENDING", taskStatus(32L));
    }

    @Test
    void lostConfirmIsResentByRestartedWorkerWithoutChangingTask() {
        insertAnalysisDispatch(33L, 12L);
        sender.enqueue(CompletableFuture.failedFuture(
                new TimeoutException("confirm lost")));

        worker.scanAndPublish();

        assertEquals(OutboxEventStatus.PENDING,
                mapper.selectById(12L).getStatus());
        makeRetryDue(12L);
        sender.enqueueCompleted(OutboxBrokerConfirmation.ack());
        OutboxWorker restarted = new OutboxWorker(
                mapper, sender, properties, "worker-after-restart");
        restarted.scanAndPublish();

        assertEquals(List.of(12L, 12L), sender.sentIds());
        assertEquals(OutboxEventStatus.PUBLISHED,
                mapper.selectById(12L).getStatus());
        assertEquals("PENDING", taskStatus(33L));
    }

    private void insertPending(Long id) {
        LocalDateTime now = LocalDateTime.now().minusSeconds(1);
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        event.setAggregateType("TestAggregate");
        event.setAggregateId("aggregate-" + id);
        event.setEventType("test.created");
        event.setPayload("{\"value\":" + id + "}");
        event.setStatus(OutboxEventStatus.PENDING);
        event.setRetryCount(0);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        mapper.insert(event);
    }

    private void makeRetryDue(Long id) {
        jdbc.update("UPDATE outbox_event SET next_retry_at = ? WHERE id = ?",
                LocalDateTime.now().minusSeconds(1), id);
    }

    private void insertAnalysisDispatch(Long taskId, Long eventId) {
        jdbc.update("INSERT INTO audio_analysis_task (id, status) "
                + "VALUES (?, 'PENDING')", taskId);
        LocalDateTime now = LocalDateTime.now().minusSeconds(1);
        OutboxEvent event = new OutboxEvent();
        event.setId(eventId);
        event.setAggregateType("AUDIO_ANALYSIS_TASK");
        event.setAggregateId(taskId.toString());
        event.setEventType("AUDIO_ANALYSIS_TASK_CREATED");
        event.setPayload("{\"taskId\":" + taskId + "}");
        event.setStatus(OutboxEventStatus.PENDING);
        event.setRetryCount(0);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        mapper.insert(event);
    }

    private String taskStatus(Long taskId) {
        return jdbc.queryForObject(
                "SELECT status FROM audio_analysis_task WHERE id = ?",
                String.class, taskId);
    }

    private static final class StubSender implements OutboxMessageSender {
        private final Queue<CompletableFuture<OutboxBrokerConfirmation>>
                outcomes = new ArrayDeque<>();
        private final List<Long> sentIds = new ArrayList<>();

        void enqueue(CompletableFuture<OutboxBrokerConfirmation> result) {
            outcomes.add(result);
        }

        void enqueueCompleted(OutboxBrokerConfirmation result) {
            enqueue(CompletableFuture.completedFuture(result));
        }

        void enqueueFailure(RuntimeException failure) {
            enqueue(CompletableFuture.failedFuture(failure));
        }

        List<Long> sentIds() {
            return List.copyOf(sentIds);
        }

        @Override
        public CompletableFuture<OutboxBrokerConfirmation> send(
                OutboxEvent event) {
            sentIds.add(event.getId());
            CompletableFuture<OutboxBrokerConfirmation> result =
                    outcomes.poll();
            if (result == null) {
                throw new AssertionError("No sender outcome configured");
            }
            return result;
        }
    }
}
