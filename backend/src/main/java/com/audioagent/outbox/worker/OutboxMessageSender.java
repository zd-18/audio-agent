package com.audioagent.outbox.worker;

import com.audioagent.outbox.entity.OutboxEvent;

import java.util.concurrent.CompletableFuture;

public interface OutboxMessageSender {

    CompletableFuture<OutboxBrokerConfirmation> send(OutboxEvent event);
}
