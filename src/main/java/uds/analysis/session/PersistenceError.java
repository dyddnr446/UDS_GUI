package uds.analysis.session;

/** Raised when analysis session persistence fails; in-memory store must be kept. */
public final class PersistenceError extends RuntimeException {
  public PersistenceError(String message) {
    super(message);
  }

  public PersistenceError(String message, Throwable cause) {
    super(message, cause);
  }
}
