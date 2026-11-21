package dev.apipilot.spec;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * @param in one of {@code path}, {@code query}, {@code header}, {@code cookie}
 */
public record Parameter(String name, String in, boolean required, @Nullable JsonNode schema) {}
