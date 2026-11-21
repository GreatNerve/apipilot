package dev.apipilot.inference;

/**
 * Where the value for a resource's ID parameter comes from.
 *
 * @param operationId the operation to call to obtain the value
 * @param pointer JSON Pointer into that operation's response body, or its request body for {@link
 *     Kind#CREATE_REQUEST}
 * @param confidence 0 to 1; 1 means the spec states it outright
 */
public record IdSource(Kind kind, String operationId, String pointer, double confidence) {

  public enum Kind {
    /** Returned by the resource's own create operation. */
    CREATE_RESPONSE,
    /** Chosen by the caller and sent in the create request, such as a username. */
    CREATE_REQUEST,
    /** Picked from the first item the list operation returns. */
    LIST_RESPONSE,
    /** Returned by an operation outside this resource. */
    OTHER_RESPONSE
  }
}
