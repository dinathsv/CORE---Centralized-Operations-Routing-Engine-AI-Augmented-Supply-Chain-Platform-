package com.core.inventory.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class EventOutbox {
    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;

    public EventOutbox(OutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    // The state mutation and its event must commit in the same database transaction.
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String type, String entityType, Long entityId, Object data) {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventId", eventId, "type", type, "entityType", entityType,
                    "entityId", entityId, "occurredAt", now, "data", data));
            repository.save(new OutboxEvent(eventId, "inventory-events", entityId.toString(), payload, now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize domain event", exception);
        }
    }
}
