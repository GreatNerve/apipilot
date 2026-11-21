import type { NextConfig } from "next";

const isDev = process.env.NODE_ENV === "development";

const nextConfig: NextConfig = {
  // The production build is a static export served by the Spring Boot backend.
  // Rewrites are not allowed with an export, so the API proxy exists only in `next dev`.
  ...(isDev
    ? {
        rewrites: async () => [
          { source: "/api/:path*", destination: "http://127.0.0.1:8080/api/:path*" },
        ],
      }
    : { output: "export" as const }),
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
