import { fireEvent, screen } from "@testing-library/react";
import { expect, test } from "vitest";
import { mockApi } from "@/test/mock-api";
import { renderWithQuery } from "@/test/render";
import { sampleLoadRun } from "@/test/sample-load-run";
import { LoadView } from "./load-view";

test("explains where runs come from when there are none", async () => {
  mockApi({ "GET /api/projects/p1/load-runs": () => Response.json([]) });

  renderWithQuery(<LoadView projectId="p1" />);

  expect(await screen.findByText("No load runs yet")).toBeInTheDocument();
});

test("shows the most recent run first", async () => {
  mockApi({
    "GET /api/projects/p1/load-runs": () =>
      Response.json([
        sampleLoadRun({ id: "new", name: "Newest run" }),
        sampleLoadRun({ id: "old", name: "Older run" }),
      ]),
  });

  renderWithQuery(<LoadView projectId="p1" />);

  expect(await screen.findByRole("heading", { name: "Newest run" })).toBeInTheDocument();
});

test("another run can be chosen", async () => {
  mockApi({
    "GET /api/projects/p1/load-runs": () =>
      Response.json([
        sampleLoadRun({ id: "new", name: "Newest run" }),
        sampleLoadRun({ id: "old", name: "Older run" }),
      ]),
  });
  renderWithQuery(<LoadView projectId="p1" />);

  fireEvent.change(await screen.findByLabelText("Run"), { target: { value: "old" } });

  expect(screen.getByRole("heading", { name: "Older run" })).toBeInTheDocument();
});

test("shows an error when runs cannot be loaded", async () => {
  mockApi({
    "GET /api/projects/p1/load-runs": () => Response.json({ detail: "boom" }, { status: 500 }),
  });

  renderWithQuery(<LoadView projectId="p1" />);

  expect(await screen.findByRole("alert")).toHaveTextContent("Could not load runs");
});
