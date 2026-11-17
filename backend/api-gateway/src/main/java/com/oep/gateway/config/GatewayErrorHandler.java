package com.oep.gateway.config;

import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * Turns "upstream unreachable / too slow / circuit open" into a clean, retryable 503 (or 504) with Retry-After instead
 * of a raw 500. Everything else falls through to Spring's default error handling.
 */
@Component
@Order(-2) // before DefaultErrorWebExceptionHandler (-1)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GatewayErrorHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatusCode status = classify(ex);
        if (status == null || exchange.getResponse().isCommitted()) return Mono.error(ex);
        log.warn("Upstream unavailable for {} {} -> {}: {}", exchange.getRequest().getMethod(), exchange.getRequest().getURI().getPath(), status.value(), rootCauses(ex));
        var res = exchange.getResponse();
        res.setStatusCode(status);
        res.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        res.getHeaders().set("Retry-After", "5");
        String code = status.value() == 504 ? "GATEWAY_TIMEOUT" : "SERVICE_UNAVAILABLE";
        String msg = status.value() == 504 ? "Dịch vụ phản hồi quá chậm, vui lòng thử lại" : "Dịch vụ tạm thời không phản hồi, vui lòng thử lại sau ít giây";
        byte[] body = ("{\"code\":\"" + code + "\",\"message\":\"" + msg + "\"}").getBytes(StandardCharsets.UTF_8);
        return res.writeWith(Mono.just(res.bufferFactory().wrap(body)));
    }

    private static String rootCauses(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = ex; t != null && sb.length() < 400; t = t.getCause()) sb.append(t.getClass().getSimpleName()).append("(").append(t.getMessage()).append(") <- ");
        return sb.toString();
    }

    private static HttpStatusCode classify(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ResponseStatusException rse && (rse.getStatusCode().value() == 503 || rse.getStatusCode().value() == 504)) {
                return rse.getStatusCode();
            }
            String n = t.getClass().getName();
            if (t instanceof ConnectException || t instanceof UnknownHostException || n.endsWith("ConnectTimeoutException")
                    || n.endsWith("CallNotPermittedException") || n.endsWith("AnnotatedConnectException")) {
                return HttpStatus.SERVICE_UNAVAILABLE;
            }
            if (t instanceof TimeoutException || n.endsWith("ReadTimeoutException") || n.endsWith("TimeoutException")) {
                return HttpStatus.GATEWAY_TIMEOUT;
            }
        }
        return null;
    }
}
