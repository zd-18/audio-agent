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
