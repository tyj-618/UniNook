package com.uninook.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the JSON parsing pattern used by the Elasticsearch and embedding clients.
 * Spring Boot 4 configures RestClient with Jackson 3 converters, which cannot
 * deserialize Jackson 2 {@link JsonNode} bodies ("Type definition error"). External
 * payloads must therefore be read as String and parsed with a Jackson 2 mapper.
 */
class RestClientJsonParsingTests {

    @Test
    void stringBodyParsedWithJackson2Mapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/search", exchange -> {
            byte[] payload = "{\"hits\":{\"hits\":[{\"_source\":{\"postId\":7}}]}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();
        try {
            RestClient restClient = RestClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .build();
            String responseBody = restClient.post().uri("/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("size", 1))
                    .retrieve()
                    .body(String.class);
            JsonNode response = new ObjectMapper().readTree(responseBody);
            assertThat(response.path("hits").path("hits").path(0).path("_source").path("postId").longValue())
                    .isEqualTo(7L);
        } finally {
            server.stop(0);
        }
    }
}
