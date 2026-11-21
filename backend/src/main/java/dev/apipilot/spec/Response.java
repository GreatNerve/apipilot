package dev.apipilot.spec;

import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * @param status an HTTP status code, a range such as {@code 2XX}, or {@code default}
 * @param contentType null when the response has no body
 * @param schema null when the response has no body or no declared schema
 */
public record Response(
    String status, @Nullable String contentType, @Nullable JsonNode schema, List<Link> links) {}
