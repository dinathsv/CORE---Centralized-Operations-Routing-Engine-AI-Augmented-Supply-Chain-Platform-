package com.core.inventory.events;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "outbox_events", indexes = @Index(name = "outbox_created_at", columnList = "createdAt"))
public class OutboxEvent {
    @Id
    private String id;
    @Column(nullable = false)
    private String topic;
    @Column(nullable = false)
    private String aggregateId;
    @Column(nullable = false, columnDefinition = "text")
    private String payload;
    @Column(nullable = false)
    private Instant createdAt;

    public OutboxEvent() {
    }

    public OutboxEvent(String id, String topic, String aggregateId, String payload, Instant createdAt) {
        this.id = id;
        this.topic = topic;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
