package com.core.inventory;

import com.core.inventory.api.InventoryRequest;
import com.core.inventory.events.OutboxRepository;
import com.core.inventory.repository.InventoryRepository;
import com.core.inventory.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryServiceApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired InventoryRepository repository;
    @Autowired OutboxRepository outbox;
    @Autowired InventoryService service;
    @Autowired ObjectMapper mapper;
    @Autowired TransactionTemplate transactionTemplate;
    private static final String ITEM = """
            {"sku":"CORE-001","name":"Widget","description":"A useful widget","quantity":12,"price":19.95}
            """;

    @BeforeEach
    void clean() { outbox.deleteAll(); repository.deleteAll(); }

    @Test
    void createReadUpdateAndDeletePersistStructuredEvents() throws Exception {
        String created = mvc.perform(post("/inventory").contentType("application/json").content(ITEM))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.version").doesNotExist()).andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(created).get("id").asLong();
        mvc.perform(get("/inventory/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.sku").value("CORE-001"));
        mvc.perform(put("/inventory/" + id).contentType("application/json").content(ITEM.replace("CORE-001", "CORE-002").replace("19.95", "20.50")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sku").value("CORE-002")).andExpect(jsonPath("$.price").value(20.5));
        mvc.perform(get("/inventory")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(delete("/inventory/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/inventory/" + id)).andExpect(status().isNotFound()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        assertThat(outbox.count()).isEqualTo(3);
        var event = mapper.readTree(outbox.findAll().get(0).getPayload());
        assertThat(event.get("eventId").asText()).isNotBlank();
        assertThat(event.get("entityType").asText()).isEqualTo("inventory");
        assertThat(event.get("entityId").asLong()).isEqualTo(id);
        assertThat(event.get("data").has("quantity")).isTrue();
        assertThat(repository.count()).isZero();
    }

    @Test
    void invalidAndMissingFieldsReturnBadRequestWithoutEvents() throws Exception {
        for (String invalid : new String[]{ITEM.replace("12", "-1"), ITEM.replace("12", "1.5"), ITEM.replace("19.95", "-1"), ITEM.replace("19.95", "19.999"), ITEM.replace("\"Widget\"", "\" \""), "{}", "null"}) {
            mvc.perform(post("/inventory").contentType("application/json").content(invalid))
                    .andExpect(status().isBadRequest());
        }
        assertThat(repository.count()).isZero();
        assertThat(outbox.count()).isZero();
    }

    @Test
    void duplicateSkuIsConflictAndUpdateCannotReuseAnotherSku() throws Exception {
        var first = service.create(new InventoryRequest("CORE-001", "One", null, 1, BigDecimal.ONE));
        var second = service.create(new InventoryRequest("CORE-002", "Two", null, 1, BigDecimal.ONE));
        mvc.perform(post("/inventory").contentType("application/json").content(ITEM.replace("CORE-001", " CORE-001 ")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        mvc.perform(put("/inventory/" + second.getId()).contentType("application/json").content(ITEM))
                .andExpect(status().isConflict());
        assertThat(service.findById(first.getId()).getSku()).isEqualTo("CORE-001");
        assertThat(outbox.count()).isEqualTo(2);
    }

    @Test
    void clientCannotChooseDatabaseIdentity() throws Exception {
        mvc.perform(post("/inventory").contentType("application/json").content(ITEM.replace("{", "{\"id\":99999,")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(99999)));
        assertThat(repository.existsById(99999L)).isFalse();
    }

    @Test
    void missingAndMalformedRequestsHaveAppropriateStatus() throws Exception {
        mvc.perform(put("/inventory/99999").contentType("application/json").content(ITEM)).andExpect(status().isNotFound());
        mvc.perform(delete("/inventory/99999")).andExpect(status().isNotFound());
        mvc.perform(get("/inventory/no-id")).andExpect(status().isBadRequest());
        mvc.perform(post("/inventory").contentType("application/json").content("broken")).andExpect(status().isBadRequest());
    }

    @Test
    void rollbackRemovesBothDomainChangeAndQueuedEvent() {
        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            service.create(new InventoryRequest("ROLLBACK", "Transient", null, 1, BigDecimal.ONE));
            throw new IllegalStateException("Simulated transaction failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(repository.count()).isZero();
        assertThat(outbox.count()).isZero();
    }
}
