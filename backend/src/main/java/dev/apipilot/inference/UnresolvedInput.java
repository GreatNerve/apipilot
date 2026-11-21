package dev.apipilot.inference;

/** A required value that no operation in the spec produces, so a person has to supply it. */
public record UnresolvedInput(String operationId, String input, String reason) {}
