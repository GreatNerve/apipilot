"use client";

import { useQuery } from "@tanstack/react-query";
import { Gauge } from "lucide-react";
import { useState } from "react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { api, unwrap } from "@/lib/api";
import { LoadReport } from "./load-report";

export function LoadView({ projectId }: { projectId: string }) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const runs = useQuery({
    queryKey: ["load-runs", projectId],
    queryFn: async () =>
      unwrap(
        await api.GET("/api/projects/{projectId}/load-runs", { params: { path: { projectId } } }),
      ),
  });

  if (runs.isPending) {
    return <Skeleton className="h-96" />;
  }
  if (runs.isError) {
    return (
      <Alert variant="destructive">
        <AlertTitle>Could not load runs</AlertTitle>
        <AlertDescription>{runs.error.message}</AlertDescription>
      </Alert>
    );
  }
  if (runs.data.length === 0) {
    return (
      <Empty className="rounded-lg border border-dashed">
        <EmptyHeader>
          <EmptyMedia variant="icon">
            <Gauge />
          </EmptyMedia>
          <EmptyTitle>No load runs yet</EmptyTitle>
          <EmptyDescription>
            Response times, throughput and per-endpoint figures appear here after a load test.
          </EmptyDescription>
        </EmptyHeader>
      </Empty>
    );
  }

  const run = runs.data.find((candidate) => candidate.id === selectedId) ?? runs.data[0];
  return (
    <div className="flex flex-col gap-4">
      {runs.data.length > 1 && (
        <div className="flex items-center gap-2">
          <Label htmlFor="load-run">Run</Label>
          <NativeSelect
            id="load-run"
            value={run.id}
            onChange={(event) => setSelectedId(event.target.value)}
          >
            {runs.data.map((candidate) => (
              <NativeSelectOption key={candidate.id} value={candidate.id}>
                {new Date(candidate.startedAt).toLocaleString(undefined, {
                  dateStyle: "medium",
                  timeStyle: "short",
                })}{" "}
                — {candidate.name}
              </NativeSelectOption>
            ))}
          </NativeSelect>
        </div>
      )}
      <LoadReport run={run} />
    </div>
  );
}
