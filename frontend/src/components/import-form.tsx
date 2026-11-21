"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState, type ChangeEvent, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { api, unwrap, type ProjectSummary } from "@/lib/api";

type Mode = "url" | "paste";

export function ImportForm({ onImported }: { onImported: (project: ProjectSummary) => void }) {
  const [mode, setMode] = useState<Mode>("url");
  const [specUrl, setSpecUrl] = useState("");
  const [spec, setSpec] = useState("");
  const [name, setName] = useState("");
  const [needsLogin, setNeedsLogin] = useState(false);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const queryClient = useQueryClient();

  const source =
    mode === "url"
      ? {
          specUrl: specUrl.trim(),
          ...(needsLogin ? { specUsername: username, specPassword: password } : {}),
        }
      : { spec };
  const hasSource = mode === "url" ? specUrl.trim() !== "" : spec.trim() !== "";

  const importSpec = useMutation({
    mutationFn: async () =>
      unwrap(
        await api.POST("/api/projects", {
          body: { ...(name.trim() ? { name: name.trim() } : {}), ...source },
        }),
      ),
    onSuccess: (project) => {
      queryClient.invalidateQueries({ queryKey: ["projects"] });
      onImported(project);
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    importSpec.mutate();
  }

  async function loadFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (file) {
      setSpec(await file.text());
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4">
      <Tabs value={mode} onValueChange={(value) => setMode(value as Mode)}>
        <TabsList>
          <TabsTrigger value="url">From URL</TabsTrigger>
          <TabsTrigger value="paste">Paste spec</TabsTrigger>
        </TabsList>
        <TabsContent value="url" className="flex flex-col gap-2">
          <Label htmlFor="spec-url">Spec URL</Label>
          <Input
            id="spec-url"
            type="url"
            placeholder="http://localhost:8090/v3/api-docs"
            value={specUrl}
            onChange={(e) => setSpecUrl(e.target.value)}
          />
          <p className="text-xs text-muted-foreground">
            OpenAPI 3.x or Swagger 2.0, JSON or YAML. The backend fetches it, so the URL must be
            reachable from where apipilot runs.
          </p>
          <label className="mt-1 flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              className="size-4 accent-primary"
              checked={needsLogin}
              onChange={(e) => setNeedsLogin(e.target.checked)}
            />
            This URL is password-protected
          </label>
          {needsLogin && (
            <div className="flex flex-col gap-2 rounded-md bg-muted/50 p-3">
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="spec-username">User name</Label>
                  <Input
                    id="spec-username"
                    autoComplete="off"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="spec-password">Password</Label>
                  <Input
                    id="spec-password"
                    type="password"
                    autoComplete="off"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                  />
                </div>
              </div>
              <p className="text-xs text-muted-foreground">
                Sent once as HTTP Basic authentication to fetch the spec. Not stored.
              </p>
            </div>
          )}
        </TabsContent>
        <TabsContent value="paste" className="flex flex-col gap-2">
          <Label htmlFor="spec-content">Spec content</Label>
          <Textarea
            id="spec-content"
            className="h-48 font-mono text-xs"
            placeholder="openapi: 3.0.3 …"
            value={spec}
            onChange={(e) => setSpec(e.target.value)}
          />
          <Label htmlFor="spec-file" className="text-xs text-muted-foreground">
            Or load it from a file
          </Label>
          <Input id="spec-file" type="file" accept=".json,.yaml,.yml" onChange={loadFile} />
        </TabsContent>
      </Tabs>

      <div className="flex flex-col gap-2">
        <Label htmlFor="project-name">Project name (optional)</Label>
        <Input
          id="project-name"
          placeholder="Defaults to the title in the spec"
          maxLength={200}
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
      </div>

      {importSpec.isError && (
        <p role="alert" className="text-sm text-destructive">
          {importSpec.error.message}
        </p>
      )}

      <Button type="submit" disabled={!hasSource || importSpec.isPending} className="self-start">
        {importSpec.isPending ? "Importing…" : "Import"}
      </Button>
    </form>
  );
}
