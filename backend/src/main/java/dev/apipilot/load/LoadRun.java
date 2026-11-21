package dev.apipilot.load;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One load, spike, soak or capacity run and everything measured during it.
 *
 * @param targetRate flows started per second
 * @param generatorLimited true when this machine could not keep up, which makes the figures
 *     unreliable
 */
public record LoadRun(
    UUID id,
    UUID projectId,
    String name,
    Profile profile,
    Status status,
    double targetRate,
    int durationSeconds,
    Instant startedAt,
    boolean generatorLimited,
    Totals totals,
    List<Window> windows,
    List<StepStats> steps) {

  public enum Profile {
    SMOKE,
    LOAD,
    SPIKE,
    SOAK,
    CAPACITY
  }

  public enum Status {
    RUNNING,
    PASSED,
    /** A threshold on response time or error rate was crossed. */
    FAILED,
    ABORTED
  }
}
