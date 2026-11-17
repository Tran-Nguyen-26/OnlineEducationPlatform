package com.oep.gateway.config;

import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Idempotency for state-changing calls. A client sends a unique {@code Idempotency-Key}; repeating the same request
 * (network retry, double click, timeout then retry) replays the FIRST response instead of executing again:
 * <ul>
 *   <li>first call: key reserved ("in progress"), request proceeds, the response (status &lt; 500) is stored for 24h;</li>
 *   <li>repeat while the first is still running: 409 {@code IDEMPOTENCY_IN_PROGRESS} + Retry-After;</li>
 *   <li>repeat after completion with the same payload: stored response, header {@code Idempotent-Replay: true};</li>
 *   <li>same key with a different method/path/body: 422 {@code IDEMPOTENCY_KEY_REUSED}.</li>
 * </ul>
 * Server errors (5xx) are never stored so the client can safely retry. State lives in Redis, so every gateway instance agrees.
 */
@Component
public class IdempotencyFilter implements GlobalFilter, Ordered {

    private static final Set<HttpMethod> MUTATING = Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH);
    private static final Duration LOCK_TTL = Duration.ofSeconds(60);
    private static final Duration RESULT_TTL = Duration.ofHours(24);
    private static final int MAX_BODY = 1024 * 1024;

    private final ReactiveStringRedisTemplate redis;

    public IdempotencyFilter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        String idemKey = req.getHeaders().getFirst("Idempotency-Key");
        String path = req.getURI().getPath();
        if (idemKey == null || !MUTATING.contains(req.getMethod()) || !path.startsWith("/api/")) {
            return chain.filter(exchange);
        }
        if (idemKey.length() < 8 || idemKey.length() > 128) {
            return error(exchange, HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID", "Idempotency-Key phải dài 8–128 ký tự", null);
        }
        if (req.getHeaders().getContentLength() > MAX_BODY) {
            return chain.filter(exchange);
        }
        return owner(exchange).flatMap(owner ->
                DataBufferUtils.join(req.getBody()).map(buf -> {
                    byte[] bytes = new byte[buf.readableByteCount()];
                    buf.read(bytes);
                    DataBufferUtils.release(buf);
                    return bytes;
                }).defaultIfEmpty(new byte[0]).flatMap(body -> {
                    String hash = sha256(req.getMethod() + " " + req.getURI().getRawPath() + "?" + req.getURI().getRawQuery() + "\n", body);
                    String redisKey = "oep:idem:" + owner + ":" + idemKey;
                    return redis.opsForValue().setIfAbsent(redisKey, "P:" + hash, LOCK_TTL).flatMap(acquired -> {
                        if (Boolean.TRUE.equals(acquired)) return execute(exchange, chain, body, redisKey, hash);
                        return redis.opsForValue().get(redisKey).defaultIfEmpty("").flatMap(state -> replay(exchange, state, hash));
                    });
                }));
    }

    private Mono<Void> execute(ServerWebExchange exchange, GatewayFilterChain chain, byte[] body, String redisKey, String hash) {
        ServerHttpResponse original = exchange.getResponse();
        DataBufferFactory bf = original.bufferFactory();
        AtomicBoolean stored = new AtomicBoolean(false);

        ServerHttpRequest replayableRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public Flux<DataBuffer> getBody() {
                return body.length == 0 ? Flux.empty() : Flux.just(bf.wrap(body));
            }
        };
        ServerHttpResponse capturing = new ServerHttpResponseDecorator(original) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> publisher) {
                return super.writeWith(Flux.from(publisher).collectList().flatMapMany(parts -> {
                    int total = parts.stream().mapToInt(DataBuffer::readableByteCount).sum();
                    byte[] all = new byte[total];
                    int off = 0;
                    for (DataBuffer p : parts) {
                        int n = p.readableByteCount();
                        p.read(all, off, n);
                        off += n;
                        DataBufferUtils.release(p);
                    }
                    int status = getStatusCode() == null ? 200 : getStatusCode().value();
                    String ctype = getHeaders().getContentType() == null ? "" : getHeaders().getContentType().toString();
                    Mono<Boolean> save = (status < 500 && all.length <= MAX_BODY)
                            ? redis.opsForValue().set(redisKey, "D:" + hash + ":" + status + ":" + ctype + ":" + Base64.getEncoder().encodeToString(all), RESULT_TTL)
                            : redis.delete(redisKey).thenReturn(true);
                    stored.set(true);
                    return save.onErrorReturn(false).thenMany(Flux.just(bf.wrap(all)));
                }));
            }
        };
        return chain.filter(exchange.mutate().request(replayableRequest).response(capturing).build())
                .then(Mono.defer(() -> {
                    if (stored.get()) return Mono.empty();
                    int status = original.getStatusCode() == null ? 200 : original.getStatusCode().value();
                    return (status < 500
                            ? redis.opsForValue().set(redisKey, "D:" + hash + ":" + status + "::", RESULT_TTL)
                            : redis.delete(redisKey).thenReturn(true)).onErrorReturn(false).then();
                }))
                .onErrorResume(e -> redis.delete(redisKey).onErrorReturn(0L).then(Mono.error(e)));
    }

    private Mono<Void> replay(ServerWebExchange exchange, String state, String hash) {
        if (state.startsWith("P:")) {
            if (!state.substring(2).equals(hash)) return reused(exchange);
            return error(exchange, HttpStatus.CONFLICT, "IDEMPOTENCY_IN_PROGRESS", "Yêu cầu đầu tiên vẫn đang được xử lý, hãy thử lại sau giây lát", "1");
        }
        if (state.startsWith("D:")) {
            String[] p = state.split(":", 5);
            if (p.length < 5) return error(exchange, HttpStatus.CONFLICT, "IDEMPOTENCY_IN_PROGRESS", "Đang xử lý", "1");
            if (!p[1].equals(hash)) return reused(exchange);
            ServerHttpResponse res = exchange.getResponse();
            res.setStatusCode(HttpStatus.valueOf(Integer.parseInt(p[2])));
            if (!p[3].isEmpty()) res.getHeaders().setContentType(MediaType.parseMediaType(p[3]));
            res.getHeaders().add("Idempotent-Replay", "true");
            byte[] body = p[4].isEmpty() ? new byte[0] : Base64.getDecoder().decode(p[4]);
            return body.length == 0 ? res.setComplete() : res.writeWith(Mono.just(res.bufferFactory().wrap(body)));
        }
        // key expired between the two Redis calls: tell the client to retry
        return error(exchange, HttpStatus.CONFLICT, "IDEMPOTENCY_IN_PROGRESS", "Vui lòng thử lại", "1");
    }

    private Mono<Void> reused(ServerWebExchange exchange) {
        return error(exchange, HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED", "Idempotency-Key này đã được dùng cho một yêu cầu khác", null);
    }

    private Mono<String> owner(ServerWebExchange exchange) {
        return exchange.getPrincipal().ofType(Authentication.class)
                .filter(a -> a instanceof JwtAuthenticationToken)
                .map(a -> ((JwtAuthenticationToken) a).getToken().getSubject())
                .switchIfEmpty(Mono.fromSupplier(() -> exchange.getRequest().getRemoteAddress() == null ? "anon"
                        : exchange.getRequest().getRemoteAddress().getHostString()));
    }

    private Mono<Void> error(ServerWebExchange exchange, HttpStatus status, String code, String message, String retryAfter) {
        ServerHttpResponse res = exchange.getResponse();
        res.setStatusCode(status);
        res.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (retryAfter != null) res.getHeaders().add("Retry-After", retryAfter);
        byte[] b = ("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        return res.writeWith(Mono.just(res.bufferFactory().wrap(b)));
    }

    private static String sha256(String prefix, byte[] body) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            d.update(prefix.getBytes(StandardCharsets.UTF_8));
            d.update(body);
            return HexFormat.of().formatHex(d.digest());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
