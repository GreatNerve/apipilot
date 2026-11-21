package dev.apipilot.load;

/** Response time in milliseconds. Percentiles come from merged histograms, never from averages. */
public record Latency(double p50, double avg, double p95, double p99, double max) {}
