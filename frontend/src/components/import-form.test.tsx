import { fireEvent, screen, waitFor } from "@testing-library/react";
import { expect, test, vi } from "vitest";
import { mockApi } from "@/test/mock-api";
import { renderWithQuery } from "@/test/render";
import { ImportForm } from "./import-form";

const created = {
  id: "p1",
  name: "Pets",
  operationCount: 3,
  createdAt: "2026-11-16T10:00:00Z",
};

test("imports a spec from a URL and reports the new project", async () => {
  const calls = mockApi({ "POST /api/projects": () => Response.json(created, { status: 201 }) });
  const onImported = vi.fn();
  renderWithQuery(<ImportForm onImported={onImported} />);

  fireEvent.change(screen.getByLabelText("Spec URL"), {
    target: { value: "http://localhost:8090/v3/api-docs" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Import" }));

  await waitFor(() => expect(onImported).toHaveBeenCalledWith(created));
  expect(calls[0].body).toEqual({ specUrl: "http://localhost:8090/v3/api-docs" });
});

test("sends a user name and password for password-protected docs", async () => {
  const calls = mockApi({ "POST /api/projects": () => Response.json(created, { status: 201 }) });
  renderWithQuery(<ImportForm onImported={() => {}} />);

  fireEvent.change(screen.getByLabelText("Spec URL"), { target: { value: "http://x/spec" } });
  fireEvent.click(screen.getByRole("checkbox", { name: "This URL is password-protected" }));
  fireEvent.change(screen.getByLabelText("User name"), { target: { value: "docs" } });
  fireEvent.change(screen.getByLabelText("Password"), { target: { value: "s3cret" } });
  fireEvent.click(screen.getByRole("button", { name: "Import" }));

  await waitFor(() => expect(calls).toHaveLength(1));
  expect(calls[0].body).toEqual({
    specUrl: "http://x/spec",
    specUsername: "docs",
    specPassword: "s3cret",
  });
});

test("credential fields stay hidden, and are not sent, unless asked for", async () => {
  mockApi({});
  renderWithQuery(<ImportForm onImported={() => {}} />);

  expect(screen.queryByLabelText("User name")).toBeNull();
  expect(screen.queryByLabelText("Password")).toBeNull();
});

test("the password is masked", () => {
  mockApi({});
  renderWithQuery(<ImportForm onImported={() => {}} />);

  fireEvent.click(screen.getByRole("checkbox", { name: "This URL is password-protected" }));

  expect(screen.getByLabelText("Password")).toHaveAttribute("type", "password");
});

test("imports pasted spec content with an optional name", async () => {
  const calls = mockApi({ "POST /api/projects": () => Response.json(created, { status: 201 }) });
  renderWithQuery(<ImportForm onImported={() => {}} />);

  fireEvent.click(screen.getByRole("tab", { name: "Paste spec" }));
  fireEvent.change(await screen.findByLabelText("Spec content"), {
    target: { value: "openapi: 3.0.3" },
  });
  fireEvent.change(screen.getByLabelText("Project name (optional)"), {
    target: { value: "Pets" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Import" }));

  await waitFor(() => expect(calls).toHaveLength(1));
  expect(calls[0].body).toEqual({ name: "Pets", spec: "openapi: 3.0.3" });
});

test("loads a chosen file into the spec content", async () => {
  mockApi({});
  renderWithQuery(<ImportForm onImported={() => {}} />);

  fireEvent.click(screen.getByRole("tab", { name: "Paste spec" }));
  const file = new File(["swagger: '2.0'"], "api.yaml", { type: "application/yaml" });
  fireEvent.change(await screen.findByLabelText("Or load it from a file"), {
    target: { files: [file] },
  });

  await waitFor(() => expect(screen.getByLabelText("Spec content")).toHaveValue("swagger: '2.0'"));
});

test("shows the reason when the backend rejects the spec", async () => {
  mockApi({
    "POST /api/projects": () =>
      Response.json({ detail: "The spec contains no operations" }, { status: 400 }),
  });
  renderWithQuery(<ImportForm onImported={() => {}} />);

  fireEvent.change(screen.getByLabelText("Spec URL"), { target: { value: "http://x/spec" } });
  fireEvent.click(screen.getByRole("button", { name: "Import" }));

  expect(await screen.findByRole("alert")).toHaveTextContent("The spec contains no operations");
});

test("cannot import until a URL or content is entered", () => {
  mockApi({});
  renderWithQuery(<ImportForm onImported={() => {}} />);

  expect(screen.getByRole("button", { name: "Import" })).toBeDisabled();
});
