"use client";

import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { DependencyView } from "@/components/dependency-view";
import { EndpointExplorer } from "@/components/endpoint-explorer";
import { LoadView } from "@/components/load-view";
import { PageHeader } from "@/components/page-header";
import { ProjectAvatar } from "@/components/project-avatar";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { api, unwrap } from "@/lib/api";

export default function ProjectPage() {
  // Record IDs travel as query parameters because a static export has no dynamic routes.
  return (
    <Suspense>
      <Project />
    </Suspense>
  );
}

function Project() {
  const id = useSearchParams().get("id") ?? "";
  const project = useQuery({
    queryKey: ["project", id],
    queryFn: async () => unwrap(await api.GET("/api/projects/{id}", { params: { path: { id } } })),
    enabled: id !== "",
  });
  const graph = useQuery({
    queryKey: ["graph", id],
    queryFn: async () =>
      unwrap(await api.GET("/api/projects/{projectId}/graph", { params: { path: { projectId: id } } })),
    enabled: id !== "",
  });

  return (
    <>
      <PageHeader
        crumbs={[{ label: "Projects", href: "/" }, { label: project.data?.name ?? "Project" }]}
      />
      <div className="flex flex-col gap-5 p-6">
        {id === "" && (
          <Alert variant="destructive">
            <AlertTitle>No project selected</AlertTitle>
          </Alert>
        )}
        {project.isPending && id !== "" && <Skeleton className="h-40" />}
        {project.isError && (
          <Alert variant="destructive">
            <AlertTitle>Could not load the project</AlertTitle>
            <AlertDescription>{project.error.message}</AlertDescription>
          </Alert>
        )}
        {project.data && (
          <>
            <header className="flex flex-wrap items-center gap-x-3 gap-y-1">
              <ProjectAvatar seed={project.data.id} className="size-8" />
              <h1 className="text-lg font-semibold tracking-tight">{project.data.name}</h1>
              {project.data.version && (
                <Badge variant="secondary" className="font-mono font-normal">
                  v{project.data.version}
                </Badge>
              )}
              {project.data.api.servers[0] && (
                <code className="font-mono text-xs text-muted-foreground">
                  {project.data.api.servers[0]}
                </code>
              )}
            </header>

            <dl className="grid grid-cols-2 gap-3 lg:grid-cols-4">
              <Stat label="Operations" value={project.data.operationCount} />
              <Stat label="Resources" value={graph.data?.resources.length} />
              <Stat label="Dependencies" value={graph.data?.dependencies.length} />
              <Stat
                label="Needs your input"
                value={graph.data?.unresolved.length}
                warn={(graph.data?.unresolved.length ?? 0) > 0}
              />
            </dl>

            <Tabs defaultValue="endpoints" className="gap-4">
              <TabsList variant="line">
                <TabsTrigger value="endpoints">Endpoints</TabsTrigger>
                <TabsTrigger value="dependencies">Dependencies</TabsTrigger>
                <TabsTrigger value="load">Load</TabsTrigger>
              </TabsList>
              <TabsContent value="endpoints">
                <EndpointExplorer api={project.data.api} />
              </TabsContent>
              <TabsContent value="dependencies">
                <DependencyView projectId={id} />
              </TabsContent>
              <TabsContent value="load">
                <LoadView projectId={id} />
              </TabsContent>
            </Tabs>
          </>
        )}
      </div>
    </>
  );
}

function Stat({ label, value, warn }: { label: string; value: number | undefined; warn?: boolean }) {
  return (
    <div className="rounded-lg border bg-card px-4 py-3">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd
        className={`mt-0.5 text-2xl font-semibold tabular-nums ${warn ? "text-amber-600 dark:text-amber-400" : ""}`}
      >
        {value ?? <Skeleton className="mt-1 h-7 w-10" />}
      </dd>
    </div>
  );
}
