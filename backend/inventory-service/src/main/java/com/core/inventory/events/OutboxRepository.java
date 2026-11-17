package com.core.inventory.events;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findTop100ByOrderByCreatedAtAscIdAsc();
}
