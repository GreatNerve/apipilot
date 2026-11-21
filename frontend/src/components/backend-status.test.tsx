import { screen } from "@testing-library/react";
import { expect, test, vi } from "vitest";
import { renderWithQuery } from "@/test/render";
import { BackendStatus } from "./backend-status";

test("shows connected when the health endpoint reports UP", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => Response.json({ status: "UP" })),
  );

  renderWithQuery(<BackendStatus />);

  expect(await screen.findByText("Backend connected")).toBeInTheDocument();
  expect(fetch).toHaveBeenCalledWith("/api/health");
});

test("shows unreachable when the health request fails", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => {
      throw new TypeError("Failed to fetch");
    }),
  );

  renderWithQuery(<BackendStatus />);

  expect(await screen.findByText("Backend unreachable")).toBeInTheDocument();
});

test("shows unreachable when the backend reports it is not UP", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => Response.json({ status: "DOWN" }, { status: 503 })),
  );

  renderWithQuery(<BackendStatus />);

  expect(await screen.findByText("Backend unreachable")).toBeInTheDocument();
});
