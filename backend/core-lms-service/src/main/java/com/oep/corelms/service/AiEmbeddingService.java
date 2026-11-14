package com.oep.corelms.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiEmbeddingService {
    public static final int DIMENSIONS = 1536;
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?\\d[\\d .()-]{7,}\\d)(?!\\d)");
    private static final Pattern IPV4 = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern UUID_VALUE = Pattern.compile("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b");

    @Value("${oep.ai.embeddings-url:}")
    private String embeddingsUrl;
    @Value("${oep.ai.api-key:}")
    private String apiKey;
    @Value("${oep.ai.embedding-model:text-embedding-3-small}")
    private String model;

    public boolean isConfigured() {
        return embeddingsUrl != null && !embeddingsUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    public List<float[]> embed(List<String> inputs) {
        if (!isConfigured()) throw new IllegalStateException("AI embeddings provider is not configured");
        if (inputs == null || inputs.isEmpty()) return List.of();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2_000);
        requestFactory.setReadTimeout(10_000);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", inputs.stream().map(AiEmbeddingService::stripPii).toList());
        body.put("dimensions", DIMENSIONS);
        JsonNode response = RestClient.builder().requestFactory(requestFactory).build().post().uri(embeddingsUrl)
                .contentType(MediaType.APPLICATION_JSON).headers(headers -> headers.setBearerAuth(apiKey))
                .body(body).retrieve().body(JsonNode.class);
        JsonNode data = response == null ? null : response.path("data");
        if (data == null || !data.isArray() || data.size() != inputs.size()) {
            throw new IllegalStateException("Embedding provider returned an unexpected number of vectors");
        }
        List<JsonNode> ordered = new ArrayList<>();
        data.forEach(ordered::add);
        ordered.sort(Comparator.comparingInt(node -> node.path("index").asInt(Integer.MAX_VALUE)));
        List<float[]> vectors = new ArrayList<>();
        for (JsonNode item : ordered) {
            JsonNode values = item.path("embedding");
            if (!values.isArray() || values.size() != DIMENSIONS) {
                throw new IllegalStateException("Embedding provider returned an invalid vector dimension");
            }
            float[] vector = new float[DIMENSIONS];
            for (int i = 0; i < DIMENSIONS; i++) vector[i] = (float) values.get(i).asDouble();
            vectors.add(vector);
        }
        return List.copyOf(vectors);
    }

    public static String toPgVector(float[] vector) {
        StringBuilder value = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) value.append(',');
            value.append(Float.toString(vector[i]));
        }
        return value.append(']').toString();
    }

    private static String stripPii(String text) {
        return UUID_VALUE.matcher(IPV4.matcher(PHONE.matcher(EMAIL.matcher(text).replaceAll("[EMAIL]"))
                .replaceAll("[PHONE]")).replaceAll("[IP]")).replaceAll("[ID]");
    }
}
