/** Number formatting for load results. Everything here is display only. */

export function formatMs(ms: number): string {
  if (ms === 0) {
    return "0 ms";
  }
  if (ms >= 999.5) {
    return `${(ms / 1000).toFixed(2)} s`;
  }
  return ms < 10 ? `${ms.toFixed(1)} ms` : `${Math.round(ms)} ms`;
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${Math.round(bytes)} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} kB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function formatCount(count: number): string {
  return Math.round(count).toLocaleString("en-US");
}

export function formatPercent(part: number, whole: number): string {
  if (whole === 0 || part === 0) {
    return "0%";
  }
  const percent = (part / whole) * 100;
  return `${Number(percent.toPrecision(2))}%`;
}

export function formatDuration(seconds: number): string {
  const parts = [
    [Math.floor(seconds / 3600), "h"],
    [Math.floor((seconds % 3600) / 60), "m"],
    [seconds % 60, "s"],
  ] as const;
  return (
    parts
      .filter(([value]) => value > 0)
      .map(([value, unit]) => `${value}${unit}`)
      .join(" ") || "0s"
  );
}

/** Position within a run, as minutes:seconds. */
export function formatClock(seconds: number): string {
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`;
}
