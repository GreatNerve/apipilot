import { expect, test } from "vitest";
import { sampleGraph } from "@/test/sample-graph";
import { layoutGraph } from "./graph-layout";

test("places each level in its own column, left to right", () => {
  const { nodes } = layoutGraph(sampleGraph);
  const x = (id: string) => nodes.find((node) => node.id === id)!.position.x;

  expect(x("/categories")).toBe(x("/workspaces"));
  expect(x("/documents")).toBeGreaterThan(x("/workspaces"));
});

test("stacks resources of the same level without overlap", () => {
  const { nodes } = layoutGraph(sampleGraph);
  const y = (id: string) => nodes.find((node) => node.id === id)!.position.y;

  expect(y("/workspaces")).toBeGreaterThan(y("/categories"));
});

test("draws each dependency from the resource created first to the one that needs it", () => {
  const { edges } = layoutGraph(sampleGraph);

  expect(edges).toHaveLength(3);
  expect(edges[0]).toMatchObject({
    source: "/workspaces",
    target: "/workspaces/{workspaceId}/members",
    label: "workspaceId",
  });
});

test("marks low-confidence dependencies so they stand out", () => {
  const { edges } = layoutGraph(sampleGraph);

  expect(edges.find((edge) => edge.target === "/documents" && edge.source === "/categories"))
    .toMatchObject({ animated: true });
  expect(edges[0].animated).toBe(false);
});
