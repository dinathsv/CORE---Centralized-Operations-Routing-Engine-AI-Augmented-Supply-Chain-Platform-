package com.core.orders;

import com.core.orders.events.OutboxRepository;
import com.core.orders.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrdersServiceApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired OrderRepository repository;
    @Autowired OutboxRepository outbox;
    @Autowired ObjectMapper mapper;
    private static final String ORDER = """
            {"customerName":"Acme","sku":"CORE-001","quantity":2}
            """;

    @BeforeEach
    void clean() { outbox.deleteAll(); repository.deleteAll(); }

    private long create() throws Exception {
        String body = mvc.perform(post("/orders").contentType("application/json").content(ORDER))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.endsWith("Z")))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asLong();
    }

    @Test
    void orderLifecycleEmitsPersistentEventsAndCannotReverseDelivery() throws Exception {
        long id = create();
        for (String status : new String[]{"CONFIRMED", "SHIPPED", "DELIVERED"}) {
            mvc.perform(patch("/orders/" + id + "/status").param("status", status))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(status));
        }
        mvc.perform(delete("/orders/" + id)).andExpect(status().isConflict());
        mvc.perform(patch("/orders/" + id + "/status").param("status", "PENDING")).andExpect(status().isConflict());
        mvc.perform(get("/orders/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"));
        mvc.perform(get("/orders")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        assertThat(outbox.count()).isEqualTo(4);
        for (var row : outbox.findAll()) {
            var event = mapper.readTree(row.getPayload());
            assertThat(event.get("eventId").asText()).isNotBlank();
            assertThat(event.get("entityId").asLong()).isEqualTo(id);
            assertThat(event.get("data").get("sku").asText()).isEqualTo("CORE-001");
        }
    }

    @Test
    void cannotSkipStatesAndRepeatedStatusDoesNotEmitDuplicateEvent() throws Exception {
        long id = create();
        mvc.perform(patch("/orders/" + id + "/status").param("status", "SHIPPED")).andExpect(status().isConflict());
        mvc.perform(patch("/orders/" + id + "/status").param("status", "DELIVERED")).andExpect(status().isConflict());
        mvc.perform(patch("/orders/" + id + "/status").param("status", "PENDING")).andExpect(status().isOk());
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void pendingAndConfirmedOrdersCanBeCancelledIdempotently() throws Exception {
        long pending = create();
        long confirmed = create();
        mvc.perform(patch("/orders/" + confirmed + "/status").param("status", "CONFIRMED")).andExpect(status().isOk());
        for (long id : new long[]{pending, confirmed}) {
            mvc.perform(delete("/orders/" + id)).andExpect(status().isNoContent());
            mvc.perform(delete("/orders/" + id)).andExpect(status().isNoContent());
            mvc.perform(get("/orders/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
            mvc.perform(patch("/orders/" + id + "/status").param("status", "CONFIRMED")).andExpect(status().isConflict());
        }
        assertThat(outbox.count()).isEqualTo(5);
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void shippedOrdersCannotBeCancelled() throws Exception {
        long id = create();
        mvc.perform(patch("/orders/" + id + "/status").param("status", "CONFIRMED")).andExpect(status().isOk());
        mvc.perform(patch("/orders/" + id + "/status").param("status", "SHIPPED")).andExpect(status().isOk());
        mvc.perform(delete("/orders/" + id)).andExpect(status().isConflict());
    }

    @Test
    void validatesRequiredFieldsQuantityAndEnum() throws Exception {
        for (String body : new String[]{"{}", ORDER.replace("2", "0"), ORDER.replace("2", "1.5"), ORDER.replace("2", "-1"), ORDER.replace("Acme", " ")}) {
            mvc.perform(post("/orders").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        long id = create();
        mvc.perform(patch("/orders/" + id + "/status").param("status", "INVALID")).andExpect(status().isBadRequest());
        mvc.perform(patch("/orders/" + id + "/status")).andExpect(status().isBadRequest());
        mvc.perform(get("/orders/999999")).andExpect(status().isNotFound());
        mvc.perform(delete("/orders/999999")).andExpect(status().isNotFound());
    }

    @Test
    void clientCannotChooseIdentityStatusOrTimestamp() throws Exception {
        String malicious = ORDER.replace("{", "{\"id\":99999,\"status\":\"DELIVERED\",\"createdAt\":\"2000-01-01T00:00:00\",");
        mvc.perform(post("/orders").contentType("application/json").content(malicious))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.not("2000-01-01T00:00:00")));
        assertThat(repository.existsById(99999L)).isFalse();
    }
}
