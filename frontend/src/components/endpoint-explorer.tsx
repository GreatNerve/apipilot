"use client";

import { Lock, LockOpen, Search } from "lucide-react";
import { useMemo, useState, type ReactNode } from "react";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { ApiModel, JsonSchema, Operation } from "@/lib/api";
import { SchemaView } from "./schema-view";

const METHOD_STYLES: Record<string, string> = {
  GET: "text-emerald-700 bg-emerald-500/10 dark:text-emerald-400",
  POST: "text-blue-700 bg-blue-500/10 dark:text-blue-400",
  PUT: "text-amber-700 bg-amber-500/10 dark:text-amber-400",
  PATCH: "text-violet-700 bg-violet-500/10 dark:text-violet-400",
  DELETE: "text-red-700 bg-red-500/10 dark:text-red-400",
};

export function MethodBadge({ method }: { method: string }) {
  return (
    <span
      className={`inline-block w-13 shrink-0 rounded px-1 py-0.5 text-center font-mono text-[0.68rem] font-semibold ${
        METHOD_STYLES[method] ?? "bg-muted text-muted-foreground"
      }`}
    >
      {method}
    </span>
  );
}

function statusStyle(status: string): string {
  switch (status[0]) {
    case "2":
      return "text-emerald-700 dark:text-emerald-400";
    case "4":
      return "text-amber-700 dark:text-amber-400";
    case "5":
      return "text-red-700 dark:text-red-400";
    default:
      return "text-muted-foreground";
  }
}

export function EndpointExplorer({ api }: { api: ApiModel }) {
  const [filter, setFilter] = useState("");
  const [selectedId, setSelectedId] = useState(api.operations[0]?.id);

  const groups = useMemo(() => {
    const needle = filter.trim().toLowerCase();
    const byTag = new Map<string, Operation[]>();
    for (const operation of api.operations) {
      if (needle && !`${operation.method} ${operation.path}`.toLowerCase().includes(needle)) {
        continue;
      }
      const tag = operation.tags[0] ?? "other";
      byTag.set(tag, [...(byTag.get(tag) ?? []), operation]);
    }
    return [...byTag.entries()];
  }, [api.operations, filter]);

  const selected = api.operations.find((operation) => operation.id === selectedId);

  return (
    <div className="grid overflow-hidden rounded-lg border bg-card md:h-[calc(100svh-16rem)] md:min-h-96 md:grid-cols-[minmax(17rem,24rem)_1fr]">
      <nav aria-label="Endpoints" className="flex min-h-0 flex-col border-b md:border-r md:border-b-0">
        <div className="relative border-b p-2">
          <Search className="pointer-events-none absolute top-1/2 left-4.5 size-3.5 -translate-y-1/2 text-muted-foreground" />
          <Input
            aria-label="Filter endpoints"
            placeholder="Filter by path or method"
            className="pl-8"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          />
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto p-2">
          {groups.length === 0 && (
            <p className="p-2 text-sm text-muted-foreground">No endpoints match the filter</p>
          )}
          {groups.map(([tag, operations]) => (
            <div key={tag} role="group" aria-label={tag} className="mb-3 flex flex-col gap-px">
              <h3 className="flex items-center justify-between px-2 py-1 text-xs font-medium text-muted-foreground">
                <span className="truncate">{tag}</span>
                <span className="tabular-nums">{operations.length}</span>
              </h3>
              {operations.map((operation) => (
                <button
                  key={operation.id}
                  type="button"
                  aria-current={operation.id === selectedId}
                  onClick={() => setSelectedId(operation.id)}
                  className="flex items-center gap-2 rounded-md px-2 py-1.5 text-left outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring aria-[current=true]:bg-accent"
                >
                  <MethodBadge method={operation.method} />{" "}
                  <span title={operation.path} className="truncate font-mono text-xs">
                    {operation.path}
                  </span>
                </button>
              ))}
            </div>
          ))}
        </div>
      </nav>

      <section aria-label="Endpoint details" className="min-h-0 min-w-0 overflow-y-auto p-5">
        {selected ? (
          <OperationDetail operation={selected} schemas={api.schemas as Schemas} />
        ) : (
          <p className="text-sm text-muted-foreground">Select an endpoint to see its details.</p>
        )}
      </section>
    </div>
  );
}

type Schemas = Record<string, JsonSchema>;

function OperationDetail({ operation, schemas }: { operation: Operation; schemas: Schemas }) {
  const isPublic = operation.security.length === 0;
  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-2">
        <div className="flex items-center gap-2">
          <MethodBadge method={operation.method} />
          <code className="font-mono text-sm font-medium break-all">{operation.path}</code>
        </div>
        {operation.summary && <p className="text-sm">{operation.summary}</p>}
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="outline" className="gap-1 font-normal">
            {isPublic ? <LockOpen /> : <Lock />}
            {isPublic ? "No authentication" : `Requires ${operation.security.join(", ")}`}
          </Badge>
          <Badge variant="secondary" className="font-mono font-normal">
            {operation.id}
          </Badge>
        </div>
      </header>

      {operation.parameters.length > 0 && (
        <Section title="Parameters">
          <div className="overflow-hidden rounded-md border">
            <Table>
              <TableHeader className="bg-muted/50">
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>In</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Required</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {operation.parameters.map((parameter) => (
                  <TableRow key={`${parameter.in}:${parameter.name}`}>
                    <TableCell className="font-mono text-xs">{parameter.name}</TableCell>
                    <TableCell>{parameter.in}</TableCell>
                    <TableCell className="text-muted-foreground">
                      {describeType(parameter.schema)}
                    </TableCell>
                    <TableCell>{parameter.required ? "yes" : "no"}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </Section>
      )}

      {operation.requestBody && (
        <Section title="Request body" note={operation.requestBody.contentType}>
          <div className="rounded-md border px-3 py-1">
            {operation.requestBody.schema ? (
              <SchemaView schema={operation.requestBody.schema} schemas={schemas} />
            ) : (
              <p className="py-2 text-sm text-muted-foreground">No schema declared</p>
            )}
          </div>
        </Section>
      )}

      <Section title="Responses">
        <ul className="flex flex-col gap-3">
          {operation.responses.map((response) => (
            <li key={response.status} className="rounded-md border">
              <div className="flex items-baseline gap-2 border-b bg-muted/50 px-3 py-1.5 last:border-b-0">
                <span className={`font-mono text-sm font-semibold ${statusStyle(response.status)}`}>
                  {response.status}
                </span>
                <span className="text-xs text-muted-foreground">
                  {response.contentType ?? "no body"}
                </span>
              </div>
              {response.schema && (
                <div className="px-3 py-1">
                  <SchemaView schema={response.schema} schemas={schemas} />
                </div>
              )}
            </li>
          ))}
        </ul>
      </Section>
    </div>
  );
}

function Section({ title, note, children }: { title: string; note?: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-baseline gap-2">
        <h4 className="text-xs font-medium tracking-wide text-muted-foreground uppercase">{title}</h4>
        {note && <span className="font-mono text-xs text-muted-foreground">{note}</span>}
      </div>
      {children}
    </div>
  );
}

function describeType(schema: JsonSchema | undefined): string {
  if (!schema) {
    return "any";
  }
  const type = Array.isArray(schema.type) ? schema.type.join(" | ") : String(schema.type ?? "any");
  return typeof schema.format === "string" ? `${type} (${schema.format})` : type;
}
