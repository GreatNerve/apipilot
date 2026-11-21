package dev.apipilot.inference;

/** Identifies one dependency: {@code from} needs a value from {@code to} for {@code input}. */
public record EdgeRef(String from, String to, String input) {}
