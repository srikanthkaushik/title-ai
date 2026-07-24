package com.marion.dmv.guardrail;

import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

@Component
@Order(-100)
public class PiiGuardrailFilter implements WebFilter {

    record PiiMatch(String type, String label) {}

    private static final List<PiiMatch> PII_PATTERNS = List.of(
            new PiiMatch("\\b\\d{3}-\\d{2}-\\d{4}\\b",
                    "SSN"),
            new PiiMatch("\\b4[0-9]{3}[\\s-]?[0-9]{4}[\\s-]?[0-9]{4}[\\s-]?[0-9]{4}\\b",
                    "CREDIT_CARD_VISA"),
            new PiiMatch("\\b5[1-5][0-9]{2}[\\s-]?[0-9]{4}[\\s-]?[0-9]{4}[\\s-]?[0-9]{4}\\b",
                    "CREDIT_CARD_MC"),
            new PiiMatch("\\b3[47][0-9]{2}[\\s-]?[0-9]{6}[\\s-]?[0-9]{5}\\b",
                    "CREDIT_CARD_AMEX")
    );

    private static final List<Pattern> COMPILED = PII_PATTERNS.stream()
            .map(p -> Pattern.compile(p.type(), Pattern.CASE_INSENSITIVE))
            .toList();

    private static final List<String> GUARDED_PREFIXES = List.of("/api/transfer");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (GUARDED_PREFIXES.stream().noneMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        // defaultIfEmpty with a zero-byte buffer so flatMap always fires.
        // Never switchIfEmpty on Mono<Void> — it always completes empty and fires on the happy path.
        DataBuffer empty = exchange.getResponse().bufferFactory().wrap(new byte[0]);

        return DataBufferUtils.join(exchange.getRequest().getBody())
                .defaultIfEmpty(empty)
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    String body = new String(bytes, StandardCharsets.UTF_8);

                    String detected = detectPii(body);
                    if (detected != null) {
                        return rejectWithPiiError(exchange, detected);
                    }

                    // Wrap bytes in Flux.defer so the buffer isn't single-use
                    ServerHttpRequestDecorator cachedRequest = new ServerHttpRequestDecorator(
                            exchange.getRequest()) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return Flux.defer(() -> Flux.just(
                                    exchange.getResponse().bufferFactory().wrap(bytes)));
                        }
                    };

                    return chain.filter(exchange.mutate().request(cachedRequest).build());
                });
    }

    private String detectPii(String body) {
        for (int i = 0; i < COMPILED.size(); i++) {
            if (COMPILED.get(i).matcher(body).find()) {
                return PII_PATTERNS.get(i).label();
            }
        }
        return null;
    }

    private Mono<Void> rejectWithPiiError(ServerWebExchange exchange, String piiType) {
        String json = """
                {"error":"PII_DETECTED","piiType":"%s",\
                "message":"Personal identification numbers may not be submitted in transfer queries. \
                Remove the sensitive data and resubmit."}""".formatted(piiType);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buf = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buf));
    }
}
