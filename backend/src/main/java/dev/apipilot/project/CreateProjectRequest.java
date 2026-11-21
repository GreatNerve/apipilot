package dev.apipilot.project;

import org.jspecify.annotations.Nullable;

/**
 * Either {@code spec} (the document itself) or {@code specUrl} (where to fetch it) is expected;
 * {@code spec} wins when both are sent. {@code name} defaults to the title in the spec.
 *
 * @param specUsername for a password-protected {@code specUrl}: sent once as HTTP Basic
 *     authentication with {@code specPassword}, never stored
 */
public record CreateProjectRequest(
    @Nullable String name,
    @Nullable String spec,
    @Nullable String specUrl,
    @Nullable String specUsername,
    @Nullable String specPassword) {}
