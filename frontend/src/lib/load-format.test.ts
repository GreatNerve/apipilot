import { expect, test } from "vitest";
import { formatBytes, formatClock, formatCount, formatDuration, formatMs, formatPercent } from "./load-format";

test("response times read in ms below a second and in seconds above", () => {
  expect(formatMs(0)).toBe("0 ms");
  expect(formatMs(0.42)).toBe("0.4 ms");
  expect(formatMs(38.4)).toBe("38 ms");
  expect(formatMs(999.6)).toBe("1.00 s");
  expect(formatMs(1250)).toBe("1.25 s");
});

test("sizes use the largest unit that keeps the number short", () => {
  expect(formatBytes(512)).toBe("512 B");
  expect(formatBytes(2048)).toBe("2.0 kB");
  expect(formatBytes(5 * 1024 * 1024)).toBe("5.0 MB");
});

test("large counts are grouped", () => {
  expect(formatCount(1234567)).toBe("1,234,567");
});

test("small error rates keep enough digits to not round to zero", () => {
  expect(formatPercent(0, 100)).toBe("0%");
  expect(formatPercent(1, 10000)).toBe("0.01%");
  expect(formatPercent(25, 1000)).toBe("2.5%");
  expect(formatPercent(0, 0)).toBe("0%");
});

test("durations and clock positions", () => {
  expect(formatDuration(45)).toBe("45s");
  expect(formatDuration(120)).toBe("2m");
  expect(formatDuration(3725)).toBe("1h 2m 5s");
  expect(formatClock(75)).toBe("1:15");
});
