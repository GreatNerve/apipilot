package dev.apipilot.inference;

import java.util.List;

/**
 * A person's corrections to the inferred graph.
 *
 * @param removed inferred dependencies to ignore
 * @param added dependencies the analysis missed
 */
public record GraphOverrides(List<EdgeRef> removed, List<EdgeRef> added) {

  public static final GraphOverrides NONE = new GraphOverrides(List.of(), List.of());
}
