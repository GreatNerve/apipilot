import type { LoadRun } from "@/lib/api";

/** A 60-second run at 50 flows/s with a latency bump in the middle. Deterministic. */
export function sampleLoadRun(overrides: Partial<LoadRun> = {}): LoadRun {
  const windows = Array.from({ length: 60 }, (_, second) => {
    const bump = second >= 30 && second < 40 ? 3 : 1;
    const wobble = 1 + 0.08 * Math.sin(second / 2);
    return {
      second,
      requests: 245 + (second % 7),
      errors: second >= 32 && second < 38 ? 4 : 0,
      p50: 24 * wobble * bump,
      p95: 70 * wobble * bump,
      p99: 120 * wobble * bump,
    };
  });
  const latency = (scale: number) => ({
    p50: 24 * scale,
    avg: 31 * scale,
    p95: 78 * scale,
    p99: 182 * scale,
    max: 640 * scale,
  });
  return {
    id: "run-1",
    projectId: "p1",
    name: "Workspace lifecycle",
    profile: "LOAD",
    status: "PASSED",
    targetRate: 50,
    durationSeconds: 60,
    startedAt: "2026-11-17T10:00:00Z",
    generatorLimited: false,
    totals: { requests: 15000, errors: 24, avgBytes: 742, latency: latency(1) },
    windows,
    steps: [
      { stepId: "s1", name: "Log in", method: "POST", path: "/auth/token", requests: 50, errors: 0, avgBytes: 96, latency: latency(0.6) },
      { stepId: "s2", name: "Create workspace", method: "POST", path: "/workspaces", requests: 3000, errors: 0, avgBytes: 212, latency: latency(1.4) },
      { stepId: "s3", name: "Read workspace", method: "GET", path: "/workspaces/{workspaceId}", requests: 3000, errors: 0, avgBytes: 212, latency: latency(0.5) },
      { stepId: "s4", name: "List documents", method: "GET", path: "/documents", requests: 3000, errors: 24, avgBytes: 2890, latency: latency(2.6) },
      { stepId: "s5", name: "Delete workspace", method: "DELETE", path: "/workspaces/{workspaceId}", requests: 3000, errors: 0, avgBytes: 0, latency: latency(0.8) },
    ],
    ...overrides,
  };
}
