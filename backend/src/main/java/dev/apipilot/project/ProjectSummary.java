package dev.apipilot.project;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ProjectSummary(
    UUID id,
    String name,
    @Nullable String title,
    @Nullable String version,
    int operationCount,
    Instant createdAt) {}
