"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Skeleton } from "@/components/ui/skeleton";
import { api, unwrap, type GraphOverrides } from "@/lib/api";
import { DependencyCanvas } from "./dependency-canvas";
import { DependencyDetails } from "./dependency-details";

export function DependencyView({ projectId }: { projectId: string }) {
  const queryClient = useQueryClient();
  const params = { path: { projectId } };
  const graph = useQuery({
    queryKey: ["graph", projectId],
    queryFn: async () => unwrap(await api.GET("/api/projects/{projectId}/graph", { params })),
  });
  const overrides = useQuery({
    queryKey: ["graph-overrides", projectId],
    queryFn: async () =>
      unwrap(await api.GET("/api/projects/{projectId}/graph/overrides", { params })),
  });
  const save = useMutation({
    mutationFn: async (body: GraphOverrides) =>
      unwrap(await api.PUT("/api/projects/{projectId}/graph/overrides", { params, body })),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["graph", projectId] });
      queryClient.invalidateQueries({ queryKey: ["graph-overrides", projectId] });
    },
  });

  const error = graph.error ?? overrides.error ?? save.error;
  if (error) {
    return (
      <Alert variant="destructive">
        <AlertTitle>Could not load the dependency graph</AlertTitle>
        <AlertDescription>{error.message}</AlertDescription>
      </Alert>
    );
  }
  if (!graph.data || !overrides.data) {
    return <Skeleton className="h-[26rem]" />;
  }
  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-muted-foreground">
        Resources are created left to right and deleted right to left. An animated line is a
        low-confidence guess worth checking.
      </p>
      <DependencyCanvas graph={graph.data} />
      <DependencyDetails graph={graph.data} overrides={overrides.data} onChange={save.mutate} />
    </div>
  );
}
