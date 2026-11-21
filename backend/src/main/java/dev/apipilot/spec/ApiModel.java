package dev.apipilot.spec;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Format-neutral description of an API. Schemas are JSON Schema nodes; references between them stay
 * as {@code $ref} into {@link #schemas()} so self-referencing types cannot expand forever.
 */
public record ApiModel(
    @Nullable String title,
    @Nullable String version,
    List<String> servers,
    List<Operation> operations,
    Map<String, SecurityScheme> securitySchemes,
    Map<String, JsonNode> schemas,
    boolean openApi31) {}
