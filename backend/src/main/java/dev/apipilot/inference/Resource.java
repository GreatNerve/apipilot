package dev.apipilot.inference;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A group of operations that manage one kind of thing.
 *
 * @param key the collection path, unique per resource, for example {@code /workspaces/{id}/members}
 * @param name the last fixed path segment
 * @param idParam the path parameter that identifies one item; null for a singleton
 * @param operations operation ID per role
 * @param actions IDs of the remaining operations on this resource, in call order
 * @param idSource null when the resource has no ID or nothing in the spec produces one
 */
public record Resource(
    String key,
    String name,
    @Nullable String idParam,
    Map<Role, String> operations,
    List<String> actions,
    @Nullable IdSource idSource) {}
