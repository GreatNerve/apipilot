package dev.apipilot.load;

/** Figures for one step of the flow, which is one API operation. */
public record StepStats(
    String stepId,
    String name,
    String method,
    String path,
    long requests,
    long errors,
    double avgBytes,
    Latency latency) {}
