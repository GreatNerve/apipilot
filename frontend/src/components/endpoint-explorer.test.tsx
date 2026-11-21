import { fireEvent, render, screen, within } from "@testing-library/react";
import { expect, test } from "vitest";
import type { ApiModel } from "@/lib/api";
import { EndpointExplorer } from "./endpoint-explorer";

const api: ApiModel = {
  title: "Sample",
  version: "1",
  servers: [],
  openApi31: false,
  securitySchemes: { bearerAuth: { type: "http", scheme: "bearer" } },
  schemas: {
    Workspace: { type: "object", properties: { name: { type: "string" } } },
  },
  operations: [
    {
      id: "token",
      method: "POST",
      path: "/auth/token",
      tags: ["auth"],
      parameters: [],
      responses: [{ status: "200", links: [] }],
      security: [],
    },
    {
      id: "createWorkspace",
      method: "POST",
      path: "/workspaces",
      summary: "Create a workspace",
      tags: ["workspaces"],
      parameters: [],
      requestBody: {
        contentType: "application/json",
        required: true,
        schema: { $ref: "#/components/schemas/Workspace" },
      },
      responses: [
        {
          status: "201",
          contentType: "application/json",
          schema: { $ref: "#/components/schemas/Workspace" },
          links: [],
        },
      ],
      security: ["bearerAuth"],
    },
    {
      id: "getWorkspace",
      method: "GET",
      path: "/workspaces/{workspaceId}",
      tags: ["workspaces"],
      parameters: [
        { name: "workspaceId", in: "path", required: true, schema: { type: "string" } },
      ],
      responses: [{ status: "200", links: [] }],
      security: ["bearerAuth"],
    },
  ],
};

test("groups operations by tag", () => {
  render(<EndpointExplorer api={api} />);

  const group = screen.getByRole("group", { name: "workspaces" });
  expect(within(group).getAllByRole("button")).toHaveLength(2);
  expect(within(screen.getByRole("group", { name: "auth" })).getAllByRole("button")).toHaveLength(1);
});

test("filters operations by path or method", () => {
  render(<EndpointExplorer api={api} />);

  fireEvent.change(screen.getByLabelText("Filter endpoints"), { target: { value: "auth" } });

  expect(screen.queryByRole("group", { name: "workspaces" })).toBeNull();
  expect(screen.getByRole("button", { name: /\/auth\/token/ })).toBeInTheDocument();
});

test("shows the first operation's details until another is selected", () => {
  render(<EndpointExplorer api={api} />);
  const detail = screen.getByRole("region", { name: "Endpoint details" });

  expect(within(detail).getByText("/auth/token")).toBeInTheDocument();
  expect(within(detail).getByText("No authentication")).toBeInTheDocument();

  fireEvent.click(screen.getByRole("button", { name: /GET \/workspaces\/\{workspaceId\}/ }));

  expect(within(detail).getByText("Requires bearerAuth")).toBeInTheDocument();
  expect(within(detail).getByText("workspaceId")).toBeInTheDocument();
  expect(within(detail).getByText("path")).toBeInTheDocument();
});

test("shows the request body schema and each response", () => {
  render(<EndpointExplorer api={api} />);

  fireEvent.click(screen.getByRole("button", { name: /POST \/workspaces$/ }));
  const detail = screen.getByRole("region", { name: "Endpoint details" });

  expect(within(detail).getByText("Create a workspace")).toBeInTheDocument();
  expect(within(detail).getByText("Request body")).toBeInTheDocument();
  expect(within(detail).getByText("201")).toBeInTheDocument();
  expect(within(detail).getAllByTestId("prop-name").length).toBeGreaterThan(0);
});

test("says so when the filter matches nothing", () => {
  render(<EndpointExplorer api={api} />);

  fireEvent.change(screen.getByLabelText("Filter endpoints"), { target: { value: "zzz" } });

  expect(screen.getByText("No endpoints match the filter")).toBeInTheDocument();
});
