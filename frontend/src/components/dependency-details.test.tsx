import { fireEvent, render, screen, within } from "@testing-library/react";
import { expect, test, vi } from "vitest";
import type { GraphOverrides } from "@/lib/api";
import { sampleGraph } from "@/test/sample-graph";
import { DependencyDetails } from "./dependency-details";

const none: GraphOverrides = { removed: [], added: [] };

function renderDetails(overrides = none, onChange = vi.fn()) {
  render(<DependencyDetails graph={sampleGraph} overrides={overrides} onChange={onChange} />);
  return onChange;
}

test("lists the create order and the reverse delete order", () => {
  renderDetails();

  const create = within(screen.getByRole("list", { name: "Create order" })).getAllByRole("listitem");
  expect(create.map((item) => item.textContent)).toEqual([
    "categories",
    "workspaces",
    "members",
    "documents",
  ]);
  const remove = within(screen.getByRole("list", { name: "Delete order" })).getAllByRole("listitem");
  expect(remove.map((item) => item.textContent)).toEqual([
    "documents",
    "members",
    "workspaces",
    "categories",
  ]);
});

test("explains each dependency and flags the uncertain ones", () => {
  renderDetails();

  const row = screen.getByRole("row", { name: /documents needs categories/ });
  expect(within(row).getByText("body:/categoryId")).toBeInTheDocument();
  expect(within(row).getByText("Low confidence")).toBeInTheDocument();
  expect(within(row).getByText(/ends with the resource name/)).toBeInTheDocument();
});

test("describes how authentication will be obtained", () => {
  renderDetails();

  const auth = screen.getByRole("region", { name: "Authentication" });
  expect(auth).toHaveTextContent("bearerAuth");
  expect(auth).toHaveTextContent("register, then token");
  expect(auth).toHaveTextContent("Bearer {{token}}");
});

test("says a credential is needed when no token operation was found", () => {
  render(
    <DependencyDetails
      graph={{ ...sampleGraph, auth: [{ scheme: "api_key", valueTemplate: "{{token}}" }] }}
      overrides={none}
      onChange={() => {}}
    />,
  );

  expect(screen.getByRole("region", { name: "Authentication" })).toHaveTextContent(
    "No operation returns a token. You will need to supply the value.",
  );
});

test("lists values that a person has to supply", () => {
  render(
    <DependencyDetails
      graph={{
        ...sampleGraph,
        unresolved: [{ operationId: "createUser", input: "body:/ownerId", reason: "No match" }],
      }}
      overrides={none}
      onChange={() => {}}
    />,
  );

  const needs = screen.getByRole("region", { name: "Needs a value from you" });
  expect(needs).toHaveTextContent("createUser");
  expect(needs).toHaveTextContent("body:/ownerId");
});

test("removing a dependency reports it as an override", () => {
  const onChange = renderDetails();

  const row = screen.getByRole("row", { name: /documents needs categories/ });
  fireEvent.click(within(row).getByRole("button", { name: "Remove" }));

  expect(onChange).toHaveBeenCalledWith({
    removed: [{ from: "/documents", to: "/categories", input: "body:/categoryId" }],
    added: [],
  });
});

test("a removed dependency is listed and can be restored", () => {
  const removed = [{ from: "/documents", to: "/categories", input: "body:/categoryId" }];
  const onChange = renderDetails({ removed, added: [] });

  const item = screen.getByText(/documents no longer needs categories/).closest("li")!;
  fireEvent.click(within(item).getByRole("button", { name: "Restore" }));

  expect(onChange).toHaveBeenCalledWith({ removed: [], added: [] });
});

test("adding a dependency reports it as an override", () => {
  const onChange = renderDetails();

  fireEvent.change(screen.getByLabelText("Resource that needs a value"), {
    target: { value: "/workspaces" },
  });
  fireEvent.change(screen.getByLabelText("Resource that provides it"), {
    target: { value: "/categories" },
  });
  fireEvent.change(screen.getByLabelText("Where the value goes"), {
    target: { value: "body:/categoryId" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Add dependency" }));

  expect(onChange).toHaveBeenCalledWith({
    removed: [],
    added: [{ from: "/workspaces", to: "/categories", input: "body:/categoryId" }],
  });
});

test("cannot add a dependency of a resource on itself", () => {
  renderDetails();

  fireEvent.change(screen.getByLabelText("Resource that needs a value"), {
    target: { value: "/workspaces" },
  });
  fireEvent.change(screen.getByLabelText("Resource that provides it"), {
    target: { value: "/workspaces" },
  });
  fireEvent.change(screen.getByLabelText("Where the value goes"), {
    target: { value: "body:/x" },
  });

  expect(screen.getByRole("button", { name: "Add dependency" })).toBeDisabled();
});
