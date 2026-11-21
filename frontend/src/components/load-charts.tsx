"use client";

import { Area, AreaChart, CartesianGrid, Line, LineChart, XAxis, YAxis } from "recharts";
import {
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart";
import type { LoadRun } from "@/lib/api";
import { formatClock, formatMs } from "@/lib/load-format";

const latencyConfig = {
  p50: { label: "P50", color: "var(--chart-1)" },
  p95: { label: "P95", color: "var(--chart-2)" },
  p99: { label: "P99", color: "var(--chart-3)" },
} satisfies ChartConfig;

const throughputConfig = {
  requests: { label: "Requests/s", color: "var(--chart-1)" },
  errors: { label: "Errors/s", color: "var(--chart-2)" },
} satisfies ChartConfig;

const axis = { tickLine: false, axisLine: false, tickMargin: 8 } as const;

/** Response-time percentiles for each second of the run. One axis, in milliseconds. */
export function LatencyChart({ windows }: { windows: LoadRun["windows"] }) {
  return (
    <ChartContainer config={latencyConfig} className="aspect-auto h-64 w-full">
      <LineChart data={windows} margin={{ left: 4, right: 12, top: 12 }}>
        <CartesianGrid vertical={false} />
        <XAxis dataKey="second" tickFormatter={formatClock} minTickGap={40} {...axis} />
        <YAxis width={68} tickFormatter={(value: number) => formatMs(value)} {...axis} />
        <ChartTooltip
          content={
            <ChartTooltipContent
              labelFormatter={(_, payload) => formatClock(payload[0]?.payload.second ?? 0)}
              formatter={(value, name) => (
                <span className="flex w-full justify-between gap-4">
                  <span className="text-muted-foreground">
                    {latencyConfig[name as keyof typeof latencyConfig].label}
                  </span>
                  <span className="font-mono font-medium tabular-nums">
                    {formatMs(Number(value))}
                  </span>
                </span>
              )}
            />
          }
        />
        <ChartLegend itemSorter={null} content={<ChartLegendContent />} />
        {(["p50", "p95", "p99"] as const).map((key) => (
          <Line
            key={key}
            dataKey={key}
            type="monotone"
            stroke={`var(--color-${key})`}
            strokeWidth={2}
            dot={false}
            isAnimationActive={false}
          />
        ))}
      </LineChart>
    </ChartContainer>
  );
}

/** Requests and errors per second. Both are counts per second, so they share the axis. */
export function ThroughputChart({ windows }: { windows: LoadRun["windows"] }) {
  return (
    <ChartContainer config={throughputConfig} className="aspect-auto h-64 w-full">
      <AreaChart data={windows} margin={{ left: 4, right: 12, top: 12 }}>
        <CartesianGrid vertical={false} />
        <XAxis dataKey="second" tickFormatter={formatClock} minTickGap={40} {...axis} />
        <YAxis width={56} {...axis} />
        <ChartTooltip
          content={
            <ChartTooltipContent
              labelFormatter={(_, payload) => formatClock(payload[0]?.payload.second ?? 0)}
            />
          }
        />
        <ChartLegend itemSorter={null} content={<ChartLegendContent />} />
        {(["requests", "errors"] as const).map((key) => (
          <Area
            key={key}
            dataKey={key}
            type="monotone"
            stroke={`var(--color-${key})`}
            strokeWidth={2}
            fill={`var(--color-${key})`}
            fillOpacity={0.12}
            isAnimationActive={false}
          />
        ))}
      </AreaChart>
    </ChartContainer>
  );
}
