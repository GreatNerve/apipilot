import type { Edge, Node } from "@xyflow/react";
import type { DependencyGraph, Resource } from "./api";

const COLUMN_WIDTH = 460;
const ROW_HEIGHT = 120;

/** Below this, a dependency is a guess a person should check. */
export const LOW_CONFIDENCE = 0.7;

export type ResourceNode = Node<{ resource: Resource }, "resource">;

/** One column per level, so everything a resource needs is to its left. */
export function layoutGraph(graph: DependencyGraph): { nodes: ResourceNode[]; edges: Edge[] } {
  const byKey = new Map(graph.resources.map((resource) => [resource.key, resource]));
  const nodes = graph.levels.flatMap((level, column) =>
    level.map(
      (key, row): ResourceNode => ({
        id: key,
        type: "resource",
        position: { x: column * COLUMN_WIDTH, y: row * ROW_HEIGHT },
        data: { resource: byKey.get(key)! },
      }),
    ),
  );
  const edges = graph.dependencies.map(
    (dependency): Edge => ({
      id: `${dependency.from}|${dependency.to}|${dependency.input}`,
      source: dependency.to,
      target: dependency.from,
      label: dependency.input.replace(/^(path:|body:\/)/, ""),
      animated: dependency.confidence < LOW_CONFIDENCE,
    }),
  );
  return { nodes, edges };
}
