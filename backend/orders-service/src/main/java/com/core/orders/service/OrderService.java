package com.core.orders.service;

import com.core.orders.model.Order;
import com.core.orders.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public List<Order> findAll() {
        return repository.findAll();
    }

    public Order findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
    }

    public Order create(Order order) {
        Order saved = repository.save(order);
        kafkaTemplate.send("order-events", "CREATED:" + saved.getId());
        return saved;
    }

    public Order updateStatus(Long id, Order.OrderStatus status) {
        Order order = findById(id);
        order.setStatus(status);
        Order saved = repository.save(order);
        kafkaTemplate.send("order-events", "STATUS_CHANGED:" + saved.getId() + ":" + status);
        return saved;
    }

    public void cancel(Long id) {
        Order order = findById(id);
        order.setStatus(Order.OrderStatus.CANCELLED);
        repository.save(order);
        kafkaTemplate.send("order-events", "CANCELLED:" + order.getId());
    }
}
