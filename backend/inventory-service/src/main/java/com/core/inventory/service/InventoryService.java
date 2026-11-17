package com.core.inventory.service;

import com.core.inventory.api.InventoryRequest;
import com.core.inventory.events.EventOutbox;
import com.core.inventory.model.InventoryItem;
import com.core.inventory.repository.InventoryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class InventoryService {
    private final InventoryRepository repository;
    private final EventOutbox eventOutbox;

    public InventoryService(InventoryRepository repository, EventOutbox eventOutbox) {
        this.repository = repository;
        this.eventOutbox = eventOutbox;
    }

    public List<InventoryItem> findAll() {
        return repository.findAll(Sort.by("id").descending());
    }

    public InventoryItem findById(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found: " + id));
    }

    @Transactional
    public InventoryItem create(InventoryRequest request) {
        String sku = request.sku().trim();
        if (repository.findBySku(sku).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An item with SKU " + sku + " already exists.");
        }
        InventoryItem item = new InventoryItem();
        item.setSku(sku);
        item.setName(request.name().trim());
        item.setDescription(request.description());
        item.setQuantity(request.quantity());
        item.setPrice(request.price());
        InventoryItem saved = repository.saveAndFlush(item);
        eventOutbox.enqueue("CREATED", "inventory", saved.getId(), saved);
        return saved;
    }

    @Transactional
    public InventoryItem update(Long id, InventoryRequest request) {
        InventoryItem item = findById(id);
        String sku = request.sku().trim();
        repository.findBySku(sku).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An item with SKU " + sku + " already exists.");
        });
        item.setSku(sku);
        item.setName(request.name().trim());
        item.setDescription(request.description());
        item.setQuantity(request.quantity());
        item.setPrice(request.price());
        InventoryItem saved = repository.saveAndFlush(item);
        eventOutbox.enqueue("UPDATED", "inventory", saved.getId(), saved);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        InventoryItem item = findById(id);
        eventOutbox.enqueue("DELETED", "inventory", item.getId(), item);
        repository.delete(item);
    }
}
