package dev.apipilot.spec;

import org.jspecify.annotations.Nullable;

/**
 * @param type {@code http}, {@code apiKey}, {@code oauth2} or {@code openIdConnect}
 * @param scheme for {@code http}: {@code bearer} or {@code basic}
 * @param in for {@code apiKey}: {@code header}, {@code query} or {@code cookie}
 * @param parameterName for {@code apiKey}: the header, query or cookie name
 */
public record SecurityScheme(
    @Nullable String type,
    @Nullable String scheme,
    @Nullable String in,
    @Nullable String parameterName) {}
