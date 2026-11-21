package dev.apipilot.load;

/**
 * Whole-run figures.
 *
 * @param avgBytes average response size in bytes
 */
public record Totals(long requests, long errors, double avgBytes, Latency latency) {}
