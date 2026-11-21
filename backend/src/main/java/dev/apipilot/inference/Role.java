package dev.apipilot.inference;

/** What an operation does to its resource. */
public enum Role {
  CREATE,
  LIST,
  READ,
  UPDATE,
  PATCH,
  DELETE
}
