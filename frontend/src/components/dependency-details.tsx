"use client";

import { Plus, Undo2, X } from "lucide-react";
import { useState, type ReactNode } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { Table, TableBody, TableCell, TableRow } from "@/components/ui/table";
import type { Dependency, DependencyGraph, EdgeRef, GraphOverrides } from "@/lib/api";
import { LOW_CONFIDENCE } from "@/lib/graph-layout";

type Props = {
  graph: DependencyGraph;
  overrides: GraphOverrides;
  onChange: (overrides: GraphOverrides) => void;
};

const sameEdge = (a: EdgeRef, b: EdgeRef) =>
  a.from === b.from && a.to === b.to && a.input === b.input;

/** Everything about the inferred order in words, with the controls to correct it. */
export function DependencyDetails({ graph, overrides, onChange }: Props) {
  const names = new Map(graph.resources.map((resource) => [resource.key, resource.name]));
  const name = (key: string) => names.get(key) ?? key;
  const createOrder = graph.levels.flat();

  function remove(dependency: Dependency) {
    const edge = { from: dependency.from, to: dependency.to, input: dependency.input };
    const wasAdded = overrides.added.some((added) => sameEdge(added, edge));
    onChange(
      wasAdded
        ? { ...overrides, added: overrides.added.filter((added) => !sameEdge(added, edge)) }
        : { ...overrides, removed: [...overrides.removed, edge] },
    );
  }

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <Panel title="Execution order" className="lg:col-span-2">
        <div className="grid gap-4 sm:grid-cols-2">
          <OrderList label="Create order" keys={createOrder} name={name} />
          <OrderList label="Delete order" keys={[...createOrder].reverse()} name={name} />
        </div>
      </Panel>

      <Panel
        title="Dependencies"
        description="Why each resource has to wait for another. Remove a wrong guess or add a missing one."
        className="lg:col-span-2"
      >
        {graph.dependencies.length === 0 ? (
          <p className="text-sm text-muted-foreground">No resource depends on another.</p>
        ) : (
          <div className="overflow-hidden rounded-md border">
            <Table>
              <TableBody>
                {graph.dependencies.map((dependency) => (
                  <TableRow
                    key={`${dependency.from}|${dependency.to}|${dependency.input}`}
                    aria-label={`${name(dependency.from)} needs ${name(dependency.to)}`}
                  >
                    <TableCell className="whitespace-normal">
                      <span className="font-medium">{name(dependency.from)}</span>
                      <span className="text-muted-foreground"> needs </span>
                      <span className="font-medium">{name(dependency.to)}</span>
                      <p className="text-xs text-muted-foreground">{dependency.reason}</p>
                    </TableCell>
                    <TableCell>
                      <code className="font-mono text-xs">{dependency.input}</code>
                      {!dependency.required && (
                        <span className="ml-2 text-xs text-muted-foreground">optional</span>
                      )}
                    </TableCell>
                    <TableCell>
                      <Confidence value={dependency.confidence} />
                    </TableCell>
                    <TableCell className="w-px text-right">
                      <Button variant="ghost" size="sm" onClick={() => remove(dependency)}>
                        <X /> Remove
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
        {overrides.removed.length > 0 && (
          <ul className="flex flex-col gap-1 text-sm text-muted-foreground">
            {overrides.removed.map((edge) => (
              <li key={`${edge.from}|${edge.to}|${edge.input}`} className="flex items-center gap-2">
                <span className="line-through">
                  {name(edge.from)} no longer needs {name(edge.to)} ({edge.input})
                </span>
                <Button
                  variant="ghost"
                  size="xs"
                  onClick={() =>
                    onChange({
                      ...overrides,
                      removed: overrides.removed.filter((removed) => !sameEdge(removed, edge)),
                    })
                  }
                >
                  <Undo2 /> Restore
                </Button>
              </li>
            ))}
          </ul>
        )}
        <AddDependency
          graph={graph}
          onAdd={(edge) => onChange({ ...overrides, added: [...overrides.added, edge] })}
        />
      </Panel>

      <Panel title="Authentication">
        {graph.auth.length === 0 ? (
          <p className="text-sm text-muted-foreground">No operation requires authentication.</p>
        ) : (
          <ul className="flex flex-col gap-2 text-sm">
            {graph.auth.map((plan) => (
              <li key={plan.scheme}>
                <span className="font-medium">{plan.scheme}</span>:{" "}
                {plan.tokenOperationId ? (
                  <>
                    calls{" "}
                    {plan.registerOperationId
                      ? `${plan.registerOperationId}, then ${plan.tokenOperationId}`
                      : plan.tokenOperationId}
                    , reads the token at <Code>{plan.tokenPointer}</Code> and sends{" "}
                    <Code>{plan.valueTemplate}</Code>
                  </>
                ) : (
                  "No operation returns a token. You will need to supply the value."
                )}
              </li>
            ))}
          </ul>
        )}
      </Panel>

      {graph.standaloneOperations.length > 0 && (
        <Panel
          title="Operations outside any resource"
          description="Called on their own; they neither create nor need a resource."
        >
          <div className="flex flex-wrap gap-1.5">
            {graph.standaloneOperations.map((id) => (
              <Badge key={id} variant="secondary" className="font-mono font-normal">
                {id}
              </Badge>
            ))}
          </div>
        </Panel>
      )}

      {graph.unresolved.length > 0 && (
        <Panel
          title="Needs a value from you"
          description="Required inputs that nothing in the spec produces."
          className="border-amber-500/40 lg:col-span-2"
        >
          <ul className="flex flex-col gap-1 text-sm">
            {graph.unresolved.map((input) => (
              <li key={`${input.operationId}|${input.input}`}>
                <Code>{input.operationId}</Code> <Code>{input.input}</Code>
                <span className="text-muted-foreground"> — {input.reason}</span>
              </li>
            ))}
          </ul>
        </Panel>
      )}

      {graph.droppedForCycles.length > 0 && (
        <Panel
          title="Ignored to break a cycle"
          description="These resources reference each other; the weakest link was dropped."
          className="lg:col-span-2"
        >
          <ul className="flex flex-col gap-1 text-sm">
            {graph.droppedForCycles.map((dependency) => (
              <li key={`${dependency.from}|${dependency.to}|${dependency.input}`}>
                {name(dependency.from)} needs {name(dependency.to)} ({dependency.input})
              </li>
            ))}
          </ul>
        </Panel>
      )}
    </div>
  );
}

function Code({ children }: { children: ReactNode }) {
  return <code className="rounded bg-muted px-1 py-0.5 font-mono text-xs">{children}</code>;
}

function OrderList({
  label,
  keys,
  name,
}: {
  label: string;
  keys: string[];
  name: (key: string) => string;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <h4 className="text-xs font-medium text-muted-foreground">{label}</h4>
      <ol aria-label={label} className="flex flex-wrap items-center gap-y-1.5">
        {keys.map((key) => (
          <li
            key={key}
            title={key}
            className="rounded-md border bg-muted/50 px-2 py-0.5 font-mono text-xs not-last:mr-6 not-last:after:absolute not-last:after:ml-3.5 not-last:after:text-muted-foreground not-last:after:content-['→']"
          >
            {name(key)}
          </li>
        ))}
      </ol>
    </div>
  );
}

function Panel({
  title,
  description,
  className = "",
  children,
}: {
  title: string;
  description?: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <section aria-label={title} className={`flex flex-col gap-3 rounded-lg border bg-card p-4 ${className}`}>
      <div>
        <h3 className="text-sm font-semibold">{title}</h3>
        {description && <p className="text-xs text-muted-foreground">{description}</p>}
      </div>
      {children}
    </section>
  );
}

function Confidence({ value }: { value: number }) {
  const [label, dot] =
    value >= 1
      ? ["Certain", "bg-emerald-500"]
      : value >= LOW_CONFIDENCE
        ? ["Likely", "bg-sky-500"]
        : ["Low confidence", "bg-amber-500"];
  return (
    <Badge variant="outline" className="gap-1.5 font-normal">
      <span aria-hidden className={`size-1.5 rounded-full ${dot}`} />
      {label}
    </Badge>
  );
}

function AddDependency({ graph, onAdd }: { graph: DependencyGraph; onAdd: (edge: EdgeRef) => void }) {
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [input, setInput] = useState("");
  const valid = from !== "" && to !== "" && from !== to && input.trim() !== "";

  const options = (
    <>
      <NativeSelectOption value="">Choose…</NativeSelectOption>
      {graph.resources.map((resource) => (
        <NativeSelectOption key={resource.key} value={resource.key}>
          {resource.key}
        </NativeSelectOption>
      ))}
    </>
  );

  return (
    <form
      className="grid items-end gap-3 rounded-md bg-muted/40 p-3 md:grid-cols-[1fr_1fr_1fr_auto]"
      onSubmit={(event) => {
        event.preventDefault();
        onAdd({ from, to, input: input.trim() });
        setInput("");
      }}
    >
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="dependency-from">Resource that needs a value</Label>
        <NativeSelect id="dependency-from" className="w-full" value={from} onChange={(e) => setFrom(e.target.value)}>
          {options}
        </NativeSelect>
      </div>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="dependency-to">Resource that provides it</Label>
        <NativeSelect id="dependency-to" className="w-full" value={to} onChange={(e) => setTo(e.target.value)}>
          {options}
        </NativeSelect>
      </div>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="dependency-input">Where the value goes</Label>
        <Input
          id="dependency-input"
          className="font-mono text-xs"
          placeholder="body:/ownerId or path:ownerId"
          value={input}
          onChange={(e) => setInput(e.target.value)}
        />
      </div>
      <Button type="submit" variant="outline" disabled={!valid}>
        <Plus /> Add dependency
      </Button>
    </form>
  );
}
