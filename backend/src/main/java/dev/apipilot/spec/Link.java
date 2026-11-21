package dev.apipilot.spec;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** An OpenAPI link: values of this response feed parameters of the target operation. */
public record Link(String name, @Nullable String operationId, Map<String, String> parameters) {}
