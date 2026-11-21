package dev.apipilot.spec;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * @param schema null when the spec declares the content type without a schema
 */
public record Body(String contentType, boolean required, @Nullable JsonNode schema) {}
