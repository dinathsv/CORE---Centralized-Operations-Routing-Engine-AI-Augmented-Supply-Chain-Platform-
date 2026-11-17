package com.core.orders.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "core.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    // At-least-once delivery: consumers must deduplicate by eventId. A failed send
    // leaves its row in PostgreSQL and retries on the next poll, including after restart.
    @Scheduled(fixedDelayString = "${core.outbox.poll-interval-ms:2000}")
    public void publishPending() {
        for (OutboxEvent event : repository.findTop100ByOrderByCreatedAtAscIdAsc()) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload())
                        .get(15, TimeUnit.SECONDS);
                repository.deleteById(event.getId());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                log.warn("Event {} remains queued for retry: {}", event.getId(), exception.toString());
                return;
            }
        }
    }
}
