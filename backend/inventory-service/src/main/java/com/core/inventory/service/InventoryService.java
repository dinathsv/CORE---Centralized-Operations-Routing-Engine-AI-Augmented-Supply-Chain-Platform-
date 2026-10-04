package com.core.inventory.service;

import com.core.inventory.model.InventoryItem;
import com.core.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public List<InventoryItem> findAll() {
        return repository.findAll();
    }

    public InventoryItem findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Item not found: " + id));
    }

    public InventoryItem create(InventoryItem item) {
        InventoryItem saved = repository.save(item);
        kafkaTemplate.send("inventory-events", "CREATED:" + saved.getSku());
        return saved;
    }

    public InventoryItem update(Long id, InventoryItem item) {
        InventoryItem existing = findById(id);
        existing.setName(item.getName());
        existing.setDescription(item.getDescription());
        existing.setQuantity(item.getQuantity());
        existing.setPrice(item.getPrice());
        InventoryItem saved = repository.save(existing);
        kafkaTemplate.send("inventory-events", "UPDATED:" + saved.getSku());
        return saved;
    }

    public void delete(Long id) {
        InventoryItem item = findById(id);
        repository.deleteById(id);
        kafkaTemplate.send("inventory-events", "DELETED:" + item.getSku());
    }
}
