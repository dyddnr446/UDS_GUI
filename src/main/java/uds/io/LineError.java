package uds.io;

/** Immutable per-line log parse error (NFR-02). */
public record LineError(int lineNo, String raw, String reason) {}
