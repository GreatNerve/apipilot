package dev.apipilot.inference;

import java.util.List;

/**
 * Resources of an API and the order they have to be created in.
 *
 * @param levels resource keys; each level needs only values from earlier levels. Create runs
 *     through the levels forwards, delete runs through them backwards
 * @param droppedForCycles dependencies ignored to break a cycle
 * @param standaloneOperations IDs of operations that belong to no resource
 */
public record DependencyGraph(
    List<Resource> resources,
    List<Dependency> dependencies,
    List<Dependency> droppedForCycles,
    List<List<String>> levels,
    List<String> standaloneOperations,
    List<AuthPlan> auth,
    List<UnresolvedInput> unresolved) {}
