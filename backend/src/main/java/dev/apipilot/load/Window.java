package dev.apipilot.load;

/**
 * What happened during one second of a run.
 *
 * @param second seconds since the run started
 * @param p50 milliseconds, as are {@code p95} and {@code p99}
 */
public record Window(int second, long requests, long errors, double p50, double p95, double p99) {}
