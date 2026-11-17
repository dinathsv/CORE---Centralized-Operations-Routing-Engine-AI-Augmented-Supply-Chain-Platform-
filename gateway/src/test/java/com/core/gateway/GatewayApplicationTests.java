package com.core.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayApplicationTests {
    private static final DisposableServer UPSTREAM = HttpServer.create()
            .host("127.0.0.1").port(0)
            .handle((request, response) -> response.header("Content-Type", "application/json")
                    .sendString(Mono.just("{\"path\":\"" + request.uri()
                            + "\",\"method\":\"" + request.method().name()
                            + "\",\"authenticatedUpstream\":"
                            + request.requestHeaders().contains("Authorization") + "}")))
            .bindNow();

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + UPSTREAM.port();
        registry.add("INVENTORY_SERVICE_URL", () -> url);
        registry.add("ORDERS_SERVICE_URL", () -> url);
        registry.add("AI_SERVICE_URL", () -> url);
        registry.add("CORE_ADMIN_USERNAME", () -> "test-admin");
        registry.add("CORE_ADMIN_PASSWORD", () -> "test-password");
    }

    @AfterAll
    static void stopServer() {
        UPSTREAM.disposeNow();
    }

    @Test
    void healthIsPublicButApisRequireCredentials() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
        client.get().uri("/api/inventory").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/orders").headers(h -> h.setBasicAuth("test-admin", "wrong"))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void inventoryPathIsStrippedOnceAndCredentialsStayAtGateway() {
        client.get().uri("/api/inventory/42")
                .headers(h -> h.setBasicAuth("test-admin", "test-password"))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.path").isEqualTo("/inventory/42")
                .jsonPath("$.authenticatedUpstream").isEqualTo(false);
    }

    @Test
    void inventoryCollectionPathNeedsNoRepeatedSegment() {
        client.get().uri("/api/inventory")
                .headers(h -> h.setBasicAuth("test-admin", "test-password"))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.path").isEqualTo("/inventory");
    }

    @Test
    void ordersPreservesMutationAndQuery() {
        client.patch().uri("/api/orders/9/status?status=CONFIRMED")
                .headers(h -> h.setBasicAuth("test-admin", "test-password"))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.path").isEqualTo("/orders/9/status?status=CONFIRMED")
                .jsonPath("$.method").isEqualTo("PATCH");
    }

    @Test
    void aiPathStripsBothApiAndAiSegments() {
        client.post().uri("/api/ai/query")
                .headers(h -> h.setBasicAuth("test-admin", "test-password"))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.path").isEqualTo("/query");
    }
}
