import { render } from "@testing-library/react";
import { expect, test } from "vitest";
import { ProjectAvatar } from "./project-avatar";

const src = (seed: string) =>
  render(<ProjectAvatar seed={seed} />).container.querySelector("img")!.getAttribute("src");

test("the same seed always gives the same avatar", () => {
  expect(src("project-1")).toBe(src("project-1"));
});

test("different seeds give different avatars", () => {
  expect(src("project-1")).not.toBe(src("project-2"));
});

test("the avatar is an inline image, so it works offline, and is hidden from screen readers", () => {
  const img = render(<ProjectAvatar seed="x" />).container.querySelector("img")!;

  expect(img.getAttribute("src")).toMatch(/^data:image\/svg\+xml/);
  expect(img).toHaveAttribute("alt", "");
});
