"use client";

import { useQuery } from "@tanstack/react-query";

async function fetchHealth(): Promise<boolean> {
  const response = await fetch("/api/health");
  if (!response.ok) {
    return false;
  }
  const body: { status?: string } = await response.json();
  return body.status === "UP";
}

export function BackendStatus() {
  const { data: connected, isPending } = useQuery({
    queryKey: ["health"],
    queryFn: fetchHealth,
    refetchInterval: 15_000,
  });

  if (isPending) {
    return <span className="text-sm text-zinc-500">Checking backend…</span>;
  }
  return (
    <span className="inline-flex items-center gap-2 text-sm">
      <span
        aria-hidden
        className={`size-2 rounded-full ${connected ? "bg-emerald-500" : "bg-red-500"}`}
      />
      {connected ? "Backend connected" : "Backend unreachable"}
    </span>
  );
}
