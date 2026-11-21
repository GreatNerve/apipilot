import { render, screen, within } from "@testing-library/react";
import { expect, test } from "vitest";
import { sampleLoadRun } from "@/test/sample-load-run";
import { LoadReport } from "./load-report";

const tile = (label: string) => screen.getByRole("group", { name: label });

test("headline tiles show volume, throughput, errors, percentiles and size", () => {
  render(<LoadReport run={sampleLoadRun()} />);

  expect(tile("Requests")).toHaveTextContent("15,000");
  expect(tile("Throughput")).toHaveTextContent("250 req/s");
  expect(tile("Error rate")).toHaveTextContent("0.16%");
  expect(tile("P50")).toHaveTextContent("24 ms");
  expect(tile("P95")).toHaveTextContent("78 ms");
  expect(tile("P99")).toHaveTextContent("182 ms");
  expect(tile("Avg response size")).toHaveTextContent("742 B");
});

test("summarises what was run", () => {
  render(<LoadReport run={sampleLoadRun()} />);

  const summary = screen.getByRole("heading", { name: "Workspace lifecycle" }).closest("header")!;
  expect(summary).toHaveTextContent("Load");
  expect(summary).toHaveTextContent("50 flows/s");
  expect(summary).toHaveTextContent("1m");
  expect(within(summary).getByText("Passed")).toBeInTheDocument();
});

test("a breached threshold is named, not just coloured", () => {
  render(<LoadReport run={sampleLoadRun({ status: "FAILED" })} />);

  expect(screen.getByText("Threshold breached")).toBeInTheDocument();
});

test("warns when this machine could not generate the requested load", () => {
  render(<LoadReport run={sampleLoadRun({ generatorLimited: true })} />);

  expect(screen.getByRole("alert")).toHaveTextContent("The load generator could not keep up");
});

test("does not warn about the generator on a healthy run", () => {
  render(<LoadReport run={sampleLoadRun()} />);

  expect(screen.queryByRole("alert")).toBeNull();
});

test("lists endpoints slowest first and marks the slowest", () => {
  render(<LoadReport run={sampleLoadRun()} />);

  const rows = within(screen.getByRole("table", { name: "Endpoints" })).getAllByRole("row").slice(1);
  expect(rows.map((row) => within(row).getByTestId("path").textContent)).toEqual([
    "/documents",
    "/workspaces",
    "/workspaces/{workspaceId}",
    "/auth/token",
    "/workspaces/{workspaceId}",
  ]);
  expect(within(rows[0]).getByText("Slowest")).toBeInTheDocument();
  expect(within(rows[1]).queryByText("Slowest")).toBeNull();
});

test("each endpoint row carries its own figures", () => {
  render(<LoadReport run={sampleLoadRun()} />);

  const row = screen.getByRole("row", { name: /List documents/ });
  expect(row).toHaveTextContent("3,000");
  expect(row).toHaveTextContent("0.8%");
  expect(row).toHaveTextContent("203 ms");
  expect(row).toHaveTextContent("2.8 kB");
});
