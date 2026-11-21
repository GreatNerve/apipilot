import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { expect, test, vi } from "vitest";
import { mockApi } from "@/test/mock-api";
import { renderWithQuery } from "@/test/render";
import { ProjectList } from "./project-list";

const pets = {
  id: "p1",
  name: "Pets",
  version: "1.0.0",
  operationCount: 3,
  createdAt: "2026-11-16T10:00:00Z",
};

test("shows each project as a table row linking to it", async () => {
  mockApi({ "GET /api/projects": () => Response.json([pets]) });

  renderWithQuery(<ProjectList onImport={() => {}} />);

  const link = await screen.findByRole("link", { name: "Pets" });
  expect(link).toHaveAttribute("href", "/project?id=p1");
  const row = link.closest("tr")!;
  expect(within(row).getByText("1.0.0")).toBeInTheDocument();
  expect(within(row).getByText("3")).toBeInTheDocument();
});

test("offers to import when there are no projects", async () => {
  mockApi({ "GET /api/projects": () => Response.json([]) });
  const onImport = vi.fn();

  renderWithQuery(<ProjectList onImport={onImport} />);

  expect(await screen.findByText("No projects yet")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Import API" }));
  expect(onImport).toHaveBeenCalled();
});

test("asks for confirmation before deleting and can be cancelled", async () => {
  const calls = mockApi({ "GET /api/projects": () => Response.json([pets]) });
  renderWithQuery(<ProjectList onImport={() => {}} />);

  fireEvent.click(await screen.findByRole("button", { name: "Delete Pets" }));
  const dialog = await screen.findByRole("alertdialog");
  expect(dialog).toHaveTextContent("Delete Pets?");
  fireEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));

  await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
  expect(calls.map((c) => c.route)).not.toContain("DELETE /api/projects/p1");
});

test("deletes a project after confirmation and refreshes the list", async () => {
  let projects = [pets];
  const calls = mockApi({
    "GET /api/projects": () => Response.json(projects),
    "DELETE /api/projects/p1": () => {
      projects = [];
      return new Response(null, { status: 204 });
    },
  });
  renderWithQuery(<ProjectList onImport={() => {}} />);

  fireEvent.click(await screen.findByRole("button", { name: "Delete Pets" }));
  const dialog = await screen.findByRole("alertdialog");
  fireEvent.click(within(dialog).getByRole("button", { name: "Delete project" }));

  expect(await screen.findByText("No projects yet")).toBeInTheDocument();
  await waitFor(() =>
    expect(calls.map((c) => c.route)).toContain("DELETE /api/projects/p1"),
  );
});

test("shows an error when projects cannot be loaded", async () => {
  mockApi({
    "GET /api/projects": () => Response.json({ detail: "boom" }, { status: 500 }),
  });

  renderWithQuery(<ProjectList onImport={() => {}} />);

  expect(await screen.findByRole("alert")).toHaveTextContent("Could not load projects");
});
