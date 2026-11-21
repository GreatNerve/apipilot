package dev.apipilot.spec;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One callable API operation.
 *
 * @param requestBody null when the operation takes no body
 * @param security names of the security schemes the caller must satisfy; empty means public
 */
public record Operation(
    String id,
    String method,
    String path,
    @Nullable String summary,
    List<String> tags,
    List<Parameter> parameters,
    @Nullable Body requestBody,
    List<Response> responses,
    List<String> security) {}
