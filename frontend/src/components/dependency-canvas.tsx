"use client";

import {
  Background,
  Controls,
  Handle,
  Position,
  ReactFlow,
  type NodeProps,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useTheme } from "next-themes";
import { useMemo } from "react";
import type { DependencyGraph } from "@/lib/api";
import { layoutGraph, type ResourceNode } from "@/lib/graph-layout";

const ROLES: [string, string][] = [
  ["CREATE", "C"],
  ["LIST", "L"],
  ["READ", "R"],
  ["UPDATE", "U"],
  ["PATCH", "P"],
  ["DELETE", "D"],
];

function ResourceCard({ data }: NodeProps<ResourceNode>) {
  const { resource } = data;
  return (
    <div className="w-64 rounded-lg border bg-card px-3 py-2 text-card-foreground shadow-xs">
      <Handle type="target" position={Position.Left} className="opacity-0" />
      <div className="text-sm font-medium">{resource.name}</div>
      <div className="truncate font-mono text-[0.68rem] text-muted-foreground" title={resource.key}>
        {resource.key}
      </div>
      <div className="mt-2 flex items-center gap-1">
        {ROLES.map(([role, letter]) => (
          <span
            key={role}
            title={`${role.toLowerCase()}${resource.operations[role] ? "" : " (not available)"}`}
            className={`w-5 rounded text-center font-mono text-[0.68rem] leading-5 ${
              resource.operations[role]
                ? "bg-foreground/10 font-semibold"
                : "text-muted-foreground/40"
            }`}
          >
            {letter}
          </span>
        ))}
        {resource.actions.length > 0 && (
          <span className="ml-1 text-[0.68rem] text-muted-foreground">
            +{resource.actions.length} action{resource.actions.length > 1 ? "s" : ""}
          </span>
        )}
      </div>
      <Handle type="source" position={Position.Right} className="opacity-0" />
    </div>
  );
}

const nodeTypes = { resource: ResourceCard };

/** Read-only picture of the graph; corrections are made in the details below it. */
export function DependencyCanvas({ graph }: { graph: DependencyGraph }) {
  const { nodes, edges } = useMemo(() => layoutGraph(graph), [graph]);
  const { resolvedTheme } = useTheme();
  return (
    <div className="h-[26rem] overflow-hidden rounded-lg border bg-card">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        colorMode={resolvedTheme === "dark" ? "dark" : "light"}
        fitView
        fitViewOptions={{ padding: 0.15, maxZoom: 1 }}
        nodesConnectable={false}
      >
        <Background />
        <Controls showInteractive={false} />
      </ReactFlow>
    </div>
  );
}
