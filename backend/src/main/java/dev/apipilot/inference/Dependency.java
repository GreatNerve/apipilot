package dev.apipilot.inference;

/**
 * Resource {@code from} needs a value that resource {@code to} produces.
 *
 * @param from key of the dependent resource
 * @param to key of the resource that must exist first
 * @param input where the value goes: {@code path:<name>} or {@code body:<JSON Pointer>}
 * @param required false when the request is valid without the value
 * @param confidence 0 to 1; 1 means it follows from the path structure
 * @param reason one sentence a person can check
 */
public record Dependency(
    String from, String to, String input, boolean required, double confidence, String reason) {}
