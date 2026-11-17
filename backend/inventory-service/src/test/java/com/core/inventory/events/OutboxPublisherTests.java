package com.core.inventory.events;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.mockito.Mockito.*;

class OutboxPublisherTests {
    @Test
    void removesOnlyAcknowledgedEvents() {
        OutboxRepository repository = mock(OutboxRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        var event = new OutboxEvent("event-id", "events", "42", "{}", Instant.now());
        when(repository.findTop100ByOrderByCreatedAtAscIdAsc()).thenReturn(List.of(event));
        when(kafka.send("events", "42", "{}")).thenReturn(CompletableFuture.completedFuture(null));
        new OutboxPublisher(repository, kafka).publishPending();
        verify(repository).deleteById("event-id");
    }

    @Test
    void brokerFailureKeepsEventsQueuedForRetry() {
        OutboxRepository repository = mock(OutboxRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        var event = new OutboxEvent("event-id", "events", "42", "{}", Instant.now());
        when(repository.findTop100ByOrderByCreatedAtAscIdAsc()).thenReturn(List.of(event));
        when(kafka.send("events", "42", "{}")).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Broker down")));
        new OutboxPublisher(repository, kafka).publishPending();
        verify(repository, never()).deleteById(anyString());
    }
}
