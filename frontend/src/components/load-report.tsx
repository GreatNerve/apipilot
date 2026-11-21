"use client";

import { CircleCheck, CircleSlash, CircleX, LoaderCircle, TriangleAlert } from "lucide-react";
import type { ReactNode } from "react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { LoadRun } from "@/lib/api";
import {
  formatBytes,
  formatCount,
  formatDuration,
  formatMs,
  formatPercent,
} from "@/lib/load-format";
import { MethodBadge } from "./endpoint-explorer";
import { LatencyChart, ThroughputChart } from "./load-charts";

const PROFILE_LABELS: Record<LoadRun["profile"], string> = {
  SMOKE: "Smoke",
  LOAD: "Load",
  SPIKE: "Spike",
  SOAK: "Soak",
  CAPACITY: "Capacity",
};

const STATUS: Record<LoadRun["status"], { label: string; icon: ReactNode; className: string }> = {
  RUNNING: { label: "Running", icon: <LoaderCircle className="animate-spin" />, className: "" },
  PASSED: {
    label: "Passed",
    icon: <CircleCheck />,
    className: "border-emerald-500/40 text-emerald-700 dark:text-emerald-400",
  },
  FAILED: {
    label: "Threshold breached",
    icon: <CircleX />,
    className: "border-red-500/40 text-red-700 dark:text-red-400",
  },
  ABORTED: { label: "Aborted", icon: <CircleSlash />, className: "text-muted-foreground" },
};

/** Everything measured during one load run: headline figures, trends, then per-endpoint detail. */
export function LoadReport({ run }: { run: LoadRun }) {
  const { totals } = run;
  const status = STATUS[run.status];
  const steps = [...run.steps].sort((a, b) => b.latency.p95 - a.latency.p95);
  const slowestP95 = steps[0]?.latency.p95 ?? 0;

  return (
    <div className="flex flex-col gap-4">
      <header className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <h2 className="text-base font-semibold">{run.name}</h2>
        <Badge variant="outline" className={`gap-1 ${status.className}`}>
          {status.icon}
          {status.label}
        </Badge>
        <p className="text-sm text-muted-foreground">
          {PROFILE_LABELS[run.profile]} · {run.targetRate} flows/s ·{" "}
          {formatDuration(run.durationSeconds)} · started{" "}
          {new Date(run.startedAt).toLocaleString(undefined, {
            dateStyle: "medium",
            timeStyle: "short",
          })}
        </p>
      </header>

      {run.generatorLimited && (
        <Alert className="border-amber-500/50">
          <TriangleAlert />
          <AlertTitle>The load generator could not keep up</AlertTitle>
          <AlertDescription>
            This machine ran out of capacity before the API did, so these figures understate what
            the API can handle. Lower the rate or run from a larger machine.
          </AlertDescription>
        </Alert>
      )}

      <div className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-7">
        <Tile label="Requests" value={formatCount(totals.requests)} hint="sent in total" />
        <Tile
          label="Throughput"
          value={`${formatCount(totals.requests / Math.max(run.durationSeconds, 1))} req/s`}
          hint="average over the run"
        />
        <Tile
          label="Error rate"
          value={formatPercent(totals.errors, totals.requests)}
          hint={`${formatCount(totals.errors)} failed`}
          warn={totals.errors > 0}
        />
        <Tile label="P50" value={formatMs(totals.latency.p50)} hint="half were faster" />
        <Tile label="P95" value={formatMs(totals.latency.p95)} hint="95% were faster" />
        <Tile label="P99" value={formatMs(totals.latency.p99)} hint="99% were faster" />
        <Tile
          label="Avg response size"
          value={formatBytes(totals.avgBytes)}
          hint={`slowest ${formatMs(totals.latency.max)}`}
        />
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        <Panel title="Response time" description="Percentiles for each second of the run">
          <LatencyChart windows={run.windows} />
        </Panel>
        <Panel title="Throughput" description="Requests and failed requests per second">
          <ThroughputChart windows={run.windows} />
        </Panel>
      </div>

      <Panel title="Endpoints" description="Slowest first, by P95 response time">
        <div className="overflow-hidden rounded-md border">
          <Table aria-label="Endpoints">
            <TableHeader className="bg-muted/50">
              <TableRow>
                <TableHead>Endpoint</TableHead>
                <TableHead className="text-right">Requests</TableHead>
                <TableHead className="text-right">Errors</TableHead>
                <TableHead className="text-right">Avg</TableHead>
                <TableHead className="text-right">P50</TableHead>
                <TableHead className="w-48">P95</TableHead>
                <TableHead className="text-right">P99</TableHead>
                <TableHead className="text-right">Avg size</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {steps.map((step, index) => (
                <TableRow key={step.stepId} aria-label={step.name}>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <MethodBadge method={step.method} />
                      <span data-testid="path" className="font-mono text-xs">
                        {step.path}
                      </span>
                      {index === 0 && steps.length > 1 && (
                        <Badge variant="secondary" className="font-normal">
                          Slowest
                        </Badge>
                      )}
                    </div>
                    <p className="mt-0.5 text-xs text-muted-foreground">{step.name}</p>
                  </TableCell>
                  <Num>{formatCount(step.requests)}</Num>
                  <Num warn={step.errors > 0}>{formatPercent(step.errors, step.requests)}</Num>
                  <Num>{formatMs(step.latency.avg)}</Num>
                  <Num>{formatMs(step.latency.p50)}</Num>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <span className="w-16 shrink-0 text-right font-mono text-xs tabular-nums">
                        {formatMs(step.latency.p95)}
                      </span>
                      <span className="h-1.5 flex-1 rounded-full bg-muted">
                        <span
                          className="block h-full rounded-full bg-chart-1"
                          style={{ width: `${(step.latency.p95 / slowestP95) * 100}%` }}
                        />
                      </span>
                    </div>
                  </TableCell>
                  <Num>{formatMs(step.latency.p99)}</Num>
                  <Num>{formatBytes(step.avgBytes)}</Num>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </Panel>
    </div>
  );
}

function Tile({
  label,
  value,
  hint,
  warn,
}: {
  label: string;
  value: string;
  hint: string;
  warn?: boolean;
}) {
  return (
    <div role="group" aria-label={label} className="rounded-lg border bg-card px-4 py-3">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div
        className={`mt-0.5 text-xl font-semibold tabular-nums ${warn ? "text-amber-600 dark:text-amber-400" : ""}`}
      >
        {value}
      </div>
      <div className="text-xs text-muted-foreground">{hint}</div>
    </div>
  );
}

function Panel({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <section aria-label={title} className="flex flex-col gap-3 rounded-lg border bg-card p-4">
      <div>
        <h3 className="text-sm font-semibold">{title}</h3>
        <p className="text-xs text-muted-foreground">{description}</p>
      </div>
      {children}
    </section>
  );
}

function Num({ children, warn }: { children: ReactNode; warn?: boolean }) {
  return (
    <TableCell
      className={`text-right font-mono text-xs tabular-nums ${warn ? "text-amber-600 dark:text-amber-400" : ""}`}
    >
      {children}
    </TableCell>
  );
}
