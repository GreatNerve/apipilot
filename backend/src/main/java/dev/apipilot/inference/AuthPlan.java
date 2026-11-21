package dev.apipilot.inference;

import org.jspecify.annotations.Nullable;

/**
 * How to satisfy one security scheme.
 *
 * @param tokenOperationId operation that returns a token; null when credentials must be supplied
 * @param tokenPointer JSON Pointer to the token in that operation's response
 * @param registerOperationId operation that creates the account the token operation logs in as
 * @param valueTemplate value to send, with {@code {{token}}} standing for the token; null for
 *     schemes that need a user-supplied value
 */
public record AuthPlan(
    String scheme,
    @Nullable String tokenOperationId,
    @Nullable String tokenPointer,
    @Nullable String registerOperationId,
    @Nullable String valueTemplate) {}
