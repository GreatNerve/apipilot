import createClient from "openapi-fetch";
import type { components, paths } from "./api-types";

type Schemas = components["schemas"];

export type ProjectSummary = Schemas["ProjectSummary"];
export type ProjectDetail = Schemas["ProjectDetail"];
export type ApiModel = Schemas["ApiModel"];
export type Operation = Schemas["Operation"];
export type JsonSchema = { [key: string]: unknown };
export type DependencyGraph = Schemas["DependencyGraph"];
export type Resource = Schemas["Resource"];
export type Dependency = Schemas["Dependency"];
export type GraphOverrides = Schemas["GraphOverrides"];
export type EdgeRef = Schemas["EdgeRef"];
export type LoadRun = Schemas["LoadRun"];

export const api = createClient<paths>({
  // Absolute so requests can be built outside a browser too; the backend is always same-origin.
  baseUrl: typeof window === "undefined" ? "" : window.location.origin,
  // Looked up per call so tests can replace the global.
  fetch: (request) => fetch(request),
});

/** Returns the response data, or throws an Error carrying the backend's explanation. */
export function unwrap<T>(result: { data?: T; error?: unknown; response: Response }): T {
  if (!result.response.ok) {
    const detail = (result.error as { detail?: string } | undefined)?.detail;
    throw new Error(detail ?? `Request failed with HTTP ${result.response.status}`);
  }
  return result.data as T;
}
