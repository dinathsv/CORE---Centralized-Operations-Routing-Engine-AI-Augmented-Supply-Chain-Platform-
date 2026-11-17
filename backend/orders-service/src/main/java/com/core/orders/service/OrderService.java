package com.core.orders.service;

import com.core.orders.api.OrderRequest;
import com.core.orders.events.EventOutbox;
import com.core.orders.model.Order;
import com.core.orders.model.Order.OrderStatus;
import com.core.orders.repository.OrderRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrderService {
    private final OrderRepository repository;
    private final EventOutbox eventOutbox;

    public OrderService(OrderRepository repository, EventOutbox eventOutbox) {
        this.repository = repository;
        this.eventOutbox = eventOutbox;
    }

    public List<Order> findAll() {
        return repository.findAll(Sort.by("createdAt").descending().and(Sort.by("id").descending()));
    }

    public Order findById(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
    }

    @Transactional
    public Order create(OrderRequest request) {
        Order order = new Order();
        order.setCustomerName(request.customerName().trim());
        order.setSku(request.sku().trim());
        order.setQuantity(request.quantity());
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(Instant.now());
        Order saved = repository.saveAndFlush(order);
        eventOutbox.enqueue("CREATED", "order", saved.getId(), saved);
        return saved;
    }

    @Transactional
    public Order updateStatus(Long id, OrderStatus status) {
        Order order = findById(id);
        if (order.getStatus() == status) return order;
        boolean allowed = switch (order.getStatus()) {
            case PENDING -> status == OrderStatus.CONFIRMED || status == OrderStatus.CANCELLED;
            case CONFIRMED -> status == OrderStatus.SHIPPED || status == OrderStatus.CANCELLED;
            case SHIPPED -> status == OrderStatus.DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot move an order from " + order.getStatus() + " to " + status + ".");
        }
        order.setStatus(status);
        Order saved = repository.saveAndFlush(order);
        eventOutbox.enqueue(status == OrderStatus.CANCELLED ? "CANCELLED" : "STATUS_CHANGED", "order", saved.getId(), saved);
        return saved;
    }

    @Transactional
    public void cancel(Long id) {
        updateStatus(id, OrderStatus.CANCELLED);
    }
}
