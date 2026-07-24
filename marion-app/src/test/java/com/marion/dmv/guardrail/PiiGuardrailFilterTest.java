package com.marion.dmv.guardrail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.POST;

class PiiGuardrailFilterTest {

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        // Stub handler that echoes 200 OK — if filter passes, we reach here
        var router = RouterFunctions.route(POST("/api/transfer/query"),
                req -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .bodyValue("ok"));

        client = WebTestClient
                .bindToRouterFunction(router)
                .webFilter(new PiiGuardrailFilter())
                .build();
    }

    @Test
    void cleanRequest_passesThrough() {
        client.post()
                .uri("/api/transfer/query")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"question":"What docs for a Crestwood paper transfer?",\
                        "vehicleVin":"1HGBH41JXMN109186","originState":"Crestwood"}""")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void ssnInBody_returns400() {
        client.post()
                .uri("/api/transfer/query")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"question":"My SSN is 078-05-1120. What docs do I need?",\
                        "originState":"Crestwood"}""")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("PII_DETECTED")
                .jsonPath("$.piiType").isEqualTo("SSN")
                // Must NOT echo the SSN back in the response
                .jsonPath("$.message").value((String msg) ->
                        org.junit.jupiter.api.Assertions.assertFalse(msg.contains("078-05-1120")));
    }

    @Test
    void visaCreditCard_returns400() {
        client.post()
                .uri("/api/transfer/query")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"question":"My credit card is 4111 1111 1111 1111, paid for this vehicle"}""")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("PII_DETECTED")
                .jsonPath("$.piiType").isEqualTo("CREDIT_CARD_VISA");
    }

    @Test
    void vinNumber_notBlocked() {
        // VINs are 17 alphanumeric chars — must not trigger credit card pattern
        client.post()
                .uri("/api/transfer/query")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"question":"Transfer for VIN 1HGBH41JXMN109186","vehicleVin":"1HGBH41JXMN109186"}""")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void nonTransferPath_notFiltered() {
        // Unrelated path should bypass the guard entirely
        var router = RouterFunctions.route(POST("/api/ingest/reset"),
                req -> ServerResponse.ok().bodyValue("ok"));
        var c = WebTestClient
                .bindToRouterFunction(router)
                .webFilter(new PiiGuardrailFilter())
                .build();

        c.post()
                .uri("/api/ingest/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"question":"SSN 123-45-6789 should not be checked on this path"}""")
                .exchange()
                .expectStatus().isOk();
    }
}
