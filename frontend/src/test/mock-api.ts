import { vi } from "vitest";

type Handler = (body: unknown) => Response | Promise<Response>;

/**
 * Replaces `fetch` with handlers keyed by "METHOD /path". Returns the recorded calls so a test can
 * assert on what was sent.
 */
export function mockApi(routes: Record<string, Handler>) {
  const calls: { route: string; body: unknown }[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const request = new Request(input, init);
      const url = new URL(request.url);
      const route = `${request.method} ${url.pathname}`;
      const text = await request.text();
      const body = text ? JSON.parse(text) : undefined;
      calls.push({ route, body });
      const handler = routes[route];
      if (!handler) {
        return Response.json({ detail: `No mock for ${route}` }, { status: 500 });
      }
      return handler(body);
    }),
  );
  return calls;
}
