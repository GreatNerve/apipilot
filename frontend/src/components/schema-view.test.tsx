import { fireEvent, render, screen, within } from "@testing-library/react";
import { expect, test } from "vitest";
import { SchemaView } from "./schema-view";

const schemas = {
  Pet: {
    type: "object",
    required: ["id"],
    properties: {
      id: { type: "integer", format: "int64" },
      name: { type: "string", maxLength: 50 },
      owner: { $ref: "#/components/schemas/Owner" },
    },
  },
  Owner: { type: "object", properties: { email: { type: "string", format: "email" } } },
  Node: {
    type: "object",
    properties: { children: { type: "array", items: { $ref: "#/components/schemas/Node" } } },
  },
};

test("lists properties with type, format and constraints", () => {
  render(<SchemaView schema={schemas.Pet} schemas={schemas} />);

  expect(within(screen.getByTestId("prop-id")).getByText("integer (int64)")).toBeInTheDocument();
  expect(within(screen.getByTestId("prop-name")).getByText("maxLength 50")).toBeInTheDocument();
});

test("marks required properties", () => {
  render(<SchemaView schema={schemas.Pet} schemas={schemas} />);

  expect(within(screen.getByTestId("prop-id")).getByText("required")).toBeInTheDocument();
  expect(within(screen.getByTestId("prop-name")).queryByText("required")).toBeNull();
});

test("shows a referenced schema by name and expands it on demand", () => {
  render(<SchemaView schema={schemas.Pet} schemas={schemas} />);

  expect(screen.queryByTestId("prop-email")).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Owner" }));

  expect(screen.getByTestId("prop-email")).toBeInTheDocument();
});

test("resolves a top-level reference", () => {
  render(<SchemaView schema={{ $ref: "#/components/schemas/Owner" }} schemas={schemas} />);

  expect(screen.getByTestId("prop-email")).toBeInTheDocument();
});

test("renders a self-referencing schema without expanding it forever", () => {
  render(<SchemaView schema={schemas.Node} schemas={schemas} />);

  const children = screen.getByTestId("prop-children");
  expect(within(children).getByText("array of")).toBeInTheDocument();
  expect(within(children).getByRole("button", { name: "Node" })).toBeInTheDocument();
  expect(screen.getAllByTestId("prop-children")).toHaveLength(1);
});
