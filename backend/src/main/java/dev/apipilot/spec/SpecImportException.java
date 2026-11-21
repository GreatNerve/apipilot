package dev.apipilot.spec;

/** The supplied content could not be read as an API description. */
public class SpecImportException extends RuntimeException {

  public SpecImportException(String message) {
    super(message);
  }

  public SpecImportException(String message, Throwable cause) {
    super(message, cause);
  }
}
