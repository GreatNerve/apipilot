package dev.apipilot.project;

import dev.apipilot.spec.ApiModel;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ProjectDetail(
    UUID id,
    String name,
    @Nullable String title,
    @Nullable String version,
    int operationCount,
    Instant createdAt,
    ApiModel api) {}
