package com.audioagent.outbox.worker;

public record OutboxBrokerConfirmation(boolean acknowledged, String reason) {

    public static OutboxBrokerConfirmation ack() {
        return new OutboxBrokerConfirmation(true, null);
    }

    public static OutboxBrokerConfirmation nack(String reason) {
        return new OutboxBrokerConfirmation(false, reason);
    }
}
