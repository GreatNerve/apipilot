"use client";

import { Plus } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ImportForm } from "@/components/import-form";
import { PageHeader } from "@/components/page-header";
import { ProjectList } from "@/components/project-list";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

export default function Home() {
  const router = useRouter();
  const [importing, setImporting] = useState(false);
  return (
    <>
      <PageHeader
        crumbs={[{ label: "Projects" }]}
        actions={
          <Button size="sm" onClick={() => setImporting(true)}>
            <Plus /> Import API
          </Button>
        }
      />
      <div className="flex flex-col gap-4 p-6">
        <div>
          <h1 className="text-lg font-semibold tracking-tight">Projects</h1>
          <p className="text-sm text-muted-foreground">
            One project per API. Open one to explore endpoints, review dependencies and run tests.
          </p>
        </div>
        <ProjectList onImport={() => setImporting(true)} />
      </div>

      <Dialog open={importing} onOpenChange={setImporting}>
        <DialogContent className="sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>Import an API</DialogTitle>
            <DialogDescription>
              OpenAPI 3.x or Swagger 2.0, as JSON or YAML.
            </DialogDescription>
          </DialogHeader>
          <ImportForm
            onImported={(project) => {
              setImporting(false);
              router.push(`/project?id=${project.id}`);
            }}
          />
        </DialogContent>
      </Dialog>
    </>
  );
}
